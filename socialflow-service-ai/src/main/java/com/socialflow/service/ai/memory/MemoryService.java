package com.socialflow.service.ai.memory;

import com.socialflow.service.ai.llm.ChatMessage;

import java.util.List;

/**
 * AI 记忆管理服务接口——管理短期对话记忆和长期用户偏好记忆。
 *
 * 【什么是 AI 记忆？为什么需要记忆？】
 *
 * LLM 本身是"无状态"的——每次调用时它不记得之前的对话内容。
 * 为了让 AI 能够理解上下文、记住用户偏好，系统需要人为地管理"记忆"：
 * 将历史对话和用户信息保存下来，在后续调用时作为上下文一并发送给 LLM。
 *
 * 【短期记忆 vs 长期记忆】
 *   - 类型: 短期对话记忆 | 存储位置: Redis | 时效: 24 小时自动过期（TTL） | 内容示例: 当前会话的最近 N 条消息（滑动窗口）
 *   - 类型: 短期摘要记忆 | 存储位置: {@code conversation_session} 表 | 时效: 持久化 | 内容示例: 对过期会话的内容摘要（一段话概括整个对话）
 *   - 类型: 长期偏好记忆 | 存储位置: {@code user_preference} 表 | 时效: 持久化 | 内容示例: 用户写作风格偏好、常用关键词、语气习惯等
 *   - 类型: 长期向量记忆 | 存储位置: {@code memory_vectors} 向量集合 | 时效: 持久化 | 内容示例: 历史交互的语义化记忆，支持相似性搜索
 *
 * 【在系统中的位置】
 *
 * 在每次 AI 调用前，系统会从短期记忆中加载近期对话，从长期记忆中检索相关偏好，
 * 将它们一起注入到提示词中，让 AI 的回复更连贯、更贴合用户习惯。
 */
public interface MemoryService {

    /**
     * 将一条新消息追加到会话的滑动窗口中。
     *
     * 滑动窗口是一种只保留最近 N 条消息的策略，防止上下文过长导致 token 超限。
     * 消息存储在 Redis 中，24 小时后自动过期。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID（每次对话的唯一标识）
     * @param message   要追加的消息
     */
    void appendMessage(Long userId, String sessionId, ChatMessage message);

    /**
     * 加载会话的滑动窗口消息（最近 N 条）。
     *
     * 用于在后续 LLM 调用时提供对话上下文，让 AI "记得"之前聊了什么。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 按时间顺序排列的最近 N 条消息
     */
    List<ChatMessage> loadSession(Long userId, String sessionId);

    /**
     * 将一个会话的对话内容压缩为一段摘要并存储。
     *
     * 当会话过长或即将过期时，使用 LLM 将完整对话总结为一段概括文本，
     * 存入 {@code conversation_session} 表。这样既节省了存储空间，
     * 又保留了关键信息供未来参考。
     *
     * @param userId    用户 ID
     * @param sessionId 要摘要的会话 ID
     */
    void summarizeSession(Long userId, String sessionId);

    /**
     * 在用户的长期向量记忆中进行语义搜索。
     *
     * 根据自然语言查询，在用户积累的向量化记忆中找到语义最相关的内容。
     * 例如查询"用户喜欢什么风格"可能返回之前记录的写作偏好。
     *
     * @param userId 用户 ID
     * @param query  自然语言查询文本
     * @param topK   返回的记忆条目数量
     * @return 按相关度排序的记忆内容文本列表
     */
    List<String> searchLongTerm(Long userId, String query, int topK);

    /**
     * 存储一条新的长期记忆。
     *
     * 将用户的偏好、习惯等信息向量化后存入长期向量记忆集合，
     * 供后续 {@link #searchLongTerm} 语义检索。
     *
     * @param userId     用户 ID
     * @param memoryType 记忆类型（如 "preference"、"writing_style"、"topic_interest"）
     * @param content    记忆内容文本
     */
    void rememberLongTerm(Long userId, String memoryType, String content);
}
