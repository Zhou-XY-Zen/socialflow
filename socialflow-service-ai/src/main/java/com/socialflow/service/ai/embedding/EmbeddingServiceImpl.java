package com.socialflow.service.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.exception.AiCallException;
import com.socialflow.common.util.JsonUtil;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding 嵌入服务实现类 —— 调用 Qwen text-embedding-v3 API 生成文本向量。
 *
 * 使用 DashScope 兼容 OpenAI 格式的 Embeddings 接口，将文本转换为 1024 维浮点向量。
 * 支持单条嵌入和批量嵌入（自动按 batch-size 分批请求）。
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    /** Embedding API 地址（DashScope 兼容 OpenAI 格式） */
    @Value("${socialflow.embedding.api-url}")
    private String apiUrl;

    /** 嵌入模型名称，如 text-embedding-v3 */
    @Value("${socialflow.embedding.model}")
    private String model;

    /** 向量维度，默认 1024 */
    @Value("${socialflow.embedding.dimension:1024}")
    private int dimension;

    /** 单次批量请求的最大文本数量，防止超过 API 限制 */
    @Value("${socialflow.embedding.batch-size:20}")
    private int batchSize;

    /** Embedding 专用 API Key（优先），回退到系统级 key */
    @Value("${socialflow.embedding.api-key:${socialflow.ai.system-api-key:}}")
    private String apiKey;

    /** HTTP 客户端，复用 Spring WebClient */
    private final WebClient webClient;

    public EmbeddingServiceImpl(WebClient.Builder webClientBuilder) {
        // 配置独立的 HttpClient，避免连接池复用过期连接导致 Connection reset
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(java.time.Duration.ofSeconds(60));
        this.webClient = webClientBuilder
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    /**
     * 返回底层嵌入模型的向量维度。
     */
    @Override
    public int dimension() {
        return dimension;
    }

    /**
     * 对单条文本生成向量嵌入。
     *
     * @param text 要嵌入的文本
     * @return 浮点数向量，长度等于 dimension
     */
    /**
     * 单条文本嵌入。
     *
     * <p>缓存策略：以 {@code SHA-256(text)} 为 key 缓存 24h（{@link com.socialflow.config.CacheConfig}）。
     * 这是最大的成本节约点 —— 同一段文案的重复嵌入将直接命中缓存，不消耗 API token。</p>
     *
     * <p>注解叠加顺序：Spring AOP 先执行 @Cacheable（命中直接返回），未命中再走 @Retry。</p>
     */
    @Override
    @Cacheable(value = "embeddingCache", key = "T(cn.hutool.crypto.SecureUtil).sha256(#text)")
    @Retry(name = "embedding-api")
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new AiCallException("嵌入文本不能为空");
        }
        // 单条文本直接调用 API，取第一个结果
        List<float[]> results = callEmbeddingApi(List.of(text));
        return results.get(0);
    }

    /**
     * 批量文本向量嵌入。
     *
     * 按 batch-size 拆分为子批次，逐批调用 API，最终拼接为完整结果列表。
     *
     * @param texts 要嵌入的文本列表
     * @return 向量列表，顺序与输入文本一一对应
     */
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> allResults = new ArrayList<>();
        // 按 batchSize 分批处理，避免单次请求文本过多导致 API 超限
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> subBatch = texts.subList(i, end);
            log.debug("【Embedding 批量】处理子批次 {}/{}, 文本数={}", (i / batchSize) + 1,
                    (int) Math.ceil((double) texts.size() / batchSize), subBatch.size());

            List<float[]> batchResults = callEmbeddingApi(subBatch);
            allResults.addAll(batchResults);
        }

        return allResults;
    }

    /**
     * 调用 DashScope Embedding API，将一批文本转换为向量。
     *
     * 请求格式（OpenAI 兼容）：
     *   POST {apiUrl}
     *   Authorization: Bearer {apiKey}
     *   {"model": "text-embedding-v3", "input": ["text1", "text2"], "dimensions": 1024}
     *
     * 响应格式：
     *   {"data": [{"embedding": [0.1, 0.2, ...], "index": 0}], "usage": {"total_tokens": 10}}
     *
     * @param texts 文本列表（大小不超过 batchSize）
     * @return 向量列表，按 index 排序后与输入文本一一对应
     */
    private List<float[]> callEmbeddingApi(List<String> texts) {
        // 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", texts);
        body.put("dimensions", dimension);

        long start = System.currentTimeMillis();
        try {
            // 发送 POST 请求
            String json = webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            long latency = System.currentTimeMillis() - start;

            // 解析响应 JSON
            JsonNode root = JsonUtil.mapper().readTree(json);
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.isEmpty()) {
                throw new AiCallException("Embedding API 返回数据为空");
            }

            // 按 index 顺序解析每个向量
            // API 返回的 data 数组中每个元素包含 index 和 embedding
            float[][] resultArray = new float[texts.size()][];
            for (JsonNode item : dataArray) {
                int index = item.path("index").asInt();
                JsonNode embeddingNode = item.path("embedding");
                float[] vector = new float[embeddingNode.size()];
                for (int j = 0; j < embeddingNode.size(); j++) {
                    vector[j] = (float) embeddingNode.get(j).asDouble();
                }
                resultArray[index] = vector;
            }

            // 记录使用量日志
            int totalTokens = root.path("usage").path("total_tokens").asInt(0);
            log.info("【Embedding 调用成功】模型={}, 文本数={}, 耗时={}ms, tokens={}",
                    model, texts.size(), latency, totalTokens);

            // 转换为 List 返回
            List<float[]> results = new ArrayList<>(texts.size());
            for (float[] v : resultArray) {
                results.add(v);
            }
            return results;

        } catch (WebClientResponseException e) {
            log.error("【Embedding 调用失败】HTTP={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new AiCallException("调用 Embedding API 失败: " + e.getResponseBodyAsString(), e);
        } catch (AiCallException e) {
            // 已经是 AiCallException，直接抛出
            throw e;
        } catch (Exception e) {
            log.error("【Embedding 调用异常】{}", e.getMessage(), e);
            throw new AiCallException("调用 Embedding API 异常: " + e.getMessage(), e);
        }
    }
}
