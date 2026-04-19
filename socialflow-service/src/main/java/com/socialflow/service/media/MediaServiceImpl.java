package com.socialflow.service.media;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.socialflow.common.result.PageResult;
import com.socialflow.dao.mapper.MediaAssetMapper;
import com.socialflow.model.entity.MediaAsset;
import com.socialflow.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 * 媒体素材服务实现类 —— 基于腾讯云 COS 对象存储的素材管理。
 *
 * 负责文件上传、数据库元信息管理、文件删除等操作。
 * 底层存储通过 StorageService 接口抽象，当前实现为 CosStorageService。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaAssetMapper mediaAssetMapper;
    private final StorageService storageService;

    /**
     * 上传媒体文件并保存元信息到数据库（Wave 4.5 升级：去重 + 缩略图 + 尺寸）。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>读取文件字节流到内存（用于 hash + 缩略图）</li>
     *   <li>SHA-256 → 查 (user_id, sha256) 命中直接返回旧记录（节省存储 + 带宽）</li>
     *   <li>上传原图到 COS</li>
     *   <li>图片：ImageIO 提取 width/height + Thumbnailator 生成 240×240 缩略图上传</li>
     *   <li>写 MediaAsset 入库，含 sha256/width/height/thumbnailUrl</li>
     * </ol>
     */
    @Override
    public MediaAsset upload(Long userId, MultipartFile file, String tags) {
        try {
            String originalFilename = file.getOriginalFilename();
            String mimeType = file.getContentType();
            String fileType = (mimeType != null && mimeType.startsWith("video/")) ? "VIDEO" : "IMAGE";

            // 1. 读取字节 + 计算 SHA-256
            byte[] bytes = file.getBytes();
            String sha256 = SecureUtil.sha256(new ByteArrayInputStream(bytes));

            // 2. 去重命中检查
            MediaAsset existing = mediaAssetMapper.selectOne(
                    new LambdaQueryWrapper<MediaAsset>()
                            .eq(MediaAsset::getUserId, userId)
                            .eq(MediaAsset::getSha256, sha256)
                            .last("LIMIT 1"));
            if (existing != null) {
                log.info("[Media DEDUP HIT] userId={}, sha256={}, existingId={}",
                        userId, sha256, existing.getId());
                return existing;
            }

            // 3. 上传原图
            String objectKey = "media/" + userId + "/" + UUID.randomUUID() + "_" + originalFilename;
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                storageService.upload(objectKey, is, file.getSize(), mimeType);
            }
            String fileUrl = storageService.getPublicUrl(objectKey);

            // 4. 图片：尺寸 + 缩略图
            Integer width = null;
            Integer height = null;
            String thumbnailUrl = fileUrl; // 默认 = 原图（视频/音频 fallback）
            if ("IMAGE".equals(fileType)) {
                try (InputStream dimStream = new ByteArrayInputStream(bytes)) {
                    BufferedImage img = ImageIO.read(dimStream);
                    if (img != null) {
                        width = img.getWidth();
                        height = img.getHeight();
                        // 大图才生成缩略图，<300px 的图本身就够小
                        if (width > 300 || height > 300) {
                            ByteArrayOutputStream thumbOut = new ByteArrayOutputStream();
                            Thumbnails.of(new ByteArrayInputStream(bytes))
                                    .size(240, 240)
                                    .outputFormat("jpg")
                                    .outputQuality(0.85)
                                    .toOutputStream(thumbOut);
                            byte[] thumbBytes = thumbOut.toByteArray();
                            String thumbKey = "media/" + userId + "/thumb_"
                                    + UUID.randomUUID() + ".jpg";
                            storageService.upload(thumbKey,
                                    new ByteArrayInputStream(thumbBytes),
                                    thumbBytes.length, "image/jpeg");
                            thumbnailUrl = storageService.getPublicUrl(thumbKey);
                        }
                    }
                } catch (Exception e) {
                    log.warn("[Media] 缩略图/尺寸提取失败（不影响主流程）: {}", e.getMessage());
                }
            }

            // 5. 写 DB
            MediaAsset asset = new MediaAsset();
            asset.setUserId(userId);
            asset.setFileName(originalFilename);
            asset.setFileType(fileType);
            asset.setMimeType(mimeType);
            asset.setFileUrl(fileUrl);
            asset.setThumbnailUrl(thumbnailUrl);
            asset.setFileSize(file.getSize());
            asset.setTags(tags);
            asset.setSha256(sha256);
            asset.setWidth(width);
            asset.setHeight(height);
            mediaAssetMapper.insert(asset);

            log.info("素材上传成功: userId={}, fileName={}, sha256={}, dim={}x{}, thumb={}",
                    userId, originalFilename, sha256.substring(0, 8),
                    width, height, !thumbnailUrl.equals(fileUrl));
            return asset;
        } catch (Exception e) {
            log.error("素材上传失败: userId={}", userId, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
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
