package com.socialflow.service.ai.guardrails.impl;

import com.socialflow.common.enums.GuardrailRule;
import com.socialflow.common.util.TokenCountUtil;
import com.socialflow.service.ai.guardrails.Guardrail;
import com.socialflow.service.ai.guardrails.GuardrailContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 输入长度检查护栏——拒绝超过 Token 上限的用户输入。
 *
 * 【为什么需要限制输入长度？】
 *
 * LLM 的 API 按 Token 数量计费，过长的输入不仅费用高，还可能导致：
 *     - 超出模型的上下文窗口限制，导致 API 报错
 *     - 响应时间过长，影响用户体验
 *     - 被恶意用户利用来刷取高额费用
 * 因此需要在输入端设置一个 Token 数量上限（默认 2000），超过则直接拒绝。
 *
 * 【执行特性】
 *
 * 这是一个纯规则检查（Pure Rule），只做简单的数值比较，
 * 执行时间在亚毫秒级别，不会对系统性能产生任何影响。
 *
 * 【在护栏链中的位置】
 *
 * 阶段：INPUT（输入端）；优先级 order=10（最先执行）。
 * 长度检查应当最先执行，如果输入过长，后续的敏感词检查等也没有必要运行。
 */
@Component
public class InputLengthGuardrail implements Guardrail {

    /**
     * 允许的最大输入 Token 数量，超过此值将被阻断。
     *
     * 可通过配置项 {@code socialflow.ai.guardrails.max-input-tokens} 自定义，默认 2000。
     */
    @Value("${socialflow.ai.guardrails.max-input-tokens:2000}")
    private int maxTokens;

    /** 返回规则枚举：INPUT_LENGTH_CHECK（输入长度检查） */
    @Override
    public GuardrailRule rule() { return GuardrailRule.INPUT_LENGTH_CHECK; }

    /** 运行阶段：INPUT（输入端，LLM 调用之前） */
    @Override
    public Phase phase() { return Phase.INPUT; }

    /** 执行优先级：10（在所有输入端规则中最先执行） */
    @Override
    public int order() { return 10; }

    /**
     * 评估用户输入是否超过 Token 上限。
     *
     * 使用 {@link TokenCountUtil#estimate} 快速估算文本的 Token 数量
     * （精确计算 Token 需要加载分词器，这里用估算方法以保证速度）。
     * 如果超过上限则返回 BLOCKED，否则返回 PASS。
     */
    @Override
    public Result evaluate(GuardrailContext ctx) {
        // 估算输入文本的 Token 数量
        int tokens = TokenCountUtil.estimate(ctx.getText());
        // 如果超过配置的上限，返回阻断结果
        if (tokens > maxTokens) {
            return Result.blocked("input exceeds max tokens: " + tokens + " > " + maxTokens);
        }
        // 未超限，放行
        return Result.pass();
    }
}
