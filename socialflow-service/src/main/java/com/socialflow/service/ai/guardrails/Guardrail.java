package com.socialflow.service.ai.guardrails;

import com.socialflow.common.enums.GuardrailRule;

/**
 * 护栏链中的单条规则接口。
 *
 * 【概述】
 *
 * 每一条护栏规则（如"输入长度检查"、"敏感词过滤"）都实现本接口。
 * 规则被注册为 Spring Bean 后，由 {@link GuardrailService} 的实现类
 * 按照 {@link #phase()} 和 {@link #order()} 自动编排执行。
 *
 * 【设计要点】
 *
 * 每条规则是独立的、可插拔的。添加新规则只需：
 *     - 创建一个类实现本接口
 *     - 用 {@code @Component} 注解注册为 Spring Bean
 *     - 指定 phase（输入端 / 输出端）和 order（执行优先级）
 * 无需修改任何已有代码，符合"开闭原则"（对扩展开放、对修改关闭）。
 */
public interface Guardrail {

    /**
     * 返回此护栏对应的规则枚举值。
     *
     * 用于日志记录和配置管理，例如 {@code INPUT_LENGTH_CHECK}、{@code SENSITIVE_WORD}。
     *
     * @return 护栏规则枚举
     */
    GuardrailRule rule();

    /**
     * 返回此护栏运行的阶段。
     *     - {@code INPUT}——在 LLM 调用之前运行（检查用户输入）
     *     - {@code OUTPUT}——在 LLM 调用之后运行（检查模型输出）
     *
     * @return 执行阶段枚举
     */
    Phase phase();

    /**
     * 返回此护栏在其阶段内的执行顺序。
     *
     * 数值越小越先执行。例如 order=10 的规则会在 order=20 之前执行。
     * 建议以 10 为间隔，方便后续插入新规则。
     *
     * @return 执行顺序数值
     */
    int order();

    /**
     * 评估当前规则——核心方法。
     *
     * 根据传入的上下文信息判断内容是否合规，返回非空的 {@link Result}。
     *
     * @param ctx 护栏评估上下文，包含待检查的文本和相关元数据
     * @return 评估结果（通过 / 警告 / 阻断）
     */
    Result evaluate(GuardrailContext ctx);

    /**
     * 护栏执行阶段枚举。
     *     - {@code INPUT}——输入端，在 LLM 调用之前执行，检查用户输入内容
     *     - {@code OUTPUT}——输出端，在 LLM 调用之后执行，检查模型生成内容
     */
    enum Phase { INPUT, OUTPUT }

    /**
     * 护栏评估结果动作枚举。
     *     - {@code PASS}——通过：内容合规，放行
     *     - {@code WARNING}——警告：内容有风险但不阻断，记录日志后继续
     *     - {@code REGENERATED}——重新生成：内容不合规，系统自动要求 LLM 重新生成
     *     - {@code BLOCKED}——阻断：内容严重违规，立即拒绝并抛出异常
     */
    enum Action { PASS, WARNING, REGENERATED, BLOCKED }

    /**
     * 护栏评估结果记录。
     *
     * @param action 评估动作（通过 / 警告 / 重新生成 / 阻断）
     * @param reason 原因说明（PASS 时为 null，其他情况下描述触发原因）
     */
    record Result(Action action, String reason) {
        /** 快捷构造：创建一个"通过"结果。 */
        public static Result pass() { return new Result(Action.PASS, null); }

        /** 快捷构造：创建一个"警告"结果，附带原因说明。 */
        public static Result warning(String reason) { return new Result(Action.WARNING, reason); }

        /** 快捷构造：创建一个"阻断"结果，附带原因说明。 */
        public static Result blocked(String reason) { return new Result(Action.BLOCKED, reason); }
    }
}
