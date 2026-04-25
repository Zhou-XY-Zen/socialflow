package com.socialflow.service.ai.rag.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.service.ai.rag.RerankerService;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
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
 * RerankerService 的 DashScope gte-rerank 实现（Wave 4.1 完成）。
 *
 * <p>原先是空桩实现（{@code return List.of()}），任何启用了 enable-rerank 的调用都会
 * 把候选清空。本实现改用阿里云 DashScope 的 gte-rerank API（兼容 OpenAI 格式），
 * 给出真正的 Cross-Encoder 精排得分。</p>
 *
 * <p>API 失败时 fallback 到"原序前 topK"，保证 RAG 链路不被 reranker 单点拖垮。</p>
 *
 * <p>受 Resilience4j {@code embedding-api} 的 Retry 实例保护（共享配置即可）。</p>
 */
@Slf4j
@Service
public class RerankerServiceImpl implements RerankerService {

    @Value("${socialflow.rag.rerank-model:gte-rerank}")
    private String model;

    @Value("${socialflow.rag.rerank-api-url:https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}")
    private String apiUrl;

    /** 复用 embedding 的 DashScope key（同账号） */
    @Value("${socialflow.embedding.api-key:${socialflow.ai.system-api-key:}}")
    private String apiKey;

    private final WebClient webClient;

    public RerankerServiceImpl(WebClient.Builder builder) {
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(30));
        this.webClient = builder
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    @Retry(name = "embedding-api")
    public List<ScoredIndex> rerank(String query, List<String> texts, int topK) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("[Reranker] no api key configured, fallback to original order");
            return identityFallback(texts.size(), topK);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        Map<String, Object> input = new HashMap<>();
        input.put("query", query);
        input.put("documents", texts);
        body.put("input", input);
        Map<String, Object> params = new HashMap<>();
        params.put("return_documents", false);
        params.put("top_n", Math.min(topK, texts.size()));
        body.put("parameters", params);

        try {
            String json = webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode results = JsonUtil.mapper().readTree(json).path("output").path("results");
            List<ScoredIndex> ranked = new ArrayList<>();
            if (results.isArray()) {
                for (JsonNode node : results) {
                    int idx = node.path("index").asInt(-1);
                    double score = node.path("relevance_score").asDouble(0.0);
                    if (idx >= 0 && idx < texts.size()) {
                        ranked.add(new ScoredIndex(idx, score));
                    }
                }
            }

            if (ranked.isEmpty()) {
                log.warn("[Reranker] empty result, fallback to original order");
                return identityFallback(texts.size(), topK);
            }
            log.debug("[Reranker] reranked {} → {}", texts.size(), ranked.size());
            return ranked;

        } catch (WebClientResponseException e) {
            log.warn("[Reranker] HTTP {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return identityFallback(texts.size(), topK);
        } catch (Exception e) {
            log.warn("[Reranker] call failed: {}", e.getMessage());
            return identityFallback(texts.size(), topK);
        }
    }

    /**
     * 失败兜底：按原始顺序返回前 topK 条，相关度填 0.0。
     * 让上游 RAG 管道不至于因 Reranker API 故障而返回空。
     */
    private List<ScoredIndex> identityFallback(int total, int topK) {
        int n = Math.min(topK, total);
        List<ScoredIndex> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new ScoredIndex(i, 0.0));
        }
        return out;
    }
}
