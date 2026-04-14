package com.socialflow.service.ai.llm.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.exception.AiCallException;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmProviderService;
import com.socialflow.service.ai.llm.LlmResponse;
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
 * 智谱AI（GLM）大语言模型提供者的具体实现类。
 *
 * 【什么是智谱AI / GLM？】
 *
 * 智谱AI（Zhipu AI）是清华大学技术团队孵化的AI公司，推出了 GLM（General Language Model）
 * 系列大语言模型。GLM-4 系列模型中文能力优秀，且提供兼容 OpenAI 格式的 API 接口，
 * 这意味着可以复用与 OpenAI 相同的请求/响应格式，降低对接成本。
 *
 * 【API 兼容性说明】
 *
 * 智谱AI的 Chat Completions 接口完全兼容 OpenAI 格式：
 *   - 请求体：{@code {"model":"glm-4-flash", "messages":[...], "temperature":0.7, "stream":false}}
 *   - 同步响应：{@code {"choices":[{"message":{"content":"..."}}], "usage":{...}}}
 *   - 流式响应：SSE 格式，每行 {@code data: {"choices":[{"delta":{"content":"..."}}]}}
 *   - 鉴权方式：{@code Authorization: Bearer {apiKey}}
 *
 * 【在系统中的位置】
 *
 * 作为 {@link LlmProviderService} 的一个实现类，通过 {@code @Service} 注解注册为
 * Spring Bean，启动时被 {@link com.socialflow.service.ai.llm.LlmRouter} 自动发现
 * 并注册到路由表中。当用户选择 GLM 作为模型提供者时，请求会被路由到本类处理。
 *
 * 【HTTP 客户端选型】
 *
 * 使用 Spring WebFlux 提供的 {@link WebClient} 作为 HTTP 客户端：
 *   - 支持同步（{@code block()}）和异步（{@code Flux}）两种调用方式
 *   - 天然支持 SSE（Server-Sent Events）流式响应解析
 *   - 无需额外引入第三方 HTTP 库
 */
@Service
public class GlmLlmProvider implements LlmProviderService {

    private static final Logger log = LoggerFactory.getLogger(GlmLlmProvider.class);

    /** 智谱AI API 基础地址，从配置文件读取 */
    @Value("${socialflow.ai.providers.glm.base-url:https://open.bigmodel.cn/api/paas/v4/}")
    private String baseUrl;

    /** 默认使用的模型名称，从配置文件读取 */
    @Value("${socialflow.ai.providers.glm.default-model:glm-4-flash}")
    private String defaultModel;

    /** API 调用超时时间，从配置文件读取 */
    @Value("${socialflow.ai.providers.glm.timeout:60s}")
    private Duration timeout;

    /** 系统级 API 密钥，作为用户未配置自有密钥时的兜底方案 */
    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    /** WebClient 实例，用于发送 HTTP 请求到智谱AI API */
    private final WebClient webClient;

