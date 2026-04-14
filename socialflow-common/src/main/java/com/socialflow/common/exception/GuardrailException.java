package com.socialflow.common.exception;

import com.socialflow.common.result.ResultCode;
import lombok.Getter;

/**
 * 护栏（Guardrail）拦截异常
 *
 * 【作用】当AI生成的内容或用户的输入被安全护栏规则拦截时，抛出此异常。
 *   错误码固定为 2002（GUARDRAIL_BLOCKED）。
 *
 * 【什么是护栏（Guardrail）】
 *   护栏是一套内容安全检查机制，在AI生成内容的前后进行检查：
 *   - 输入护栏（INPUT）：检查用户输入是否安全（如是否包含prompt注入攻击、敏感词等）
 *   - 输出护栏（OUTPUT）：检查AI生成的内容是否合规（如是否符合平台规范、是否有幻觉等）
 *
 * 【使用场景举例】
 *   throw new GuardrailException("SENSITIVE_WORD", "INPUT", "输入中包含违禁词汇");
 *   throw new GuardrailException("PLATFORM_RULE_CHECK", "OUTPUT", "小红书文案标题超过20字限制");
 *
 * 【额外信息】相比普通异常，这个异常多了两个字段：
 *   - ruleName：哪条规则触发了拦截
 *   - triggerType：是在输入阶段还是输出阶段被拦截的
 *   这些信息可以帮助前端展示更精确的错误提示，也方便后端排查问题。
 */
@Getter // Lombok注解：自动为 ruleName 和 triggerType 生成 getter 方法
public class GuardrailException extends BaseException {

    /** 触发拦截的规则名称，对应 GuardrailRule 枚举中的值，如 "SENSITIVE_WORD"、"PROMPT_INJECTION" */
    private final String ruleName;

    /**
     * 触发阶段类型，只有两个值：
     * - "INPUT"：输入阶段被拦截（用户的输入不安全）
     * - "OUTPUT"：输出阶段被拦截（AI生成的内容不合规）
     */
    private final String triggerType;

    /**
     * 构造护栏拦截异常
     *
     * @param ruleName    触发拦截的规则名称（如 "SENSITIVE_WORD"）
     * @param triggerType 触发阶段："INPUT"（输入阶段）或 "OUTPUT"（输出阶段）
     * @param message     具体的拦截原因描述
     */
    public GuardrailException(String ruleName, String triggerType, String message) {
        super(ResultCode.GUARDRAIL_BLOCKED, message);
        this.ruleName = ruleName;
        this.triggerType = triggerType;
    }
}
