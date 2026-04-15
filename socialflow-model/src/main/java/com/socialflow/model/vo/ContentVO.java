package com.socialflow.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文案详情视图对象（VO）—— 用于向前端返回文案的完整信息
 *
 * 【作用】封装一篇文案的所有展示信息，包括基本属性、生成参数、质量评分，
 *   以及安全审核警告和 RAG 引用来源等附加信息。
 *   是前端展示文案详情页时使用的核心数据结构。
 *
 * 【对应 API 接口（作为返回值）】
 *   - GET  /api/content/{id}     —— 获取单篇文案详情
 *   - POST /api/content/generate —— 生成文案后返回结果
 *   - POST /api/content/rewrite  —— 改写文案后返回结果
 *
 * 【与 Content 实体的区别】
 *   Content 是数据库实体，包含 userId 等内部字段；
 *   ContentVO 是面向前端的视图对象，剔除了敏感/无用字段，并增加了
 *   guardrailWarnings（安全警告）和 ragSources（RAG 引用来源）等聚合信息。
 */
@Data
@Schema(description = "content detail")
public class ContentVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文案 ID
     *
     * 文案的唯一标识。
     */
    private Long id;

    /**
     * 文案标题
     *
     * 文案的主标题。
     */
    private String title;

    /**
     * 文案正文内容
     *
     * 完整的文案正文，支持纯文本或 Markdown 格式。
     */
    private String body;

    /**
     * 目标发布平台
     *
     * 可选值："XIAOHONGSHU"、"DOUYIN"、"WECHAT_MOMENT"、"WECHAT_MP"
     */
    private String platform;

    /**
     * 文案状态
     *
     * 可选值："DRAFT"（草稿）、"SCHEDULED"（已排期）、"PUBLISHING"（发布中）、
     *         "PUBLISHED"（已发布）、"FAILED"（发布失败）
     */
    private String status;

    /**
     * 使用的 AI 模型名称
     *
     * 生成该文案时使用的大模型。
     * 示例："gpt-4o"、"claude-3-sonnet"
     * 手动创建的文案为空。
     */
    private String model;

    /**
     * Token 消耗量
     *
     * AI 生成该文案时消耗的总 Token 数。
     */
    private Integer tokenUsage;

    /**
     * 文案标签
     *
     * 用于分类和搜索的标签，多个标签用逗号分隔。
     */
    private String tags;

    /**
     * AI 质量评分
     *
     * 评估系统对文案质量的综合打分，范围通常为 0.00 ~ 10.00。
     * 未评估时为空。
     */
    private BigDecimal evalScore;

    /**
     * 排期发布时间
     *
     * 用户设定的定时发布时间。为空表示不排期。
     */
    private LocalDateTime scheduledTime;

    /**
     * 实际发布时间
     *
     * 文案成功发布到平台的时间。
     */
    private LocalDateTime publishedTime;

    /**
     * 文案创建时间
     *
     * 文案记录的创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 安全护栏警告列表
     *
     * 如果文案生成过程中触发了内容安全规则但未被拦截（actionTaken=WARNING），
     * 会在这里列出警告信息，提醒用户注意。
     * 示例：["包含可能的夸大宣传用语", "检测到敏感话题"]
     * 未触发任何规则时为空列表。
     */
    @Schema(description = "guardrail warnings raised during generation")
    private List<String> guardrailWarnings;

    /**
     * RAG 引用来源列表
     *
     * 如果文案是通过 RAG 模式生成的，此字段列出 AI 参考的知识库切片来源。
     * 用户可以据此查看 AI 引用了哪些文档的哪些内容。
     * 未使用 RAG 时为空。
     */
    @Schema(description = "source chunks from RAG (if any)")
    private List<RagSourceVO> ragSources;

    /**
     * 实际使用的 LLM provider（Wave 3.4）。
     *
     * 当主 provider 失败/熔断、走 fallback 时，这里反映兜底用到的 provider。
     * 前端可显示 "已从 DeepSeek 切换到 Qwen" 提示。
     */
    @Schema(description = "actual LLM provider used (may differ from requested if fallback)")
    private String providerUsed;

    /**
     * 是否走了 fallback 路径（Wave 3.4）。
     */
    @Schema(description = "true if response came from fallback chain")
    private Boolean fallback;
}
