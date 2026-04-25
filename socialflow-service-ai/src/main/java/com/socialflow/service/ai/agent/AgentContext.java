package com.socialflow.service.ai.agent;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 智能体链共享上下文——在多个 Agent 之间传递状态和数据。
 *
 * 【什么是 Agent 间的共享上下文？】
 *
 * 在多智能体协作中，每个 Agent 不是孤立工作的，它需要知道：
 *     - 用户的原始需求是什么（主题、平台、关键词等）
 *     - 前面的 Agent 产出了什么（策划方案、初稿、审稿意见等）
 *     - 当前是第几轮迭代（支持多轮优化）
 * AgentContext 就是这个共享的"黑板"——每个 Agent 从中读取需要的信息，
 * 完成任务后将自己的产出写回其中，供后续 Agent 使用。
 *
 * 【数据流转示例】
 * 
 * 用户输入 → [topic, platform, keywords, ragContext]
 *     ↓
 * Planner Agent → 生成 plan（JSON 格式的策划方案）
 *     ↓
 * Writer Agent → 根据 plan 生成 draft（初稿）
 *     ↓
 * Reviewer Agent → 审查 draft，生成 reviewResult（审稿意见）
 *     ↓
 * Optimizer Agent → 根据 reviewResult 优化 draft（最终稿）
 */
@Data
@Builder
public class AgentContext {

    /** 发起请求的用户 ID，用于审计和个性化。 */
    private Long userId;

    /** 内容创作主题，如"咖啡探店"、"春季穿搭"等。 */
    private String topic;

    /** 目标平台编码，如"xiaohongshu"、"douyin"，不同平台的文风和格式要求不同。 */
    private String platform;

    /** 用户指定的关键词，Agent 在生成内容时需要自然融入这些词。 */
    private String keywords;

    /** RAG 检索到的参考资料上下文，提供给 Agent 作为创作素材。 */
    private String ragContext;

    /**
     * Planner Agent 生成的策划方案（JSON 格式）。
     *
     * 包含字段：{@code angle}（切入角度）、{@code structure}（文章结构）、
     * {@code keyPoints}（核心要点）、{@code wordCount}（目标字数）。
     * Writer Agent 根据此方案撰写初稿。
     */
    private String plan;

    /**
     * 当前草稿内容。
     *
     * 由 Writer Agent 生成初稿，经 Optimizer Agent 优化后更新。
     * 如果有多轮迭代，此字段会在每轮中被更新。
     */
    private String draft;

    /**
     * Reviewer Agent 的审稿结论（JSON 格式）。
     *
     * 包含字段：{@code passed}（是否通过）、{@code issues[]}（发现的问题列表）、
     * {@code suggestions[]}（修改建议列表）。Optimizer Agent 根据此结论优化草稿。
     */
    private String reviewResult;

    /**
     * 当前迭代轮次（从 1 开始）。
     *
     * 如果 Reviewer 判定初稿未通过，可以再跑一轮"Writer → Reviewer → Optimizer"
     * 循环，round 值递增。通常限制最多 2-3 轮以控制 API 开销。
     */
    private int round;

    /**
     * 扩展字段映射表，用于传递不在标准字段中的额外信息。
     *
     * 例如特殊的格式要求、用户偏好设置等，提供灵活的扩展能力。
     */
    private Map<String, Object> extras;
}
