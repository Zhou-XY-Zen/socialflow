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
 * DeepSeek LLM Provider —— 兼容 OpenAI 格式的 API。
 * 支持 deepseek-chat 和 deepseek-reasoner 模型。
 */
@Service
public class DeepSeekLlmProvider implements LlmProviderService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmProvider.class);

    @Value("${socialflow.ai.providers.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${socialflow.ai.providers.deepseek.default-model:deepseek-reasoner}")
    private String defaultModel;

    @Value("${socialflow.ai.providers.deepseek.timeout:180s}")
    private Duration timeout;

    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    private final WebClient webClient;

    public DeepSeekLlmProvider(WebClient.Builder webClientBuilder) {
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(java.time.Duration.ofSeconds(300));
        this.webClient = webClientBuilder
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.DEEPSEEK;
    }

    @Override
    @CircuitBreaker(name = "llm-deepseek", fallbackMethod = "chatFallback")
    @Retry(name = "llm-deepseek")
    public LlmResponse chat(List<ChatMessage> messages, LlmConfig config) {
        String apiKey = resolveApiKey(config);
        String model = (config.getModel() != null && !config.getModel().isBlank()) ? config.getModel() : defaultModel;
        String url = resolveBaseUrl(config);

        Map<String, Object> body = buildRequest(messages, model, config, false);
        log.debug("DeepSeek chat: model={}, messages={}", model, messages.size());

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

            // deepseek-reasoner：只取 content（最终回答），不取 reasoning_content（推理过程）
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            // 过滤掉可能混入的 <think> 标签
            if (content.contains("<think>")) {
                content = content.replaceAll("<think>[\\s\\S]*?</think>", "").trim();
            }
            int prompt = root.path("usage").path("prompt_tokens").asInt(0);
            int completion = root.path("usage").path("completion_tokens").asInt(0);

            // 按黄山版 2.3.2：每个占位符对应一个变量
            int totalTokens = prompt + completion;
            log.info("DeepSeek OK: model={}, latency={}ms, prompt={}, completion={}, total={}",
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
            log.error("DeepSeek HTTP error: status={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new AiCallException("DeepSeek API error: " + e.getResponseBodyAsString(), e);
        } catch (AiCallException e) {
            throw e;
        } catch (Exception e) {
            throw new AiCallException("DeepSeek call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> chatStream(List<ChatMessage> messages, LlmConfig config) {
        String apiKey = resolveApiKey(config);
        String model = (config.getModel() != null && !config.getModel().isBlank()) ? config.getModel() : defaultModel;
        String url = resolveBaseUrl(config);

        Map<String, Object> body = buildRequest(messages, model, config, true);
        log.debug("DeepSeek stream: model={}, messages={}", model, messages.size());

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
                        JsonNode delta = node.path("choices").path(0).path("delta");
                        // deepseek-reasoner：只取 content（最终回答），跳过 reasoning_content（推理过程）
                        if (delta.has("reasoning_content") && !delta.has("content")) {
                            return ""; // 纯推理 chunk，跳过
                        }
                        String content = delta.path("content").asText("");
                        // 过滤掉可能的 <think> 标签包裹的推理内容
                        if (content.contains("<think>") || content.contains("</think>")) {
                            content = content.replaceAll("<think>[\\s\\S]*?</think>", "")
                                             .replace("<think>", "").replace("</think>", "");
                        }
                        return content;
                    } catch (Exception e) {
                        // 黄山版 2.2.2：catch 后记日志
                        log.warn("[DeepSeek] 流式 chunk JSON 解析失败: {}", e.getMessage());
                        return "";
                    }
                })
                .filter(s -> !s.isEmpty())
                .onErrorMap(WebClientResponseException.class,
                        e -> new AiCallException("DeepSeek stream error: " + e.getResponseBodyAsString(), e));
    }

    /**
     * 熔断/重试耗尽后的兜底 —— 直接抛 AiCallException 让上层（GlobalExceptionHandler）
     * 转成统一的 5xx 错误响应。后续 Wave 3.4 会改造 LlmRouter 自动 fallback 到 next provider。
     */
    public LlmResponse chatFallback(List<ChatMessage> messages, LlmConfig config, Throwable t) {
        log.error("DeepSeek 熔断/重试已耗尽, 触发降级: {}", t.toString());
        throw new AiCallException("DeepSeek 暂时不可用，请稍后重试或切换其他模型: " + t.getMessage(), t);
    }

    @Override
    public float[] embed(String text, String model) {
        throw new UnsupportedOperationException("DeepSeek embedding not supported, use EmbeddingService");
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, String model) {
        throw new UnsupportedOperationException("DeepSeek embedding not supported, use EmbeddingService");
    }

    private String resolveApiKey(LlmConfig config) {
        String key = config.getApiKey();
        if (key == null || key.isBlank()) key = systemApiKey;
        if (key == null || key.isBlank()) throw new AiCallException("DeepSeek API Key not configured");
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
        // deepseek-reasoner 不支持 temperature/top_p 参数
        if (!"deepseek-reasoner".equals(model)) {
            if (config.getTemperature() != null) req.put("temperature", config.getTemperature());
            if (config.getMaxTokens() != null) req.put("max_tokens", config.getMaxTokens());
            if (config.getTopP() != null) req.put("top_p", config.getTopP());
        }
        return req;
    }
}
