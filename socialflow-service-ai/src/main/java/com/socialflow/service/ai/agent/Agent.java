package com.socialflow.service.ai.agent;

import com.socialflow.common.enums.AgentRole;
import com.socialflow.service.ai.llm.LlmConfig;

/**
 * 单个智能体（Agent）接口——代表多智能体链中的一个独立角色。
 *
 * 【什么是 Agent（智能体）？】
 *
 * 在 AI 工程中，Agent 是一个具有明确角色定位、专属系统提示词和特定能力的 AI 实体。
 * 每个 Agent 本质上是对同一个 LLM 的不同"人设"封装——通过不同的 System Prompt
 * 让同一个模型扮演不同角色（如策划者、写作者、审稿者等）。
 *
 * 【Agent 的三要素】
 *     - 角色（Role）：通过 {@link AgentRole} 枚举标识，如 PLANNER、WRITER 等
 *     - 系统提示词（System Prompt）：定义该 Agent 的专业领域和行为规范
 *     - 执行逻辑（Execute）：根据上下文和 LLM 配置完成本角色的工作
 *
 * 【在系统中的位置】
 *
 * 多个 Agent 实现类被 {@link MultiAgentService} 编排为一条协作链。
 * 每个 Agent 从共享的 {@link AgentContext} 中读取前序 Agent 的输出，
 * 完成自己的任务后将结果写回上下文，供后续 Agent 使用。
 */
public interface Agent {

    /**
     * 返回此 Agent 的角色枚举值。
     *
     * 用于在多智能体链中标识和查找特定角色的 Agent。
     *
     * @return Agent 角色枚举，如 PLANNER、WRITER、REVIEWER、OPTIMIZER
     */
    AgentRole role();

    /**
     * 返回此 Agent 的系统提示词（System Prompt）。
     *
     * 系统提示词是发送给 LLM 的第一条消息，用于定义 Agent 的角色、
     * 专业领域和行为准则。例如 Writer Agent 的提示词可能是
     * "你是一名资深的社交媒体文案撰写专家..."。
     *
     * @return 系统提示词文本
     */
    String systemPrompt();

    /**
     * 执行此 Agent 的任务，返回原始文本输出。
     *
     * Agent 从上下文中读取所需信息（如策划方案、初稿等），
     * 结合自己的系统提示词调用 LLM 生成内容，然后返回结果文本。
     *
     * @param ctx       共享上下文对象，包含前序 Agent 的输出和任务参数
     * @param llmConfig LLM 调用配置（模型、温度、API Key 等）
     * @return Agent 生成的文本输出
     */
    String execute(AgentContext ctx, LlmConfig llmConfig);
}
