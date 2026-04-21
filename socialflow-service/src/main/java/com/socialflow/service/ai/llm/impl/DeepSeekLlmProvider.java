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

    /** 流式调用的 idle timeout —— "两次 chunk 间隔"超过此值才认为卡死。
     *  DeepSeek 正常吐 token 每秒几十个，这里给 120s 覆盖偶发 warmup / 服务端排队。 */
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofSeconds(120);

    public DeepSeekLlmProvider(WebClient.Builder webClientBuilder) {
        // responseTimeout 是 Netty 的"接收完整响应总时长"硬天花板。流式模式下保留 30 分钟兜底，
        // 实际超时主要由 Flux.timeout(idle) 控制 —— 服务端持续吐 token 就永不超时。
        // DeepSeek 官方 TCP 维持 30 分钟，对齐该值。
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofMinutes(30));
        this.webClient = webClientBuilder
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                // 代码分析 FINAL 阶段响应可能含数 MB 的 summaryMd + Mermaid，4MB 太紧 → 16MB
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
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

        // 内部改走 SSE 流式聚合 —— 对外接口仍返回完整 LlmResponse。
        // 这样做的关键收益：DeepSeek 生成 5000+ 字长回答时，单次推理总时长偶尔 > 300s 会撞 Netty
        // responseTimeout 硬天花板触发 Resilience4j 重试 3 次（15 分钟惩罚）。改用 idle timeout 后，
        // 只要服务端持续吐 token 就永不超时；整个 30 分钟连接上限内都能跑完。
        Map<String, Object> body = buildRequest(messages, model, config, true);
        body.put("stream_options", Map.of("include_usage", true));  // 最后一个 chunk 带 usage
        log.debug("DeepSeek chat (stream): model={}, messages={}", model, messages.size());

        StringBuilder contentBuf = new StringBuilder(16384);
        int[] usage = {0, 0};  // [prompt_tokens, completion_tokens]
        long start = System.currentTimeMillis();
        try {
            webClient.post()
                    .uri(url + "chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    // idle timeout：相邻 chunk 间隔 > 120s 才视为卡死；正常吐 token 场景永不触发
                    .timeout(STREAM_IDLE_TIMEOUT)
                    .doOnNext(raw -> accumulateStreamChunk(raw, contentBuf, usage))
                    .blockLast();

            long latency = System.currentTimeMillis() - start;
            String content = stripThinkTags(contentBuf.toString());
            int prompt = usage[0], completion = usage[1];
            int totalTokens = prompt + completion;

            log.info("DeepSeek OK: model={}, latency={}ms, prompt={}, completion={}, total={}",
                    model, latency, prompt, completion, totalTokens);
            return LlmResponse.builder()
                    .content(content)
                    .promptTokens(prompt)
                    .completionTokens(completion)
                    .totalTokens(totalTokens)
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

    /**
     * 解析一条 SSE data 行，累积到 contentBuf 和 usage 数组。
     *
     * DeepSeek SSE 流格式（stream_options.include_usage=true）：
     *   data: {"choices":[{"delta":{"content":"你"}}]}
     *   data: {"choices":[{"delta":{"content":"好"}}]}
     *   ...
     *   data: {"choices":[],"usage":{"prompt_tokens":N,"completion_tokens":M,"total_tokens":K}}
     *   data: [DONE]
     *
     * 这里解析 content delta + 流末尾的 usage。reasoner 的 reasoning_content chunk 跳过。
     */
    private void accumulateStreamChunk(String raw, StringBuilder contentBuf, int[] usage) {
        if (raw == null) return;
        String line = raw.trim();
        if (line.isEmpty()) return;
        // bodyToFlux(String) 通常已经去掉 "data: " 前缀，但有时会保留（不同 Netty 版本），做一次兼容处理
        if (line.startsWith("data:")) line = line.substring(5).trim();
        if (line.isEmpty() || "[DONE]".equals(line) || line.startsWith(":")) return;
        try {
            JsonNode node = JsonUtil.mapper().readTree(line);
            JsonNode choices = node.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.path(0).path("delta");
                // deepseek-reasoner：跳过纯 reasoning_content chunk
                String content = delta.path("content").asText("");
                if (!content.isEmpty()) contentBuf.append(content);
            }
            JsonNode u = node.path("usage");
            if (u.isObject() && !u.isMissingNode()) {
                usage[0] = u.path("prompt_tokens").asInt(usage[0]);
                usage[1] = u.path("completion_tokens").asInt(usage[1]);
            }
        } catch (Exception e) {
            // 单条 chunk 解析失败不中断整条流 —— 记日志继续
            log.warn("[DeepSeek] SSE chunk 解析失败: {} (chunk preview: {})",
                    e.getMessage(), line.length() > 120 ? line.substring(0, 120) + "..." : line);
        }
    }

    /** 过滤 <think>...</think> 标签（reasoner 模型混入时） */
    private static String stripThinkTags(String s) {
        if (s == null || !s.contains("<think>")) return s == null ? "" : s;
        return s.replaceAll("<think>[\\s\\S]*?</think>", "").trim();
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
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                // idle timeout，与 chat() 统一：相邻 token 间隔 > 120s 才视为卡死
                .timeout(STREAM_IDLE_TIMEOUT)
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
