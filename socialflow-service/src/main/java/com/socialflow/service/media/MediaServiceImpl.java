package com.socialflow.service.media;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.socialflow.common.result.PageResult;
import com.socialflow.dao.mapper.MediaAssetMapper;
import com.socialflow.model.entity.MediaAsset;
import com.socialflow.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * 媒体素材服务实现类 —— 基于统一存储服务（COS/MinIO）的素材管理。
 *
 * 负责文件上传、数据库元信息管理、文件删除等操作。
 * 底层存储通过 StorageService 接口抽象，支持腾讯云 COS 和 MinIO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaAssetMapper mediaAssetMapper;
    private final StorageService storageService;

    /**
     * 上传媒体文件并保存元信息到数据库。
     */
    @Override
    public MediaAsset upload(Long userId, MultipartFile file, String tags) {
        try {
            String originalFilename = file.getOriginalFilename();
            String mimeType = file.getContentType();

            // 生成对象键：media/{userId}/{UUID}_{原始文件名}
            String objectKey = "media/" + userId + "/" + UUID.randomUUID() + "_" + originalFilename;

            // 上传文件到对象存储（COS 或 MinIO）
            try (InputStream inputStream = file.getInputStream()) {
                storageService.upload(objectKey, inputStream, file.getSize(), mimeType);
            }

            // 获取公开访问 URL
            String fileUrl = storageService.getPublicUrl(objectKey);

            // 根据 MIME 类型判断文件类型
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
            asset.setThumbnailUrl(fileUrl);
            asset.setFileSize(file.getSize());
            asset.setTags(tags);

            mediaAssetMapper.insert(asset);
            log.info("素材上传成功: userId={}, fileName={}, url={}", userId, originalFilename, fileUrl);
            return asset;
        } catch (Exception e) {
            log.error("素材上传失败: userId={}", userId, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public PageResult<MediaAsset> list(Long userId, Integer pageNum, Integer pageSize,
                                       String fileType, String keyword) {
        LambdaQueryWrapper<MediaAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaAsset::getUserId, userId);
        if (StringUtils.hasText(fileType)) {
            wrapper.eq(MediaAsset::getFileType, fileType);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(MediaAsset::getFileName, keyword)
                    .or()
                    .like(MediaAsset::getTags, keyword));
        }
        wrapper.orderByDesc(MediaAsset::getCreateTime);

        Page<MediaAsset> page = new Page<>(pageNum, pageSize);
        Page<MediaAsset> result = mediaAssetMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), pageNum, pageSize);
    }

    /**
     * 删除媒体素材：校验权限 → 删除存储文件 → 删除数据库记录。
     */
    @Override
    public void delete(Long userId, Long id) {
        MediaAsset asset = mediaAssetMapper.selectById(id);
        if (asset == null) {
            throw new RuntimeException("素材不存在");
        }
        if (!asset.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除他人素材");
        }

        try {
            // 从 URL 中提取对象键
            String publicUrlBase = storageService.getPublicUrl("");
            String objectKey = asset.getFileUrl().replace(publicUrlBase, "");
            storageService.delete(objectKey);
            log.info("已从存储删除文件: {}", objectKey);
        } catch (Exception e) {
            log.warn("存储文件删除失败（继续删除数据库记录）: {}", e.getMessage());
        }

        mediaAssetMapper.deleteById(id);
        log.info("素材删除成功: userId={}, id={}", userId, id);
    }

    @Override
    public PageResult<MediaAsset> semanticSearch(Long userId, String query, int topK) {
        return PageResult.empty(1, topK);
    }
}
