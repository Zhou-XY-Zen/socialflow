package com.socialflow.service.ai.guardrails;

/**
 * AI 内容安全护栏（Guardrails）门面服务接口。
 *
 * 【什么是 Guardrails（护栏 / 安全防护）？】
 *
 * Guardrails 是 AI 系统中的"安全检查站"。在用户输入发送给 LLM 之前
 * 和 LLM 生成内容返回给用户之前，系统会自动运行一系列安全检查规则，
 * 拦截不安全、不合规或不合适的内容。常见的护栏规则包括：
 *     - 输入长度限制：防止超长输入导致高额 API 费用
 *     - 敏感词过滤：拦截包含违禁词汇的输入
 *     - 幻觉检测：检查 LLM 输出是否与参考资料一致，防止"编造"
 *     - 平台合规检查：确保生成内容符合目标平台的规范
 *
 * 【工作模式】
 *
 * 本服务采用责任链模式（Chain of Responsibility）：
 * 所有注册的 {@link Guardrail} 规则按优先级依次执行。
 * 一旦某条规则返回 {@link Guardrail.Action#BLOCKED}（阻断），
 * 立即抛出 {@link com.socialflow.common.exception.GuardrailException} 异常
 * 并写入审计日志 {@code guardrail_log}，后续规则不再执行。
 *
 * 【在系统中的位置】
 *
 * 护栏检查嵌入在 AI 调用流程的两端：
 *     - 输入端：业务层在调用 LLM 之前调用 {@link #checkInput}
 *     - 输出端：收到 LLM 回复后、返回用户之前调用 {@link #checkOutput}
 */
public interface GuardrailService {

    /**
     * 对用户的原始输入执行所有已启用的输入端护栏检查。
     *
     * 在发送给 LLM 之前调用。如果输入不合规，会抛出 GuardrailException。
     *
     * @param userId 用户 ID，用于审计日志
     * @param text   用户的原始输入文本
     * @throws com.socialflow.common.exception.GuardrailException 当某条规则判定为 BLOCKED 时
     */
    void checkInput(Long userId, String text);

    /**
     * 对 LLM 生成的输出执行所有已启用的输出端护栏检查。
     *
     * 在将生成内容返回给用户之前调用。如果输出不合规，会抛出 GuardrailException。
     *
     * @param userId        用户 ID，用于审计日志
     * @param text          LLM 生成的输出文本
     * @param platform      目标平台编码（用于平台特定的合规检查）
     * @param sourceContext RAG 检索上下文（传递给幻觉检测护栏，用于判断输出是否偏离了参考资料）；
     *                      如果未使用 RAG 则传 null
     */
    void checkOutput(Long userId, String text, String platform, String sourceContext);
}
