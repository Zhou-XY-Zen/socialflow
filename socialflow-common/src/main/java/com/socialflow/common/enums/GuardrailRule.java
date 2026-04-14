package com.socialflow.common.enums;

/**
 * 内置护栏（Guardrail）规则标识枚举
 *
 * 【作用】定义系统中所有内置的安全护栏规则。
 *   护栏是AI生成流水线中的"安全检查站"，分为输入护栏和输出护栏：
 *   - 输入护栏：在调用AI之前检查用户的输入是否安全
 *   - 输出护栏：在AI生成内容之后检查输出是否合规
 *
 * 【检查流程】
 *   用户输入 → [输入护栏检查] → AI模型生成 → [输出护栏检查] → 返回给用户
 *
 * 【使用场景】
 *   当某条规则检查不通过时，会抛出 GuardrailException 并附带规则名称，
 *   前端可以根据规则名称给出具有针对性的提示信息。
 */
public enum GuardrailRule {

    /** 输入长度检查——检查用户输入的文本长度是否在允许范围内（防止超长输入浪费Token） */
    INPUT_LENGTH_CHECK,

    /** 敏感词检测——检查输入/输出中是否包含违禁词汇、敏感词 */
    SENSITIVE_WORD,

    /** Prompt注入检测——检查用户输入中是否包含企图绕过AI限制的恶意指令 */
    PROMPT_INJECTION,

    /** 主题边界检查——检查用户请求是否偏离了系统允许的主题范围（如不允许生成违法内容） */
    TOPIC_BOUNDARY,

    /** 输出格式检查——检查AI生成的内容格式是否符合要求（如JSON格式是否完整） */
    OUTPUT_FORMAT_CHECK,

    /** 平台规则检查——检查生成的内容是否符合目标平台的规则（如标题长度、字数限制等） */
    PLATFORM_RULE_CHECK,

    /** 内容安全检查——检查生成的内容是否包含不良信息（如暴力、色情等） */
    CONTENT_SAFETY,

    /** 幻觉检测——检查AI生成的内容是否包含编造的虚假信息（hallucination） */
    HALLUCINATION,

    /** 品牌语气检查——检查生成的内容是否符合品牌的语气和风格要求 */
    BRAND_TONE
}
