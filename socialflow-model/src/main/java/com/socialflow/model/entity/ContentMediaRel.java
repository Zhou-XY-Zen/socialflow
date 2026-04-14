package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文案与媒体素材的关联关系实体类 —— 对应数据库表 `content_media_rel`
 *
 * 【作用】维护文案（content）和媒体素材（media_asset）之间的多对多关联关系。
 *   一篇文案可以关联多个图片/视频素材，一个素材也可以被多篇文案复用。
 *
 * 【为什么需要它】
 *   文案和素材是多对多的关系，需要一张中间表来维护关联。
 *   本表还记录了素材在文案中的排列顺序（sortOrder），
 *   保证发布时图片/视频按用户指定的顺序展示。
 *
 * 【注意】
 *   本实体不继承 BaseEntity，因为作为关联表它不需要逻辑删除和独立主键，
 *   而是使用 (content_id, media_id) 的联合主键。
 *
 * 【关联关系】
 *   - content_media_rel.content_id → content.id （文案）
 *   - content_media_rel.media_id → media_asset.id （素材）
 *
 * 【使用场景】
 *   - 用户编辑文案时添加/移除配图配视频
 *   - 展示文案详情时查询关联的素材列表
 *   - 发布文案时获取需要一起上传的素材
 */
@Data
@TableName("content_media_rel")
public class ContentMediaRel implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文案 ID
     *
     * 关联 content.id，标识关联的是哪篇文案。
     */
    private Long contentId;

    /**
     * 媒体素材 ID
     *
     * 关联 media_asset.id，标识关联的是哪个素材。
     */
    private Long mediaId;

    /**
     * 排列顺序
     *
     * 素材在文案中的展示顺序，数字越小越靠前。
     * 例如文案配了 3 张图，sortOrder 分别为 1、2、3，表示图片的展示顺序。
     */
    private Integer sortOrder;

    /**
     * 关联创建时间
     *
     * 记录该关联关系创建的时间。
     */
    private LocalDateTime createTime;
}