    /**
     * 构造函数：初始化 WebClient。
     *
     * 使用 WebClient.Builder 创建实例，设置通用的请求头和编解码器配置。
     * 注意：baseUrl 在每次请求时动态拼接（因为 LlmConfig 可能覆盖默认地址），
     * 所以这里不在 builder 中设置 baseUrl。
     *
     * @param webClientBuilder Spring 自动注入的 WebClient 构建器
     */
    public GlmLlmProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(4 * 1024 * 1024))  // 响应体最大 4MB，防止大响应撑爆内存
                .build();
    }

    /**
     * 返回当前 Provider 对应的枚举值 GLM。
     *
     * 路由器通过此方法识别本实现，并在用户选择智谱AI时将请求转发到这里。
     */
    @Override
    public LlmProvider provider() {
        return LlmProvider.GLM;
    }

    /**
     * 同步聊天补全 —— 向智谱AI发送请求，等待完整回复后返回。
     *
     * 【实现流程】
     *   1. 解析有效的 API Key（优先使用 config 中的，其次使用系统级密钥）
     *   2. 确定实际使用的模型名称和 API 基础地址
     *   3. 构建 OpenAI 兼容格式的请求体（messages、model、temperature 等）
     *   4. 使用 WebClient 发送 POST 请求到 chat/completions 端点
     *   5. 解析响应 JSON，提取生成内容和 token 用量统计
     *   6. 记录调用耗时，封装为 LlmResponse 返回
     *
     * @param messages 对话消息列表，包含 system / user / assistant 角色消息
     * @param config   本次调用的运行时配置（模型名、温度、API Key 等）
     * @return 包含生成内容和 token 用量统计的响应对象
     * @throws AiCallException 当 API 调用失败时抛出（网络错误、鉴权失败、响应解析异常等）
     */
    @Override
    public LlmResponse chat(List<ChatMessage> messages, LlmConfig config) {
        // 获取有效的 API Key
        String apiKey = resolveApiKey(config);
        // 确定实际使用的模型名称（优先使用 config 中指定的，否则使用默认模型）
        String model = resolveModel(config);
        // 确定 API 基础地址（优先使用 config 中指定的，否则使用配置文件中的默认地址）
        String effectiveBaseUrl = resolveBaseUrl(config);

        // 构建 OpenAI 兼容格式的请求体
        Map<String, Object> requestBody = buildChatRequest(messages, model, config, false);

        log.debug("【GLM 同步调用】模型={}, 消息数={}, 温度={}", model, messages.size(),
                config.getTemperature());

        long startTime = System.currentTimeMillis();

        try {
            // 发送 POST 请求到 chat/completions 端点，等待完整响应
            String responseJson = webClient.post()
                    .uri(effectiveBaseUrl + "chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(JsonUtil.toJson(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();

            long latencyMs = System.currentTimeMillis() - startTime;

            // 解析响应 JSON
            return parseResponse(responseJson, model, latencyMs);

        } catch (WebClientResponseException e) {
            // HTTP 状态码异常（如 401 未授权、429 限流、500 服务器错误等）
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("【GLM 调用失败】HTTP状态码={}, 响应体={}, 耗时={}ms",
                    e.getStatusCode().value(), e.getResponseBodyAsString(), latencyMs);
            throw new AiCallException(
                    String.format("调用智谱AI失败，HTTP状态码: %d, 错误信息: %s",
                            e.getStatusCode().value(), e.getResponseBodyAsString()),
                    e);
        } catch (AiCallException e) {
            // 重新抛出已封装的异常，避免被下面的 catch-all 捕获
            throw e;
        } catch (Exception e) {
            // 其他异常（网络超时、JSON 解析错误等）
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("【GLM 调用异常】耗时={}ms, 异常类型={}, 信息={}",
                    latencyMs, e.getClass().getSimpleName(), e.getMessage());
            throw new AiCallException("调用智谱AI异常: " + e.getMessage(), e);
        }
    }

    /**
     * 流式聊天补全 —— 逐 token 返回生成内容（打字机效果）。
     *
     * 【实现流程】
     *   1. 构建请求体，设置 {@code "stream": true}
     *   2. 使用 WebClient 发送 POST 请求，以 SSE 方式接收响应
     *   3. 逐行解析 SSE 数据：{@code data: {"choices":[{"delta":{"content":"..."}}]}}
     *   4. 提取每行中的 delta.content，过滤掉 [DONE] 终止标记和空内容
     *   5. 以 Flux<String> 形式逐 token 推送给调用方
     *
     * 【SSE（Server-Sent Events）格式说明】
     *   - 每个事件以 "data: " 开头，后跟 JSON 数据
     *   - 最后一个事件为 "data: [DONE]"，表示生成完成
     *   - 每个 JSON 中的 choices[0].delta.content 包含一小段增量文本
     *
     * @param messages 对话消息列表
     * @param config   本次调用的运行时配置
     * @return 逐 token 推送的文本流（Flux<String>）
     */
    @Override
    public Flux<String> chatStream(List<ChatMessage> messages, LlmConfig config) {
        // 获取有效的 API Key
        String apiKey = resolveApiKey(config);
        // 确定实际使用的模型名称
        String model = resolveModel(config);
        // 确定 API 基础地址
        String effectiveBaseUrl = resolveBaseUrl(config);

        // 构建请求体，启用流式模式
        Map<String, Object> requestBody = buildChatRequest(messages, model, config, true);

        log.debug("【GLM 流式调用】模型={}, 消息数={}", model, messages.size());

        return webClient.post()
                .uri(effectiveBaseUrl + "chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(JsonUtil.toJson(requestBody))
                .retrieve()
                .bodyToFlux(String.class)            // 以文本行的方式接收 SSE 流
                .timeout(timeout)
                .filter(line -> !line.isBlank())       // 过滤空行
                .map(String::trim)
                .filter(line -> line.startsWith("data:") || !line.startsWith(":"))  // 保留 data 行，过滤 SSE 注释行
                .map(line -> {
                    // 去掉 "data: " 前缀（有些 SSE 实现会直接返回 JSON 而不带 data: 前缀）
                    if (line.startsWith("data:")) {
                        return line.substring(5).trim();
                    }
                    return line.trim();
                })
                .filter(data -> !data.equals("[DONE]") && !data.isBlank())  // 过滤终止标记和空数据
                .map(this::extractDeltaContent)       // 从 JSON 中提取 delta.content
                .filter(content -> content != null && !content.isEmpty())  // 过滤空内容片段
                .onErrorMap(WebClientResponseException.class, e -> {
                    // 将 HTTP 错误转换为 AiCallException
                    log.error("【GLM 流式调用失败】HTTP状态码={}, 响应体={}",
                            e.getStatusCode().value(), e.getResponseBodyAsString());
                    return new AiCallException(
                            String.format("智谱AI流式调用失败，HTTP状态码: %d", e.getStatusCode().value()),
                            e);
                })
                .onErrorMap(e -> !(e instanceof AiCallException), e -> {
                    // 将其他异常转换为 AiCallException
                    log.error("【GLM 流式调用异常】{}", e.getMessage());
                    return new AiCallException("智谱AI流式调用异常: " + e.getMessage(), e);
                });
    }

    /**
     * 单条文本嵌入（不支持）。
     *
     * 智谱AI的 Embedding 功能由专用的 {@link com.socialflow.service.ai.embedding.EmbeddingService}
     * 统一处理，本 Provider 仅负责聊天补全功能。
     *
     * @throws UnsupportedOperationException 始终抛出，表示本 Provider 不支持嵌入功能
     */
    @Override
    public float[] embed(String text, String model) {
        throw new UnsupportedOperationException("GLM 的嵌入功能由 EmbeddingService 统一处理，不在此 Provider 中实现");
    }

    /**
     * 批量文本嵌入（不支持）。
     *
     * 原因同上，嵌入功能由专用服务处理。
     *
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public List<float[]> embedBatch(List<String> texts, String model) {
        throw new UnsupportedOperationException("GLM 的嵌入功能由 EmbeddingService 统一处理，不在此 Provider 中实现");
    }

    // ============================== 私有辅助方法 ==============================

    /**
     * 解析有效的 API Key。
     *
     * 优先使用 LlmConfig 中调用方传入的 API Key（通常是用户自有的密钥），
     * 如果为空则回退到系统级密钥（从配置文件 socialflow.ai.system-api-key 读取）。
     *
     * @param config 运行时配置
     * @return 有效的 API Key
     * @throws AiCallException 如果两者都为空，说明未配置任何密钥
     */
    /**
     * 解析 API Key 并生成智谱 JWT Token。
     *
     * 智谱AI 的 /api/paas/v4/ 端点使用 JWT 鉴权，而非直接传 API Key。
     * API Key 格式为 "{id}.{secret}"，需要拆分后生成 JWT：
     *   - Header: {"alg":"HS256","sign_type":"SIGN"}
     *   - Payload: {"api_key":"{id}","exp":{13位毫秒时间戳},"timestamp":{13位毫秒时间戳}}
     *   - 用 secret 做 HMAC-SHA256 签名
     */
    private String resolveApiKey(LlmConfig config) {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = systemApiKey;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiCallException("未配置智谱AI的API Key，请在系统设置中配置");
        }
        // 如果 key 不含 "."，说明已经是 token 直接返回
        if (!apiKey.contains(".")) {
            return apiKey;
        }
        // 拆分 id 和 secret
        String[] parts = apiKey.split("\\.", 2);
        if (parts.length != 2) {
            throw new AiCallException("智谱AI API Key 格式错误，应为 {id}.{secret}");
        }
        return generateJwt(parts[0], parts[1]);
    }

    /**
     * 生成智谱 AI 专用的 JWT Token。
     */
    private String generateJwt(String id, String secret) {
        try {
            // Header: {"alg":"HS256","sign_type":"SIGN"}
            String header = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"HS256\",\"sign_type\":\"SIGN\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Payload: {"api_key":"{id}","exp":{毫秒},"timestamp":{毫秒}}
            long now = System.currentTimeMillis();
            long exp = now + 3600_000; // 1小时后过期
            String payloadJson = String.format(
                    "{\"api_key\":\"%s\",\"exp\":%d,\"timestamp\":%d}", id, exp, now);
            String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Signature: HMAC-SHA256(header.payload, secret)
            String content = header + "." + payload;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String signature = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sig);

            return content + "." + signature;
        } catch (Exception e) {
            throw new AiCallException("生成智谱AI JWT Token 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析实际使用的模型名称。
     *
     * 优先使用 LlmConfig 中指定的模型，如果未指定则使用配置文件中的默认模型。
     *
     * @param config 运行时配置
     * @return 模型名称
     */
    private String resolveModel(LlmConfig config) {
        if (config.getModel() != null && !config.getModel().isBlank()) {
            return config.getModel();
        }
        return defaultModel;
    }

    /**
     * 解析 API 基础地址。
     *
     * 优先使用 LlmConfig 中指定的地址（用于自部署或镜像场景），
     * 如果未指定则使用配置文件中的默认地址。
     * 确保地址以 "/" 结尾，方便后续拼接路径。
     *
     * @param config 运行时配置
     * @return 以 "/" 结尾的基础地址
     */
    private String resolveBaseUrl(LlmConfig config) {
        String url = config.getBaseUrl();
        if (url == null || url.isBlank()) {
            url = baseUrl;
        }
        // 确保 URL 以斜杠结尾，避免路径拼接时出错
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url;
    }

    /**
     * 构建 OpenAI 兼容格式的聊天请求体。
     *
     * 将系统内部的 ChatMessage 列表转换为 OpenAI 格式的 messages 数组，
     * 并添加模型名称、温度、最大 token 数等参数。
     *
     * @param messages 对话消息列表
     * @param model    模型名称
     * @param config   运行时配置（温度、maxTokens 等）
     * @param stream   是否启用流式输出
     * @return 构建好的请求体 Map，将被序列化为 JSON 发送
     */
    private Map<String, Object> buildChatRequest(List<ChatMessage> messages, String model,
                                                  LlmConfig config, boolean stream) {
        // 将 ChatMessage 列表转换为 OpenAI 格式的 messages 数组
        // 格式：[{"role": "system", "content": "..."}, {"role": "user", "content": "..."}]
        List<Map<String, String>> messageList = messages.stream()
                .map(msg -> {
                    Map<String, String> m = new HashMap<>(2);
                    m.put("role", msg.getRole());
                    m.put("content", msg.getContent());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messageList);
        body.put("stream", stream);

        // 温度参数：控制生成文本的随机性
        if (config.getTemperature() != null) {
            body.put("temperature", config.getTemperature());
        }
        // 最大生成 token 数：限制回复长度
        if (config.getMaxTokens() != null) {
            body.put("max_tokens", config.getMaxTokens());
        }
        // Top-P 核采样参数
        if (config.getTopP() != null) {
            body.put("top_p", config.getTopP());
        }

        return body;
    }

    /**
     * 解析同步调用的响应 JSON，提取生成内容和 token 用量。
     *
     * 智谱AI返回的响应格式（OpenAI 兼容）：
     * {@code {"choices":[{"message":{"content":"生成的文本"}}],
     *         "usage":{"prompt_tokens":10, "completion_tokens":50, "total_tokens":60}}}
     *
     * @param responseJson API 返回的完整 JSON 字符串
     * @param model        请求时使用的模型名称
     * @param latencyMs    调用耗时（毫秒）
     * @return 封装好的 LlmResponse 对象
     * @throws AiCallException 如果响应 JSON 格式异常或缺少必要字段
     */
    private LlmResponse parseResponse(String responseJson, String model, long latencyMs) {
        try {
            JsonNode root = JsonUtil.mapper().readTree(responseJson);

            // 提取生成的文本内容：choices[0].message.content
            JsonNode choicesNode = root.path("choices");
            if (!choicesNode.isArray() || choicesNode.isEmpty()) {
                throw new AiCallException("智谱AI响应格式异常：choices 数组为空，响应体: " + responseJson);
            }
            String content = choicesNode.get(0).path("message").path("content").asText("");

            // 提取 token 用量统计
            JsonNode usageNode = root.path("usage");
            int promptTokens = usageNode.path("prompt_tokens").asInt(0);
            int completionTokens = usageNode.path("completion_tokens").asInt(0);
            int totalTokens = usageNode.path("total_tokens").asInt(0);

            // 如果 API 返回了实际使用的模型名称，优先使用
            String actualModel = root.path("model").asText(model);

            log.debug("【GLM 调用成功】模型={}, 输入token={}, 输出token={}, 总token={}, 耗时={}ms",
                    actualModel, promptTokens, completionTokens, totalTokens, latencyMs);

            return LlmResponse.builder()
                    .content(content)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .model(actualModel)
                    .latencyMs(latencyMs)
                    .build();

        } catch (AiCallException e) {
            throw e;
        } catch (Exception e) {
            throw new AiCallException("解析智谱AI响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从流式响应的 JSON 数据中提取增量文本内容（delta.content）。
     *
     * 流式响应中每个 SSE 事件的 JSON 格式：
     * {@code {"choices":[{"delta":{"content":"一小段文本"}}]}}
     *
     * @param jsonData 去掉 "data: " 前缀后的 JSON 字符串
     * @return 增量文本内容；如果解析失败或无内容则返回 null
     */
    private String extractDeltaContent(String jsonData) {
        try {
            JsonNode root = JsonUtil.mapper().readTree(jsonData);
            JsonNode choicesNode = root.path("choices");
            if (choicesNode.isArray() && !choicesNode.isEmpty()) {
                // 提取 choices[0].delta.content
                return choicesNode.get(0).path("delta").path("content").asText(null);
            }
            return null;
        } catch (Exception e) {
            // 解析单个 SSE 事件失败时记录警告，但不中断整个流
            log.warn("【GLM 流式解析】跳过无法解析的数据片段: {}", jsonData);
            return null;
        }
    }
}
