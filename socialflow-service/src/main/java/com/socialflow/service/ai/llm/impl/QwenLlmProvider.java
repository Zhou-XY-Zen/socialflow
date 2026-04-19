package com.socialflow.service.ai.llm.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.exception.AiCallException;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmProviderService;
import com.socialflow.service.ai.llm.LlmResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通义千问（Qwen）LLM Provider —— 阿里云大模型，兼容 OpenAI 格式。
 * 直接用 Bearer Token 认证，无需 JWT。
 */
@Service
public class QwenLlmProvider implements LlmProviderService {

    private static final Logger log = LoggerFactory.getLogger(QwenLlmProvider.class);

    @Value("${socialflow.ai.providers.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1/}")
    private String baseUrl;

    @Value("${socialflow.ai.providers.qwen.default-model:qwen-plus}")
    private String defaultModel;

    @Value("${socialflow.ai.providers.qwen.timeout:60s}")
    private Duration timeout;

    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    private final WebClient webClient;

    public QwenLlmProvider(WebClient.Builder webClientBuilder) {
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(java.time.Duration.ofSeconds(120));
        this.webClient = webClientBuilder
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.QWEN;
    }

    @Override
    @CircuitBreaker(name = "llm-qwen", fallbackMethod = "chatFallback")
    @Retry(name = "llm-qwen")
    public LlmResponse chat(List<ChatMessage> messages, LlmConfig config) {
        String apiKey = resolveApiKey(config);
        String model = (config.getModel() != null && !config.getModel().isBlank()) ? config.getModel() : defaultModel;
        String url = resolveBaseUrl(config);

        Map<String, Object> body = buildRequest(messages, model, config, false);
        log.debug("【Qwen 同步调用】模型={}, 消息数={}", model, messages.size());

        long start = System.currentTimeMillis();
        try {
            String json = webClient.post()
                    .uri(url + "chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();

            long latency = System.currentTimeMillis() - start;
            JsonNode root = JsonUtil.mapper().readTree(json);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            int prompt = root.path("usage").path("prompt_tokens").asInt(0);
            int completion = root.path("usage").path("completion_tokens").asInt(0);

            // 按黄山版 2.3.2：每个占位符对应一个变量，避免日志级别过滤时仍计算表达式
            int totalTokens = prompt + completion;
            log.info("【Qwen 调用成功】模型={}, 耗时={}ms, prompt={}, completion={}, total={}",
                    model, latency, prompt, completion, totalTokens);
            return LlmResponse.builder()
                    .content(content)
                    .promptTokens(prompt)
                    .completionTokens(completion)
                    .totalTokens(prompt + completion)
                    .model(model)
                    .latencyMs(latency)
                    .build();

        } catch (WebClientResponseException e) {
            log.error("【Qwen 调用失败】HTTP={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new AiCallException("调用通义千问失败: " + e.getResponseBodyAsString(), e);
        } catch (AiCallException e) {
            throw e;
        } catch (Exception e) {
            throw new AiCallException("调用通义千问异常: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> chatStream(List<ChatMessage> messages, LlmConfig config) {
        String apiKey = resolveApiKey(config);
        String model = (config.getModel() != null && !config.getModel().isBlank()) ? config.getModel() : defaultModel;
        String url = resolveBaseUrl(config);

        Map<String, Object> body = buildRequest(messages, model, config, true);
        log.debug("【Qwen 流式调用】模型={}, 消息数={}", model, messages.size());

        return webClient.post()
                .uri(url + "chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(timeout)
                .filter(line -> !line.isBlank())
                .map(String::trim)
                .filter(line -> line.startsWith("data:") || line.startsWith("{"))
                .map(line -> line.startsWith("data:") ? line.substring(5).trim() : line)
                .filter(line -> !line.equals("[DONE]"))
                .map(line -> {
                    try {
                        JsonNode node = JsonUtil.mapper().readTree(line);
                        return node.path("choices").path(0).path("delta").path("content").asText("");
                    } catch (Exception e) {
                        // 黄山版 2.2.2：catch 后记日志
                        log.warn("[Qwen] 流式 chunk JSON 解析失败: {}", e.getMessage());
                        return "";
                    }
                })
                .filter(s -> !s.isEmpty())
                .onErrorMap(WebClientResponseException.class,
                        e -> new AiCallException("Qwen流式调用失败: " + e.getResponseBodyAsString(), e));
    }

    @Override
    public float[] embed(String text, String model) {
        throw new UnsupportedOperationException("Qwen embedding 由 EmbeddingService 单独处理");
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, String model) {
        throw new UnsupportedOperationException("Qwen embedding 由 EmbeddingService 单独处理");
    }

    /**
     * 熔断/重试耗尽后的兜底 —— 抛 AiCallException 让上层处理，
     * Wave 3.4 会在 LlmRouter 层做 cross-provider fallback。
     */
    public LlmResponse chatFallback(List<ChatMessage> messages, LlmConfig config, Throwable t) {
        log.error("Qwen 熔断/重试已耗尽, 触发降级: {}", t.toString());
        throw new AiCallException("Qwen 暂时不可用，请稍后重试或切换其他模型: " + t.getMessage(), t);
    }

    private String resolveApiKey(LlmConfig config) {
        String key = config.getApiKey();
        if (key == null || key.isBlank()) key = systemApiKey;
        if (key == null || key.isBlank()) throw new AiCallException("未配置通义千问 API Key");
        return key;
    }

    private String resolveBaseUrl(LlmConfig config) {
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            String u = config.getBaseUrl();
            return u.endsWith("/") ? u : u + "/";
        }
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private Map<String, Object> buildRequest(List<ChatMessage> messages, String model, LlmConfig config, boolean stream) {
        Map<String, Object> req = new HashMap<>();
        req.put("model", model);
        req.put("messages", messages.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList()));
        req.put("stream", stream);
        if (config.getTemperature() != null) req.put("temperature", config.getTemperature());
        if (config.getMaxTokens() != null) req.put("max_tokens", config.getMaxTokens());
        if (config.getTopP() != null) req.put("top_p", config.getTopP());
        return req;
    }
}
