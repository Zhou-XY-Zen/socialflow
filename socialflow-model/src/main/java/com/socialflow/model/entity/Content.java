package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文案内容实体类 —— 对应数据库表 `content`
 *
 * 【作用】存储用户通过 AI 生成或手动创建的所有文案内容。
 *   这是本系统最核心的业务表，一条记录就是一篇文案。
 *
 * 【为什么需要它】
 *   文案是本系统的核心产出物。用户使用 AI 生成文案后，需要保存、编辑、排期、发布。
 *   本表记录文案的全部信息：内容本身、生成参数、目标平台、发布状态、质量评分等。
 *
 * 【字段说明】
 *   - 每篇文案属于一个用户（userId）
 *   - 指定了目标平台（platform），如小红书、抖音等
 *   - 有一个生命周期状态（status）：草稿 → 已排期 → 发布中 → 已发布/失败
 *   - 如果是 AI 生成的，会记录使用的模型（aiModel）和消耗的 Token 数（tokenUsage）
 *
 * 【关联关系】
 *   - content.user_id → sys_user.id （所属用户）
 *   - content.template_id → prompt_template.id （使用的 Prompt 模板）
 *   - content.kb_id → knowledge_base.id （RAG 生成时关联的知识库）
 *   - content_version 表记录这篇文案的所有历史版本
 *   - content_media_rel 表关联这篇文案用到的图片/视频素材
 *   - publish_task 表记录这篇文案的发布任务
 *
 * 【使用场景】
 *   - 用户请求 AI 生成文案时，创建一条记录
 *   - 用户编辑文案时更新 body 字段
 *   - 用户排期发布时设置 scheduledTime
 *   - 发布成功后更新 publishedTime 和 publishedUrl
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("content")
public class Content extends BaseEntity {

    /**
     * 所属用户 ID
     *
     * 关联 sys_user.id，标识这篇文案是哪个用户创建的。
     */
    private Long userId;

    /**
     * 文案标题
     *
     * 文案的主标题，用于列表展示和搜索。
     * 示例："春季护肤必备好物推荐"
     */
    private String title;

    /**
     * 文案正文内容
     *
     * 文案的完整正文，是 AI 生成或用户手写的主体文字。
     * 支持纯文本或 Markdown 格式。
     */
    private String body;

    /**
     * 目标发布平台
     *
     * 标识这篇文案是为哪个社交媒体平台准备的。
     * 可选值："XIAOHONGSHU"（小红书）、"DOUYIN"（抖音）、"WECHAT_MOMENT"（朋友圈）、"WECHAT_MP"（微信公众号）
     */
    private String platform;

    /**
     * 文案状态
     *
     * 标识文案在生命周期中的阶段。
     * 可选值："DRAFT"（草稿）、"SCHEDULED"（已排期）、"PUBLISHING"（发布中）、
     *         "PUBLISHED"（已发布）、"FAILED"（发布失败）
     */
    private String status;

    /**
     * 排期发布时间
     *
     * 用户设定的定时发布时间。为空表示不排期（手动发布或立即发布）。
     * 定时任务会在到达该时间时触发自动发布流程。
     */
    private LocalDateTime scheduledTime;

    /**
     * 实际发布时间
     *
     * 文案成功发布到目标平台的时间。发布成功后由系统自动填写。
     */
    private LocalDateTime publishedTime;

    /**
     * 发布后的文章链接
     *
     * 文案发布到平台后的访问 URL。
     * 示例："https://www.xiaohongshu.com/discovery/item/xxxx"
     */
    private String publishedUrl;

    /**
     * 使用的 Prompt 模板 ID
     *
     * 关联 prompt_template.id。记录生成该文案时选用的 Prompt 模板。
     * 如果是手动创建的文案，该字段为空。
     */
    private Long templateId;

    /**
     * 生成参数快照（JSON 格式）
     *
     * 记录 AI 生成该文案时的完整参数，便于复现和追溯。
     * JSON 格式示例：{"topic": "护肤", "tone": "casual", "temperature": 0.7, ...}
     */
    private String generationParams;

    /**
     * 文案标签
     *
     * 用于分类和搜索的标签，多个标签用逗号分隔。
     * 示例："护肤,美妆,春季"
     */
    private String tags;

    /**
     * AI 模型名称
     *
     * 生成该文案时使用的大模型名称。
     * 示例："gpt-4o"、"claude-3-sonnet"、"deepseek-chat"
     * 手动创建的文案该字段为空。
     */
    private String aiModel;

    /**
     * Token 消耗量
     *
     * AI 生成该文案时消耗的总 Token 数（包含 prompt + completion）。
     * 用于用量统计和成本估算。手动创建的文案该字段为空。
     */
    private Integer tokenUsage;

    /**
     * 关联的知识库 ID
     *
     * 关联 knowledge_base.id。如果生成文案时启用了 RAG（检索增强生成），
     * 会记录使用的知识库 ID。未使用 RAG 时为空。
     */
    private Long kbId;

    /**
     * AI 质量评分
     *
     * 由评估系统（Eval）对文案质量打出的综合分数，范围通常为 0.00 ~ 10.00。
     * 未经过评估的文案该字段为空。
     */
    private BigDecimal evalScore;

    /**
     * 乐观锁版本号（Wave 4.3）。
     *
     * <p>MyBatis-Plus 在 update 时会自动 {@code WHERE version = ?} 并在 SET 中递增。
     * 如果两个请求并发更新同一篇文案，第二个请求会因为 version 不匹配而 affected=0，
     * 从而避免后写覆盖先写。</p>
     */
    @Version
    private Integer version;
}
