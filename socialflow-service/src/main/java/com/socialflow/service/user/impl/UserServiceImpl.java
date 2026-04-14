package com.socialflow.service.user.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.exception.NotFoundException;
import com.socialflow.dao.mapper.SysUserMapper;
import com.socialflow.model.dto.LoginDTO;
import com.socialflow.model.dto.RegisterDTO;
import com.socialflow.model.entity.SysUser;
import com.socialflow.model.vo.LoginVO;
import com.socialflow.model.vo.UserVO;
import com.socialflow.service.user.UserService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 用户服务实现类 —— 处理用户注册、登录、登出及获取当前用户信息。
 *
 * 认证机制采用 Sa-Token 框架，登录成功后签发 Token，
 * 客户端在后续请求的 Header 中携带 Token 来证明身份。
 *
 * 密码使用 BCrypt 算法进行单向哈希存储，即使数据库泄露也无法还原明文密码。
 *
 * @see UserService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 系统用户数据库映射器 */
    private final SysUserMapper sysUserMapper;

    /** MinIO 服务地址 */
    @Value("${socialflow.storage.endpoint}")
    private String endpoint;

    /** MinIO 访问密钥 */
    @Value("${socialflow.storage.access-key}")
    private String accessKey;

    /** MinIO 密钥 */
    @Value("${socialflow.storage.secret-key}")
    private String secretKey;

    /** MinIO 存储桶名称 */
    @Value("${socialflow.storage.bucket}")
    private String bucket;

    /** MinIO 客户端实例 */
    private MinioClient minioClient;

    /**
     * 初始化 MinIO 客户端，确保存储桶存在。
     */
    @PostConstruct
    public void init() {
        try {
            this.minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建 MinIO 存储桶: {}", bucket);
            }
            log.info("UserServiceImpl MinIO 客户端初始化完成, endpoint={}, bucket={}", endpoint, bucket);
        } catch (Exception e) {
            log.error("UserServiceImpl MinIO 初始化失败", e);
            throw new RuntimeException("MinIO 初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 用户注册 —— 创建新用户账号。
     *
     * 处理流程：
     *   1. 检查邮箱是否已被注册（全局唯一约束）
     *   2. 对明文密码进行 BCrypt 哈希加密
     *   3. 构建用户实体并写入数据库
     *   4. 将数据库实体转换为视图对象返回（不含密码等敏感信息）
     *
     * @param dto 注册请求参数，包含邮箱、密码、昵称
     * @return 注册成功后的用户视图对象
     * @throws BusinessException 邮箱已被注册时抛出
     */
    @Override
    public UserVO register(RegisterDTO dto) {
        // 第一步：检查邮箱是否已存在 —— 保证邮箱全局唯一
        Long existCount = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEmail, dto.getEmail())
        );
        if (existCount > 0) {
            throw new BusinessException("该邮箱已被注册");
        }

        // 第二步：构建用户实体，对密码进行 BCrypt 哈希加密
        SysUser user = new SysUser();
        user.setEmail(dto.getEmail());
        user.setNickname(dto.getNickname());
        user.setPasswordHash(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
        user.setStatus(1);  // 1 = 正常状态

        // 第三步：插入数据库（主键由 MyBatis-Plus 雪花算法自动生成）
        sysUserMapper.insert(user);
        log.info("用户注册成功, email={}, userId={}", user.getEmail(), user.getId());

        // 第四步：转换为视图对象返回（不返回密码哈希等敏感信息）
        return toUserVO(user);
    }

    /**
     * 用户登录 —— 验证身份并签发 Sa-Token 令牌。
     *
     * 处理流程：
     *   1. 根据邮箱查询用户记录
     *   2. 校验密码是否匹配（BCrypt 比对）
     *   3. 通过 Sa-Token 执行登录，获取 Token
     *   4. 组装登录结果（Token + 过期时间 + 用户信息）返回
     *
     * @param dto 登录请求参数，包含邮箱和密码
     * @return 登录结果，包含 Token 和用户基本信息
     * @throws BusinessException 邮箱不存在或密码错误时抛出
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        // 第一步：根据邮箱查询用户 —— 邮箱不存在则提示"账号或密码错误"
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEmail, dto.getEmail())
        );
        if (user == null) {
            throw new BusinessException("账号或密码错误");
        }

        // 第二步：校验密码 —— 将用户输入的明文密码与数据库中的哈希值进行比对
        // 注意：错误提示不区分"邮箱不存在"和"密码错误"，防止攻击者枚举有效邮箱
        if (!BCrypt.checkpw(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }

        // 第三步：通过 Sa-Token 执行登录（框架自动管理 Session 和 Token 生命周期）
        StpUtil.login(user.getId());
        log.info("用户登录成功, userId={}, email={}", user.getId(), user.getEmail());

        // 第四步：组装登录结果返回
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(StpUtil.getTokenValue());
        loginVO.setExpiresIn(604800L);  // 7 天有效期（单位：秒）
        loginVO.setUser(toUserVO(user));
        return loginVO;
    }

    /**
     * 获取当前登录用户的信息。
     *
     * 根据用户 ID 从数据库查询最新的用户资料，确保返回的信息是实时的。
     *
     * @param userId 当前登录用户的 ID（由 Token 解析得到）
     * @return 用户视图对象
     * @throws NotFoundException 用户不存在时抛出
     */
    @Override
    public UserVO currentUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return toUserVO(user);
    }

    /**
     * 用户登出 —— 使指定用户的 Sa-Token 令牌失效。
     *
     * 调用 Sa-Token 的 logout 方法后，该用户的所有活跃 Token 将被注销，
     * 后续携带旧 Token 的请求将被拦截。
     *
     * @param userId 要登出的用户 ID
     */
    @Override
    public void logout(Long userId) {
        StpUtil.logout(userId);
        log.info("用户登出成功, userId={}", userId);
    }

    /**
     * 更新用户个人资料 —— 修改昵称和/或头像 URL。
     *
     * 只更新传入的非空字段，未传入的字段保持不变。
     *
     * @param userId    用户 ID
     * @param nickname  新昵称（可为 null）
     * @param avatarUrl 新头像 URL（可为 null）
     * @return 更新后的用户视图对象
     * @throws NotFoundException 用户不存在时抛出
     */
    @Override
    public UserVO updateProfile(Long userId, String nickname, String avatarUrl) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        if (StringUtils.hasText(nickname)) {
            user.setNickname(nickname);
        }
        if (StringUtils.hasText(avatarUrl)) {
            user.setAvatarUrl(avatarUrl);
        }
        sysUserMapper.updateById(user);
        log.info("用户资料更新成功, userId={}", userId);
        return toUserVO(user);
    }

    /**
     * 上传用户头像到 MinIO 对象存储。
     *
     * 文件存储路径：avatar/{userId}/avatar.jpg
     * 上传成功后更新 sys_user 表的 avatar_url 字段。
     *
     * @param userId 用户 ID
     * @param file   头像文件
     * @return 头像的访问 URL
     * @throws NotFoundException 用户不存在时抛出
     */
    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        try {
            String objectKey = "avatar/" + userId + "/avatar.jpg";
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
            String avatarUrl = endpoint + "/" + bucket + "/" + objectKey;
            user.setAvatarUrl(avatarUrl);
            sysUserMapper.updateById(user);
            log.info("用户头像上传成功, userId={}, url={}", userId, avatarUrl);
            return avatarUrl;
        } catch (Exception e) {
            log.error("用户头像上传失败, userId={}", userId, e);
            throw new RuntimeException("头像上传失败: " + e.getMessage(), e);
        }
    }

    // ==================== 内部工具方法 ====================

    /**
     * 将 SysUser 实体转换为 UserVO 视图对象。
     * 只暴露前端需要的安全字段，隐藏密码哈希等敏感信息。
     *
     * @param user 数据库用户实体
     * @return 用户视图对象
     */
    private UserVO toUserVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setStatus(user.getStatus());
        return vo;
    }
}
