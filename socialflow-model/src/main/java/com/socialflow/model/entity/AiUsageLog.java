package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 调用日志实体类 —— 对应数据库表 `ai_usage_log`
 *
 * 【作用】记录系统每次调用 AI 大模型 API 的详细信息，
 *   包括使用的模型、Token 消耗、预估费用、响应延迟等。
 *   是系统的 AI 用量监控和成本控制的核心数据来源。
 *
 * 【为什么需要它】
 *   AI API 调用是按 Token 计费的，本系统需要：
 *   1. 为用户提供用量统计和成本分析报表
 *   2. 监控 API 调用的性能（延迟）和异常
 *   3. 帮助用户评估不同模型的性价比
 *   4. 实现用量预警和配额控制
 *
 * 【关联关系】
 *   - ai_usage_log.user_id → sys_user.id （调用者）
 *   - ai_usage_log.content_id → content.id （关联的文案，若有的话）
 *
 * 【使用场景】
 *   - 每次调用 AI API 后自动写入一条日志
 *   - 用户在"用量统计"页面查看自己的消耗报表
 *   - 管理员监控系统整体的 AI 调用情况
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_usage_log")
public class AiUsageLog extends BaseEntity {

    /**
     * 调用者用户 ID
     *
     * 关联 sys_user.id，标识是哪个用户触发的 AI 调用。
     */
    private Long userId;

    /**
     * AI 模型名称
     *
     * 本次调用使用的大模型标识。
     * 示例："gpt-4o"、"claude-3-sonnet"、"deepseek-chat"
     */
    private String model;

    /**
     * AI 服务提供商
     *
     * 该模型所属的服务商。
     * 可选值："OPENAI"、"ANTHROPIC"、"DEEPSEEK"、"MOONSHOT" 等。
     */
    private String provider;

    /**
     * 输入 Token 数（Prompt Tokens）
     *
     * 发送给大模型的提示词部分消耗的 Token 数量。
     * 包括 system prompt + user prompt + 上下文等。
     */
    private Integer promptTokens;

    /**
     * 输出 Token 数（Completion Tokens）
     *
     * 大模型生成的回复内容消耗的 Token 数量。
     */
    private Integer completionTokens;

    /**
     * 总 Token 数
     *
     * promptTokens + completionTokens 的总和。
     */
    private Integer totalTokens;

    /**
     * 预估费用（美元）
     *
     * 根据模型的定价和 Token 消耗量计算出的预估费用。
     * 示例：0.0150 表示约 1.5 美分。
     */
    private BigDecimal costEstimate;

    /**
     * 请求类型
     *
     * 标识本次 AI 调用的业务场景。
     * 可选值："GENERATE"（生成文案）、"REWRITE"（改写文案）、
     *         "EVAL"（质量评估）、"CHAT"（对话）、"EMBEDDING"（向量化）等。
     */
    private String requestType;

    /**
     * 关联的文案 ID
     *
     * 关联 content.id。如果本次调用是为了生成/改写某篇文案，记录该文案 ID。
     * 如果是通用对话或 Embedding 等操作，该字段为空。
     */
    private Long contentId;

    /**
     * 响应延迟（毫秒）
     *
     * 从发起 API 调用到收到完整响应所花费的时间，单位为毫秒。
     * 用于监控 API 性能和排查超时问题。
     */
    private Integer latencyMs;
}
