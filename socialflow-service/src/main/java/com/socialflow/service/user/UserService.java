package com.socialflow.service.user;

import com.socialflow.model.dto.LoginDTO;
import com.socialflow.model.dto.RegisterDTO;
import com.socialflow.model.vo.LoginVO;
import com.socialflow.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务接口 —— 处理用户认证与身份管理。
 *
 * 负责的业务领域：用户注册、登录、登出以及获取当前用户信息。
 * 这些是系统最基础的功能，几乎所有其他业务操作都依赖于用户身份认证。
 *
 * 认证机制：通常使用 JWT（JSON Web Token）方式。
 * 用户登录成功后获取 token，后续请求在 Header 中携带 token 来证明身份。
 *
 * 对应的 Controller：{@code UserController}，路由前缀为 {@code /api/v1/user/*}。
 */
public interface UserService {

    /**
     * 用户注册。
     *
     * 创建一个新的用户账号。通常包含以下处理：
     * 用户名/邮箱去重检查 -> 密码加密（如 BCrypt）-> 保存到数据库。
     *
     * @param dto 注册参数，包含用户名、密码、邮箱等信息
     * @return 注册成功后的用户视图对象（不包含密码等敏感信息）
     */
    UserVO register(RegisterDTO dto);

    /**
     * 用户登录。
     *
     * 验证用户身份并签发访问令牌。通常包含以下处理：
     * 查找用户 -> 验证密码 -> 生成 JWT token -> 返回 token 和用户信息。
     *
     * @param dto 登录参数，包含用户名/邮箱和密码
     * @return 登录结果，包含 JWT token 和用户基本信息
     */
    LoginVO login(LoginDTO dto);

    /**
     * 获取当前登录用户的信息。
     *
     * 根据 token 解析出的用户 ID，从数据库查询并返回用户的最新信息。
     * 通常在前端初始化或刷新页面时调用。
     *
     * @param userId 当前登录用户的 ID（从 JWT token 中解析得到）
     * @return 用户视图对象，包含昵称、头像、邮箱等信息
     */
    UserVO currentUser(Long userId);

    /**
     * 用户登出。
     *
     * 使当前用户的会话/令牌失效。具体实现可能包括：
     * 将 token 加入黑名单、清除 Redis 中的会话信息等。
     *
     * @param userId 要登出的用户 ID
     */
    void logout(Long userId);

    /**
     * 更新用户个人资料。
     *
     * 可更新昵称和头像 URL，传入 null 的字段不做修改。
     *
     * @param userId    用户 ID
     * @param nickname  新昵称（可为 null，表示不修改）
     * @param avatarUrl 新头像 URL（可为 null，表示不修改）
     * @return 更新后的用户视图对象
     */
    UserVO updateProfile(Long userId, String nickname, String avatarUrl);

    /**
     * 上传用户头像到 MinIO 对象存储。
     *
     * 将头像文件上传到 MinIO 的 avatar/{userId}/ 路径下，
     * 并更新用户表中的 avatarUrl 字段。
     *
     * @param userId 用户 ID
     * @param file   头像文件
     * @return 头像的访问 URL
     */
    String uploadAvatar(Long userId, MultipartFile file);
}
