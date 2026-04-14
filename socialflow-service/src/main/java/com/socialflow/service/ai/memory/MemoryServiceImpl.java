package com.socialflow.service.ai.memory;

import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.service.ai.embedding.EmbeddingService;
import com.socialflow.service.ai.embedding.VectorStoreService;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmResponse;
import com.socialflow.service.ai.llm.LlmRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 记忆管理服务实现——基于 Redis 的短期记忆和基于向量数据库的长期记忆。
 *
 * 【短期记忆（Redis）】
 *   使用 Redis List 结构存储会话的最近 N 条消息（滑动窗口），
 *   24 小时 TTL 自动过期，防止 Redis 内存无限增长。
 *
 * 【长期记忆（pgvector）】
 *   将用户偏好、写作风格等信息向量化后存入 memory_vectors 集合，
 *   支持语义相似度搜索，实现"AI 记住用户习惯"的能力。
 *
 * 【会话摘要】
 *   利用 LLM 将完整会话压缩为一段概要文本，节省存储空间的同时保留关键信息。
 */
@Slf4j
@Service
public class MemoryServiceImpl implements MemoryService {

    /** Redis 操作模板——用于存取短期会话记忆 */
    private final StringRedisTemplate redisTemplate;

    /** LLM 路由器——用于调用大模型生成会话摘要 */
    private final LlmRouter llmRouter;

    /** 向量数据库服务——用于存储和检索长期向量记忆 */
    private final VectorStoreService vectorStoreService;

    /** 文本嵌入服务——将文本转换为向量 */
    private final EmbeddingService embeddingService;

    /** 系统级 API 密钥，用于内部 LLM 调用 */
    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    /** 默认 LLM 提供者 */
    @Value("${socialflow.ai.default-provider:DEEPSEEK}")
    private String defaultProvider;

    /** 滑动窗口大小——每个会话最多保留的消息条数 */
    private static final int SLIDING_WINDOW_SIZE = 20;

    /** 会话 TTL——Redis 中会话数据的过期时间（小时） */
    private static final int SESSION_TTL_HOURS = 24;

    /** 会话摘要的系统提示词 */
    private static final String SUMMARY_SYSTEM_PROMPT = "你是对话摘要助手。请用简洁的一段话总结以下对话的要点。";

    public MemoryServiceImpl(StringRedisTemplate redisTemplate,
                             LlmRouter llmRouter,
                             VectorStoreService vectorStoreService,
                             EmbeddingService embeddingService) {
        this.redisTemplate = redisTemplate;
        this.llmRouter = llmRouter;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
    }

    /**
     * 将一条消息追加到会话的滑动窗口中。
     *
     * 【实现逻辑】
     *   1. 将 ChatMessage 序列化为 JSON 字符串
     *   2. RPUSH 追加到 Redis List 尾部
     *   3. LTRIM 保留最后 SLIDING_WINDOW_SIZE 条消息（滑动窗口裁剪）
     *   4. 设置 TTL 为 24 小时，防止过期会话占用内存
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param message   要追加的消息
     */
    @Override
    public void appendMessage(Long userId, String sessionId, ChatMessage message) {
        String key = buildSessionKey(userId, sessionId);
        String json = JsonUtil.toJson(message);

        // RPUSH：将消息追加到列表尾部
        redisTemplate.opsForList().rightPush(key, json);

        // LTRIM：只保留最后 SLIDING_WINDOW_SIZE 条消息（滑动窗口）
        // 例如 LTRIM key -20 -1 表示保留最后 20 条
        redisTemplate.opsForList().trim(key, -SLIDING_WINDOW_SIZE, -1);

        // 每次追加消息时刷新 TTL，保持活跃会话不过期
        redisTemplate.expire(key, SESSION_TTL_HOURS, TimeUnit.HOURS);

        log.debug("【记忆】追加消息, key={}, role={}", key, message.getRole());
    }

    /**
     * 加载会话的滑动窗口消息（最近 N 条）。
     *
     * 从 Redis List 中取出全部消息，反序列化为 ChatMessage 列表。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 按时间顺序排列的消息列表
     */
    @Override
    public List<ChatMessage> loadSession(Long userId, String sessionId) {
        String key = buildSessionKey(userId, sessionId);

        // LRANGE 0 -1：获取列表中的所有元素
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            log.debug("【记忆】会话为空, key={}", key);
            return List.of();
        }

        // 逐条反序列化 JSON 为 ChatMessage 对象
        List<ChatMessage> messages = new ArrayList<>(jsonList.size());
        for (String json : jsonList) {
            try {
                ChatMessage msg = JsonUtil.fromJson(json, ChatMessage.class);
                messages.add(msg);
            } catch (Exception e) {
                log.warn("【记忆】消息反序列化失败, json={}", json, e);
            }
        }

