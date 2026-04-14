package com.socialflow.service.media;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.socialflow.common.result.PageResult;
import com.socialflow.dao.mapper.MediaAssetMapper;
import com.socialflow.model.entity.MediaAsset;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * 媒体素材服务实现类 —— 基于 MinIO 对象存储的素材管理。
 *
 * 负责文件上传到 MinIO、数据库元信息管理、文件删除等操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaAssetMapper mediaAssetMapper;

    /** MinIO 服务地址，如 http://localhost:9000 */
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
     * 初始化 MinIO 客户端，确保存储桶存在并设置公开读取策略。
     */
    @PostConstruct
    public void init() {
        try {
            // 构建 MinIO 客户端
            this.minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            // 如果存储桶不存在则创建
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建 MinIO 存储桶: {}", bucket);
            }

            // 设置存储桶策略，允许匿名读取 media/ 路径下的文件（浏览器直接加载图片）
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [{
                        "Effect": "Allow",
                        "Principal": {"AWS": ["*"]},
                        "Action": ["s3:GetObject"],
                        "Resource": ["arn:aws:s3:::%s/media/*"]
                      }]
                    }
                    """.formatted(bucket);
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
            log.info("MinIO 媒体素材服务初始化完成，endpoint={}, bucket={}", endpoint, bucket);
        } catch (Exception e) {
            log.error("MinIO 初始化失败", e);
            throw new RuntimeException("MinIO 初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传媒体文件到 MinIO 并保存元信息到数据库。
     *
     * @param userId 当前用户 ID
     * @param file   上传的文件
     * @param tags   用户标签（逗号分隔）
     * @return 新创建的媒体资产实体
     */
    @Override
    public MediaAsset upload(Long userId, MultipartFile file, String tags) {
        try {
            String originalFilename = file.getOriginalFilename();
            String mimeType = file.getContentType();

            // 生成 MinIO 对象键：media/{userId}/{UUID}_{原始文件名}
            String objectKey = "media/" + userId + "/" + UUID.randomUUID() + "_" + originalFilename;

            // 上传文件到 MinIO
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(mimeType)
                        .build());
            }

            // 生成文件访问 URL
            String fileUrl = endpoint + "/" + bucket + "/" + objectKey;

            // 根据 MIME 类型判断文件类型（IMAGE / VIDEO）
            String fileType = "IMAGE";
            if (mimeType != null && mimeType.startsWith("video/")) {
                fileType = "VIDEO";
            }

            // 构建媒体资产实体并写入数据库
            MediaAsset asset = new MediaAsset();
            asset.setUserId(userId);
            asset.setFileName(originalFilename);
            asset.setFileType(fileType);
            asset.setMimeType(mimeType);
            asset.setFileUrl(fileUrl);
            asset.setThumbnailUrl(fileUrl);  // 暂时使用原始 URL 作为缩略图
            asset.setFileSize(file.getSize());
            asset.setTags(tags);

            mediaAssetMapper.insert(asset);
            log.info("媒体素材上传成功: userId={}, fileName={}, objectKey={}", userId, originalFilename, objectKey);
            return asset;
        } catch (Exception e) {
            log.error("媒体素材上传失败: userId={}", userId, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分页查询媒体素材列表，支持按文件类型筛选和关键词搜索。
     */
    @Override
    public PageResult<MediaAsset> list(Long userId, Integer pageNum, Integer pageSize,
                                       String fileType, String keyword) {
        LambdaQueryWrapper<MediaAsset> wrapper = new LambdaQueryWrapper<>();
        // 只查询当前用户的素材
        wrapper.eq(MediaAsset::getUserId, userId);
        // 按文件类型筛选（可选）
        if (StringUtils.hasText(fileType)) {
            wrapper.eq(MediaAsset::getFileType, fileType);
        }
        // 关键词搜索：匹配文件名或标签（可选）
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(MediaAsset::getFileName, keyword)
                    .or()
                    .like(MediaAsset::getTags, keyword));
        }
        // 按创建时间倒序排列（最新上传的排前面）
        wrapper.orderByDesc(MediaAsset::getCreateTime);

        // 执行 MyBatis-Plus 分页查询
        Page<MediaAsset> page = new Page<>(pageNum, pageSize);
        Page<MediaAsset> result = mediaAssetMapper.selectPage(page, wrapper);

        return PageResult.of(result.getRecords(), result.getTotal(), pageNum, pageSize);
    }

    /**
     * 删除媒体素材：先校验权限，再删除 MinIO 文件，最后删除数据库记录。
     */
    @Override
    public void delete(Long userId, Long id) {
        // 查询素材记录
        MediaAsset asset = mediaAssetMapper.selectById(id);
        if (asset == null) {
            throw new RuntimeException("素材不存在");
        }
        // 校验所属权限
        if (!asset.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除他人素材");
        }

        try {
            // 从文件 URL 中提取 MinIO 对象键
            // URL 格式: {endpoint}/{bucket}/{objectKey}
            String prefix = endpoint + "/" + bucket + "/";
            String objectKey = asset.getFileUrl().replace(prefix, "");

            // 从 MinIO 删除文件
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            log.info("已从 MinIO 删除文件: {}", objectKey);
        } catch (Exception e) {
            log.warn("MinIO 文件删除失败（继续删除数据库记录）: {}", e.getMessage());
        }

        // 删除数据库记录（逻辑删除）
        mediaAssetMapper.deleteById(id);
        log.info("媒体素材删除成功: userId={}, id={}", userId, id);
    }

    /**
     * 语义搜索（暂未实现，返回空结果）。
     */
    @Override
    public PageResult<MediaAsset> semanticSearch(Long userId, String query, int topK) {
        // 向量搜索尚未接入媒体素材，返回空结果
        return PageResult.empty(1, topK);
    }
}
