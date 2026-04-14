package com.socialflow.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文案生成请求 DTO —— 用于 AI 生成单篇文案的接口入参
 *
 * 【作用】封装用户请求 AI 生成文案时需要传递的所有参数。
 *   前端调用生成接口时，将用户在页面上填写的各项配置（主题、平台、语气等）
 *   封装为本 DTO 传给后端。
 *
 * 【对应 API 接口】
 *   POST /api/content/generate  —— 单篇文案生成
 *
 * 【使用场景】
 *   用户在"文案生成"页面填写主题、选择平台和语气等参数后，点击"生成"按钮，
 *   前端将表单数据封装为本 DTO 提交到后端。
 *
 * 【继承关系】
 *   - ContentBatchGenerateDTO 继承本类，增加了多平台批量生成的能力
 *   - MultiAgentGenerateDTO 继承本类，增加了多 Agent 协作生成的配置
 */
@Data
@Schema(description = "content generation request")
public class ContentGenerateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文案主题/核心创意（必填）
     *
     * 告诉 AI 要围绕什么主题来生成文案，是最核心的输入参数。
     * 示例："春季护肤好物推荐"、"新品口红试色测评"
     */
    @NotBlank
    @Schema(description = "topic or core idea", required = true)
    private String topic;

    /**
     * 关键词列表（选填）
     *
     * 希望文案中包含的关键词/标签，AI 会尽量在文案中融入这些词。
     * 示例：["护肤", "防晒", "平价好物"]
     */
    @Schema(description = "keyword list")
    private List<String> keywords;

    /**
     * 目标发布平台（必填）
     *
     * 指定文案的目标平台，不同平台的文案风格和格式差异较大。
     * 可选值："XIAOHONGSHU"（小红书）、"DOUYIN"（抖音）、
     *         "WECHAT_MOMENT"（朋友圈）、"WECHAT_MP"（微信公众号）
     */
    @NotBlank
    @Schema(description = "XIAOHONGSHU / DOUYIN / WECHAT_MOMENT / WECHAT_MP", required = true)
    private String platform;

    /**
     * Prompt 模板 ID（选填）
     *
     * 指定使用哪个 Prompt 模板来生成文案。
     * 关联 prompt_template.id。
     * 为空时系统自动使用该平台的默认模板。
     */
    @Schema(description = "prompt template id; default template used if null")
    private Long templateId;

    /**
     * 文案语气/风格（选填）
     *
     * 指定生成文案的语气风格。
     * 可选值："casual"（随性日常）、"professional"（专业正式）、
     *         "humorous"（幽默搞笑）、"inspiring"（激励鼓舞）
     * 为空时使用用户偏好设置中的默认语气。
     */
    @Schema(description = "casual / professional / humorous / inspiring")
    private String tone;

    /**
     * 期望字数（选填）
     *
     * 指定文案的目标字数。AI 会尽量控制在该字数附近。
     * 为空时由 AI 根据平台和内容自行决定长度。
     */
    @Schema(description = "desired word count")
    private Integer wordCount;

    /**
     * 产品信息（选填）
     *
     * 当文案属于产品推广/种草类型时，提供要推广的产品详细信息。
     * AI 会将这些信息融入文案中。
     * 示例："XX 品牌防晒霜 SPF50+，价格 128 元，主打轻薄不油腻"
     */
    @Schema(description = "product info for promotion scenarios")
    private String productInfo;

    /**
     * 指定 AI 模型（选填）
     *
     * 覆盖系统默认的 AI 模型，使用指定的模型生成文案。
     * 示例："gpt-4o"、"claude-3-sonnet"
     * 为空时使用系统配置的默认模型。
     */
    @Schema(description = "specific model override")
    private String model;

    /**
     * 知识库 ID（选填）
     *
     * 关联 knowledge_base.id。指定后启用 RAG（检索增强生成）模式，
     * AI 会从该知识库中检索相关内容作为参考来生成文案。
     * 为空时不使用 RAG，仅依赖大模型自身知识。
     */
    @Schema(description = "knowledge base id; when set enables RAG generation")
    private Long kbId;

    /**
     * 是否启用内容安全护栏（选填）
     *
     * 控制是否在生成前后进行内容安全审核。
     * true = 启用（默认值），false = 关闭。
     * 建议始终开启以避免生成违规内容。
     */
    @Schema(description = "enable guardrails (default true)")
    private Boolean enableGuardrails;

    /**
     * 大模型温度参数（选填）
     *
     * 控制 AI 生成的随机性。取值范围 0.0 ~ 2.0。
     * - 值越低，生成结果越确定和保守
     * - 值越高，生成结果越多样和创意
     * 默认值 0.7，通常不需要修改。
     */
    @Schema(description = "LLM temperature, default 0.7")
    private Double temperature;
}