        log.debug("【记忆】加载会话, key={}, 消息数={}", key, messages.size());
        return messages;
    }

    /**
     * 将一个会话的对话内容压缩为一段摘要。
     *
     * 【实现逻辑】
     *   1. 加载会话中的所有消息
     *   2. 将消息拼接为对话文本
     *   3. 调用 LLM 生成摘要
     *   4. 将摘要存储到 Redis（独立 key，便于后续检索）
     *
     * @param userId    用户 ID
     * @param sessionId 要摘要的会话 ID
     */
    @Override
    public void summarizeSession(Long userId, String sessionId) {
        // 加载会话中的所有消息
        List<ChatMessage> messages = loadSession(userId, sessionId);
        if (messages.isEmpty()) {
            log.info("【记忆】会话为空，跳过摘要, userId={}, sessionId={}", userId, sessionId);
            return;
        }

        // 将消息列表拼接为可读的对话文本
        StringBuilder dialogue = new StringBuilder();
        for (ChatMessage msg : messages) {
            dialogue.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        // 调用 LLM 生成摘要
        List<ChatMessage> promptMessages = List.of(
                ChatMessage.system(SUMMARY_SYSTEM_PROMPT),
                ChatMessage.user("请用一段话总结以下对话的要点：\n" + dialogue)
        );
        LlmConfig config = LlmConfig.builder()
                .temperature(0.3)  // 低温度，确保摘要准确
                .maxTokens(512)
                .apiKey(systemApiKey)
                .userId(userId)
                .build();

        LlmResponse response = llmRouter
                .get(LlmProvider.valueOf(defaultProvider.toUpperCase()))
                .chat(promptMessages, config);
        String summary = response.getContent();

        // 将摘要存储到 Redis，使用独立的 key
        String summaryKey = buildSessionKey(userId, sessionId) + ":summary";
        redisTemplate.opsForValue().set(summaryKey, summary, SESSION_TTL_HOURS, TimeUnit.HOURS);

        log.info("【记忆】会话摘要完成, userId={}, sessionId={}, 摘要长度={}",
                userId, sessionId, summary.length());
    }

    /**
     * 在用户的长期向量记忆中进行语义搜索。
     *
     * 【实现逻辑】
     *   1. 将查询文本通过 EmbeddingService 转换为向量
     *   2. 在 memory_vectors 集合中按 user_id 过滤搜索
     *   3. 提取命中结果的文本内容并返回
     *
     * @param userId 用户 ID
     * @param query  自然语言查询文本
     * @param topK   返回条数
     * @return 按相关度排序的记忆内容列表
     */
    @Override
    public List<String> searchLongTerm(Long userId, String query, int topK) {
        // 将查询文本转换为向量
        float[] queryVector = embeddingService.embed(query);

        // 构建过滤条件——按 user_id 隔离，确保只搜索当前用户的记忆
        Map<String, Object> filter = new HashMap<>();
        filter.put("user_id", userId);

        // 在 memory_vectors 集合中执行相似度搜索
        List<VectorStoreService.SearchHit> hits = vectorStoreService.search(
                CommonConstants.VC_MEMORY_VECTORS, queryVector, filter, topK);

        // 从命中结果中提取文本内容
        List<String> results = new ArrayList<>();
        for (VectorStoreService.SearchHit hit : hits) {
            Object content = hit.metadata().get("content");
            if (content != null) {
                results.add(content.toString());
            }
        }

        log.debug("【记忆】长期记忆搜索, userId={}, query='{}', topK={}, 命中={}",
                userId, query, topK, results.size());
        return results;
    }

    /**
     * 存储一条新的长期记忆。
     *
     * 【实现逻辑】
     *   1. 将内容文本通过 EmbeddingService 转换为向量
     *   2. 附带 user_id 和 memory_type 元数据
     *   3. Upsert 到 memory_vectors 集合中
     *
     * @param userId     用户 ID
     * @param memoryType 记忆类型（如 preference、writing_style、topic_interest）
     * @param content    记忆内容文本
     */
    @Override
    public void rememberLongTerm(Long userId, String memoryType, String content) {
        // 将内容文本转换为向量
        float[] vector = embeddingService.embed(content);

        // 构建元数据——包含用户 ID、记忆类型和原始内容
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", userId);
        metadata.put("memory_type", memoryType);
        metadata.put("content", content);

        // 写入向量数据库
        String id = vectorStoreService.upsert(CommonConstants.VC_MEMORY_VECTORS, vector, metadata);

        log.info("【记忆】长期记忆存储, userId={}, type={}, vectorId={}, 内容长度={}",
                userId, memoryType, id, content.length());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建 Redis 会话存储的 Key。
     * 格式：sf:session:{userId}:{sessionId}
     */
    private String buildSessionKey(Long userId, String sessionId) {
        return String.format(CommonConstants.CK_USER_SESSION, userId, sessionId);
    }
}
