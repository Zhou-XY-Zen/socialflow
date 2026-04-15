package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图像生成去重缓存（Wave 4.2）—— 对应 image_asset_cache 表。
 *
 * <p>同一 (user_id, prompt_hash) 命中时直接返回 mediaIds，
 * 不再调用 DashScope wanx 重新生成 4 张图。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("image_asset_cache")
public class ImageAssetCache extends BaseEntity {

    /** 拥有者用户 ID */
    private Long userId;

    /** SHA-256(prompt + ":" + model + ":" + size)，64 字符 hex */
    private String promptHash;

    /** JSON 数组：[mediaAssetId1, mediaAssetId2, ...]，存的是 MediaAsset.id */
    private String mediaIds;

    /** 原始 prompt（审计/调试用，可截断） */
    private String prompt;

    /** wanx 模型名 */
    private String model;

    /** 图像尺寸如 1024*1024 */
    private String imageSize;

    /** 命中次数，每次缓存命中 +1，便于观察 hot prompt */
    private Integer hitCount;
}
