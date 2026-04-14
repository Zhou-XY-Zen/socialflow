package com.socialflow.service.ai.llm;

import com.socialflow.common.enums.LlmProvider;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * LLM（大语言模型）提供者统一门面接口。
 *
 * 【什么是 LLM？】
 *
 * LLM（Large Language Model，大语言模型）是一种基于深度学习的人工智能模型，
 * 能够理解和生成自然语言文本。常见的 LLM 有 DeepSeek、通义千问（Qwen）、
 * OpenAI 的 GPT 系列、Anthropic 的 Claude 等。
 *
 * 【什么是 Provider（提供者）？】
 *
 * "Provider" 是指 LLM 的供应商 / 服务方。每个供应商有自己的 API 地址、
 * 鉴权方式和模型列表。本接口通过统一的方法签名，让上层业务代码无需关心
 * 底层到底调用的是哪家厂商的模型——只需要通过 {@link LlmProvider} 枚举
 * 切换即可。
 *
 * 【在系统中的位置】
 *
 * 每个具体的 Provider（如 DeepSeekLlmProvider）都实现本接口，
 * 并在 {@link LlmRouter} 中注册。业务层通过 Router 获取对应的 Provider
 * 实例，然后调用 chat / chatStream / embed 等方法。
 *
 * 设计要点：
 *     - API 密钥在每次请求时解密，用完后从内存中清除，保证安全性。
 *     - 每次调用都会写入一条 {@code ai_usage_log} 记录，用于用量审计。
 *     - 流式输出使用 Reactor 的 {@link Flux}，逐 token 返回文本片段。
 */
public interface LlmProviderService {

    /**
     * 返回当前实现所对应的 LLM 提供者枚举值。
     *
     * 用于 {@link LlmRouter} 将请求路由到正确的实现类。
     *
     * @return 提供者枚举，如 {@code LlmProvider.DEEPSEEK}
     */
    LlmProvider provider();

    /**
     * 同步（阻塞式）聊天补全。
     *
     * 将一组对话消息发送给 LLM，等待模型生成完整回复后一次性返回。
     * 适用于对延迟不敏感、需要拿到完整结果后再处理的场景。
     *
     * @param messages 对话消息列表，包含 system / user / assistant 等角色消息
     * @param config   本次调用的运行时配置（模型名、温度、API Key 等）
     * @return 包含生成内容和 token 用量统计的响应对象
     */
    LlmResponse chat(List<ChatMessage> messages, LlmConfig config);

    /**
     * 流式聊天补全（逐 token 返回）。
     *
     * LLM 生成文本时，每产出一个 token 就立即推送给调用方，
     * 用户可以实时看到"打字机效果"，体验更好。
     * 返回值是 Reactor 的 {@link Flux}，每个元素是一小段文本增量（delta）。
     *
     * @param messages 对话消息列表
     * @param config   本次调用的运行时配置
     * @return 逐 token 推送的文本流
     */
    Flux<String> chatStream(List<ChatMessage> messages, LlmConfig config);

    /**
     * 对单条文本生成向量嵌入（Embedding）。
     *
     * 向量嵌入是将文本转换为一组浮点数（向量），使得语义相近的文本
     * 在向量空间中距离更近。这是实现语义搜索和 RAG 的基础。
     *
     * @param text  要嵌入的文本
     * @param model 嵌入模型名称
     * @return 浮点数向量
     */
    float[] embed(String text, String model);

    /**
     * 批量文本向量嵌入。
     *
     * 一次性对多条文本生成向量，比逐条调用更高效（减少网络往返次数）。
     *
     * @param texts 要嵌入的文本列表
     * @param model 嵌入模型名称
     * @return 向量列表，顺序与输入文本一一对应
     */
    List<float[]> embedBatch(List<String> texts, String model);
}
