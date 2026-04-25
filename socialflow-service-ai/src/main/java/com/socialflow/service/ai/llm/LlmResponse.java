package com.socialflow.service.ai.llm;

import lombok.Builder;
import lombok.Data;

/**
 * LLM 聊天补全的响应对象，包含生成内容和 Token 用量统计。
 *
 * 【什么是 Token 计费（Token Accounting）？】
 *
 * LLM 的 API 按照处理的 Token 数量计费。Token 是模型处理文本的最小单位，
 * 大约相当于一个汉字或者半个英文单词。每次调用的费用 = 输入 token 费用 + 输出 token 费用。
 * 因此记录每次调用的 token 用量，对于成本监控和用量审计至关重要。
 *
 * 【在系统中的位置】
 *
 * 当 {@link LlmProviderService#chat} 同步调用完成后，会将模型返回的结果
 * 封装成此对象返回给业务层。业务层可以从中获取生成的文本内容和消耗信息。
 */
@Data
@Builder
public class LlmResponse {

    /** LLM 生成的文本内容（即模型的回复）。 */
    private String content;

    /**
     * 输入提示词消耗的 token 数量（Prompt Tokens）。
     *
     * 包括 system 消息、用户问题、历史对话等所有发送给模型的文本。
     */
    private Integer promptTokens;

    /**
     * 模型生成回复消耗的 token 数量（Completion Tokens）。
     *
     * 即模型新产出的文本长度。输出 token 的单价通常高于输入 token。
     */
    private Integer completionTokens;

    /**
     * 总 token 数量 = promptTokens + completionTokens。
     *
     * 用于快速查看本次调用的总消耗。
     */
    private Integer totalTokens;

    /** 实际使用的模型名称（API 返回的，可能与请求时指定的略有不同）。 */
    private String model;

    /**
     * 本次调用的耗时（毫秒）。
     *
     * 从发送请求到收到完整响应的时间，用于性能监控和 SLA 追踪。
     */
    private Long latencyMs;

    /**
     * 实际使用的 Provider 名称（Wave 3.4）。
     *
     * <p>正常情况下与请求时指定的 provider 一致；但当 Resilience4j 熔断触发、
     * 或 LlmRouter 启动跨 provider fallback 时，这里会反映实际兜底用到的 provider。
     * 前端可借此提示用户："已从 DeepSeek 切换到 Qwen"。</p>
     */
    private String providerUsed;

    /**
     * 是否走了降级路径（Wave 3.4）。
     *
     * <p>{@code true} 表示主 provider 失败/熔断，本次响应来自 fallback 链路。
     * 默认 {@code false}（主 provider 正常返回）。</p>
     */
    @Builder.Default
    private boolean fallback = false;
}
