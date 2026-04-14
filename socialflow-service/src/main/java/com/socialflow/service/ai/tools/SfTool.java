package com.socialflow.service.ai.tools;

import java.util.Map;

/**
 * AI 工具（Tool / Function Calling）基础接口——每个可被 Agent 调用的工具都实现此接口。
 *
 * 【什么是 Function Calling / Tool Use（函数调用 / 工具使用）？】
 *
 * LLM 本身只能生成文本，无法直接执行搜索、查数据库、调用 API 等操作。
 * Function Calling（函数调用）是一种让 LLM "使用外部工具"的机制：
 *     - 开发者预先定义一组可用的"工具"（函数），包括名称、描述和参数格式
 *     - 将这些工具的信息告诉 LLM
 *     - LLM 在需要时，不直接输出文本，而是输出"我要调用某某工具，参数是..."
 *     - 系统收到调用请求后，执行对应工具，将结果返回给 LLM
 *     - LLM 基于工具返回的结果，生成最终回复
 * 这让 LLM 从"只会说话"变成了"能动手做事"的智能体。
 *
 * 【工具接口的四要素】
 *     - name：工具的机器名称，LLM 在调用时引用此名称
 *     - description：工具的自然语言描述，帮助 LLM 判断何时该使用此工具
 *     - parametersSchema：参数的 JSON Schema 定义，告诉 LLM 传什么参数
 *     - invoke：实际执行逻辑
 *
 * 【在系统中的位置】
 *
 * 具体工具（如知识库搜索工具、热点查询工具等）实现此接口并注册为 Spring Bean，
 * 由 {@link ToolRegistry} 统一管理。Agent 在与 LLM 交互时，将所有可用工具的
 * 名称、描述和参数模式传递给 LLM，LLM 决定是否调用以及调用哪个。
 */
public interface SfTool {

    /**
     * 工具的机器名称（唯一标识）。
     *
     * 例如 {@code knowledge_search}、{@code hot_topic_query} 等。
     * LLM 在发起 Function Calling 时通过此名称指定要调用的工具。
     *
     * @return 工具名称字符串
     */
    String name();

    /**
     * 工具的自然语言描述。
     *
     * 这段描述会展示给 Agent / LLM，帮助其理解"这个工具能做什么"。
     * 描述应简洁明了，例如"在指定知识库中搜索相关内容片段"。
     *
     * @return 工具描述文本
     */
    String description();

    /**
     * 执行工具——核心方法。
     *
     * 接收 LLM 传来的参数，执行实际操作（如查数据库、调用外部 API 等），
     * 返回任意可序列化为 JSON 的对象。编排器（Orchestrator）会将返回值
     * 序列化后回传给 LLM，作为后续生成的上下文。
     *
     * @param args LLM 传来的参数映射表，key-value 对应 {@link #parametersSchema()} 中定义的字段
     * @return 工具执行结果（任意 JSON 可序列化对象）
     */
    Object invoke(Map<String, Object> args);

    /**
     * 工具参数的 JSON Schema 定义。
     *
     * 符合 JSON Schema 标准的参数描述，用于生成 OpenAI / 通义千问 / DeepSeek
     * 等模型 API 要求的 Function Calling 参数模式。
     * LLM 根据此 Schema 知道需要传哪些参数、参数类型是什么、哪些是必填的。
     *
     * @return JSON Schema 映射表
     */
    Map<String, Object> parametersSchema();
}
