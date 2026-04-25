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
            // 原始文件名只用于 DB 展示和 COS 路径片段，必须先规范化：
            //   1. 用 Paths.get 取末尾段（剥离 ../ 之类的相对路径前缀）
            //   2. 仅保留字母 / 数字 / 中文 / `.` / `_` / `-`，其它全部替换为 `_`
            //   3. 截断到 80 字符，防止恶意超长文件名灌爆字段
            // 这样不会影响正常的中英文 + 后缀名的体验，但能挡住 path traversal 与控制字符注入。
            String originalFilename = sanitizeFilename(file.getOriginalFilename());
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

    /**
     * 规范化用户上传的文件名 —— 防止路径遍历 / XSS / 控制字符注入。
     *
     * <p>处理策略：</p>
     * <ol>
     *   <li>null / 空 → 兜底返回 {@code "unnamed"}</li>
     *   <li>{@code Paths.get(...).getFileName()} 取末尾段，剥掉 {@code ../} 之类的相对前缀
     *       和 Windows 风格的反斜杠</li>
     *   <li>仅保留 [a-zA-Z0-9_.-] 和中日韩文（Unicode block CJK Unified Ideographs），
     *       其它（含空格 / 引号 / 标签字符）替换为 {@code _}</li>
     *   <li>裁到最多 80 字符，避免超长文件名灌爆 DB 字段或 COS key 限制</li>
     * </ol>
     *
     * <p>对正常的中英文文件名（如 {@code "我的封面.jpg"} / {@code "screenshot 2026.png"}）
     * 影响极小，但能挡住 {@code "../../../etc/passwd"}、{@code "<script>.png"} 等。</p>
     */
    static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) return "unnamed";
        // Paths.get 在 Linux 上不识别反斜杠，先手动替换为正斜杠再剥末尾段
        String stripped = raw.replace('\\', '/');
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(stripped).getFileName();
            if (p != null) stripped = p.toString();
        } catch (java.nio.file.InvalidPathException ignored) {
            // 极端情况（NUL 字符等）按原值继续，下一步的正则会兜住
        }
        // 仅保留字母数字 / 下划线 / 连字符 / 点 / 中日韩字符
        String safe = stripped.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]", "_");
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) return "unnamed";
        if (safe.length() > 80) {
            // 保留扩展名（最多 8 字符，cover 常见 .jpeg .webp）
            int dot = safe.lastIndexOf('.');
            if (dot > 0 && safe.length() - dot <= 9) {
                String ext = safe.substring(dot);
                safe = safe.substring(0, Math.min(80 - ext.length(), dot)) + ext;
            } else {
                safe = safe.substring(0, 80);
            }
        }
        return safe;
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
