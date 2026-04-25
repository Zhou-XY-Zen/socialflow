package com.socialflow.service.ai.llm;

import lombok.Builder;
import lombok.Data;

/**
 * 单次 LLM 调用的运行时配置。
 *
 * 【作用说明】
 *
 * 每次调用大语言模型（LLM）时，都需要指定一系列参数，比如使用哪个模型、
 * 创造性有多高、最多生成多少 token 等。本类就是这些参数的载体，
 * 通过 Builder 模式构建后传给 {@link LlmProviderService} 的 chat/chatStream 方法。
 *
 * 【在系统中的位置】
 *
 * 业务层根据用户的配置（数据库中存储的 API Key、模型偏好等）构建此对象，
 * 然后通过 {@link LlmRouter} 获取对应 Provider 并发起调用。
 */
@Data
@Builder
public class LlmConfig {

    /**
     * 模型 ID，即要调用的具体模型名称。
     *
     * 示例：{@code deepseek-chat}、{@code qwen-max}、{@code gpt-4o-mini}。
     * 不同的 Provider 支持不同的模型列表。
     */
    private String model;

    /**
     * 温度参数（temperature），控制生成文本的随机性 / 创造性。
     *
     * 取值范围通常为 0.0 ~ 2.0：
     *     - 值越低（如 0.1）：输出越确定、越保守，适合事实性问答
     *     - 值越高（如 1.5）：输出越随机、越有创意，适合创意写作
     */
    private Double temperature;

    /**
     * 最大生成 token 数。
     *
     * Token 是 LLM 处理文本的最小单位（大约相当于一个汉字或半个英文单词）。
     * 此参数限制模型单次回复的最大长度，防止生成过长内容和超额计费。
     */
    private Integer maxTokens;

    /**
     * Top-P（核采样）参数，与 temperature 配合控制输出多样性。
     *
     * 模型在生成每个 token 时，只从累计概率达到 topP 的候选 token 中采样。
     * 例如 topP = 0.9 表示只从概率最高的、累计占 90% 概率的候选词中选取。
     */
    private Double topP;

    /**
     * 已解密的 API 密钥。
     *
     * 用于向 LLM 提供者的 API 进行身份验证。
     * 安全注意事项：此字段绝不能被日志记录或序列化到前端。
     */
    private String apiKey;

    /**
     * API 基础地址覆盖。
     *
     * 用于自部署模型服务或国内镜像地址的场景。
     * 例如 DeepSeek 的国内地址为 {@code https://api.deepseek.com/v1}。
     * 如果为空，则使用 Provider 的默认地址。
     */
    private String baseUrl;

    /**
     * 发起调用的用户 ID。
     *
     * 用于用量审计、速率限制和日志追踪，确保每次 API 调用都能追溯到具体用户。
     */
    private Long userId;
}
