package com.socialflow.service.ai.agent;

import com.socialflow.common.annotation.Experimental;
import com.socialflow.model.dto.MultiAgentGenerateDTO;
import reactor.core.publisher.Flux;

/**
 * 多智能体（Multi-Agent）协作服务接口——编排多个 AI 智能体协同完成内容生成。
 *
 * 【什么是多智能体协作（Multi-Agent Collaboration）？】
 *
 * 在 AI 系统中，"智能体"（Agent）是一个具有特定角色和能力的 AI 实体。
 * 单个 LLM 虽然强大，但面对复杂任务时可能表现不佳。多智能体协作的思想是：
 * 将一个复杂任务分解给多个专精不同领域的 Agent，它们像一个团队一样协作完成。
 * 就好比一篇优质文章的诞生过程：策划 → 写作 → 审稿 → 优化。
 *
 * 【本系统的 Agent 链】
 *
 * 系统采用线性流水线式的 Agent 链：
 *     - Planner（策划者）：分析主题，生成写作计划（角度、结构、关键点、字数）
 *     - Writer（写作者）：根据计划撰写初稿
 *     - Reviewer（审稿者）：审查初稿质量，指出问题并给出修改建议
 *     - Optimizer（优化者）：根据审稿意见优化文稿，输出最终版本
 * 每个 Agent 看到的是前面所有 Agent 的输出，形成一条信息传递链。
 *
 * 【流式输出】
 *
 * 使用 Reactor 的 {@link Flux} 实现流式推送，前端可以实时展示：
 *     - 当前正在执行哪个阶段（如"策划中..."、"写作中..."）
 *     - 每个 Agent 的实时输出（打字机效果）
 *     - 最终生成结果
 * 每个推送的字符串是一段 JSON，兼容 SSE（Server-Sent Events）的
 * {@code message} / {@code stage} 事件格式。
 */
@Experimental(since = "Wave 2.5",
        value = "Agent 链结构（PLANNER → WRITER → REVIEWER → OPTIMIZER）已稳定，但每个角色"
                + "的 system prompt、最大重试轮次、退避策略仍在调优；输出格式（SSE event 类型）后续可能扩展。")
public interface MultiAgentService {

    /**
     * 启动多智能体链并流式输出中间阶段和最终结果。
     *
     * @param userId 发起请求的用户 ID
     * @param dto    多智能体生成请求参数（包含主题、平台、关键词等）
     * @return 流式推送的 JSON 字符串，每个元素代表一个阶段通知或一段文本增量
     */
    Flux<String> runStream(Long userId, MultiAgentGenerateDTO dto);
}
