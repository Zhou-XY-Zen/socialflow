package com.socialflow.service.ai.guardrails;

import lombok.Builder;
import lombok.Data;

/**
 * 护栏评估上下文——承载护栏规则做出判断所需的全部信息。
 *
 * 【什么是上下文传递（Context Passing）？】
 *
 * 在责任链模式中，每条规则需要访问一些共享数据来做出判断。
 * 与其让每条规则自己去获取数据，不如将所有需要的数据打包到一个"上下文对象"中，
 * 沿着责任链统一传递。这样做的好处是：
 *     - 规则之间解耦：每条规则只需从上下文中取自己需要的字段
 *     - 灵活扩展：新增字段不影响已有规则
 *     - 统一管理：数据来源清晰，便于调试
 *
 * 【在系统中的位置】
 *
 * {@link GuardrailService} 的实现类在执行护栏检查前构建此对象，
 * 然后将其传递给每条 {@link Guardrail#evaluate(GuardrailContext)} 方法。
 * 输入端检查时只填充 userId 和 text；输出端检查时额外填充 platform 和 sourceContext。
 */
@Data
@Builder
public class GuardrailContext {

    /** 当前用户 ID，用于审计日志记录。 */
    private Long userId;

    /** 待检查的文本内容。输入端检查时为用户输入；输出端检查时为 LLM 生成的输出。 */
    private String text;

    /**
     * 目标平台编码（可选）。
     *
     * 用于平台特定的合规检查，例如不同社交平台对内容有不同的字数限制和审核标准。
     * 仅在输出端检查时填充。
     */
    private String platform;

    /**
     * RAG 检索的参考资料原文（可选）。
     *
     * 用于幻觉检测护栏：对比 LLM 生成的内容是否与参考资料一致，
     * 判断模型是否"编造"了超出参考范围的信息。
     * 仅在输出端检查且使用了 RAG 时填充。
     */
    private String sourceContext;
}
