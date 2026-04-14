package com.socialflow.service.ai.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 聊天消息对象，表示 LLM 对话中的一条消息。
 *
 * 【什么是 Role / Content 模式？】
 *
 * 大语言模型的聊天 API 采用"角色-内容"模式来组织对话。每条消息都有两个核心字段：
 *     - role（角色）：标识这条消息是"谁"说的
 *     - content（内容）：消息的实际文本
 *
 * 【常见角色说明】
 *     - system（系统）：设定 AI 的行为准则和角色定位，通常放在对话开头，
 *       例如"你是一名社交媒体文案专家"
 *     - user（用户）：真实用户输入的问题或指令
 *     - assistant（助手）：AI 之前生成的回复，用于给模型提供上下文记忆
 *     - tool（工具）：工具调用的返回结果，用于 Function Calling 场景
 *
 * 【在系统中的位置】
 *
 * 本类是 LLM 调用的基本数据单元。{@link PromptService} 将模板渲染为
 * ChatMessage 列表，然后传给 {@link LlmProviderService#chat} 方法。
 * 实现了 {@link Serializable} 以便在 Redis 等缓存中存储会话历史。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息角色：{@code system}（系统）| {@code user}（用户）
     * | {@code assistant}（助手）| {@code tool}（工具）。
     */
    private String role;

    /** 消息的文本内容。 */
    private String content;

    /** 快捷构造：创建一条系统角色消息（用于设定 AI 行为准则）。 */
    public static ChatMessage system(String content)    { return new ChatMessage("system", content); }

    /** 快捷构造：创建一条用户角色消息（用于传递用户输入）。 */
    public static ChatMessage user(String content)      { return new ChatMessage("user", content); }

    /** 快捷构造：创建一条助手角色消息（用于传递 AI 历史回复）。 */
    public static ChatMessage assistant(String content) { return new ChatMessage("assistant", content); }
}
