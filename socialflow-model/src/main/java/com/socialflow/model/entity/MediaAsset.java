package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 媒体素材实体类 —— 对应数据库表 `media_asset`
 *
 * 【作用】存储用户上传的图片、视频等媒体素材文件的元信息。
 *   实际文件存储在对象存储服务（如阿里云 OSS、MinIO）中，本表只保存文件的元数据和访问 URL。
 *
 * 【为什么需要它】
 *   社交媒体文案通常需要配图或配视频。本表提供统一的素材管理能力，
 *   让用户可以上传、管理、复用媒体素材，并在创建文案时关联使用。
 *
 * 【关联关系】
 *   - media_asset.user_id → sys_user.id （上传者）
 *   - content_media_rel 表将 media_asset 与 content 进行多对多关联
 *
 * 【使用场景】
 *   - 用户在"素材库"页面上传、浏览、搜索素材
 *   - 创建文案时选择关联的图片/视频
 *   - 发布文案时将关联的素材一起提交到目标平台
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("media_asset")
public class MediaAsset extends BaseEntity {

    /**
     * 上传者用户 ID
     *
     * 关联 sys_user.id，标识该素材是哪个用户上传的。
     */
    private Long userId;

    /**
     * 文件名称
     *
     * 上传时的原始文件名。
     * 示例："product_photo_01.jpg"
     */
    private String fileName;

    /**
     * 文件类型
     *
     * 按大类划分的文件类型。
     * 可选值："IMAGE"（图片）、"VIDEO"（视频）、"AUDIO"（音频）
     */
    private String fileType;

    /**
     * MIME 类型
     *
     * 文件的 MIME 标准类型，用于浏览器和客户端正确解析文件。
     * 示例："image/jpeg"、"image/png"、"video/mp4"
     */
    private String mimeType;

    /**
     * 文件访问 URL
     *
     * 文件在对象存储服务中的完整访问地址。
     * 示例："https://oss.example.com/media/2024/01/photo.jpg"
     */
    private String fileUrl;

    /**
     * 缩略图 URL
     *
     * 图片或视频缩略图的访问地址，用于在素材列表页快速预览。
     * 可为空（如音频文件没有缩略图）。
     */
    private String thumbnailUrl;

    /**
     * 文件大小（字节）
     *
     * 文件的大小，单位为字节（Byte）。
     * 示例：1048576 表示 1MB。
     */
    private Long fileSize;

    /**
     * 素材标签
     *
     * 用于分类和搜索的标签，多个标签用逗号分隔。
     * 示例："产品图,护肤,春季"
     */
    private String tags;

    /**
     * 向量数据库中的向量 ID
     *
     * 如果对该素材进行了向量化（如图片 embedding），
     * 此字段记录在向量数据库中对应的 ID，用于图片相似性搜索。
     * 未向量化时为空。
     */
    private String vectorId;

    /**
     * 文件内容 SHA-256 哈希（Wave 4.5）。
     *
     * <p>上传时先按文件字节流计算 SHA-256；同一用户上传相同文件直接复用现有记录，
     * 节省 COS 存储空间和上传带宽。</p>
     */
    private String sha256;

    /** 图像宽度（像素）—— Wave 4.5。视频/音频留空。 */
    private Integer width;

    /** 图像高度（像素）—— Wave 4.5。视频/音频留空。 */
    private Integer height;
}
