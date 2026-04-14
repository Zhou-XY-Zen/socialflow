package com.socialflow.service.ai.guardrails.impl;

import com.socialflow.common.enums.GuardrailRule;
import com.socialflow.common.enums.PlatformType;
import com.socialflow.service.ai.guardrails.Guardrail;
import com.socialflow.service.ai.guardrails.GuardrailContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 平台规则检查护栏——确保生成内容符合目标社交媒体平台的字数限制。
 *
 * 【为什么需要平台规则检查？】
 *
 * 不同的社交媒体平台对内容有不同的字数限制：
 *     - 小红书（XIAOHONGSHU）：标题 ≤ 20 字，正文 ≤ 800 字
 *     - 抖音（DOUYIN）：标题 ≤ 30 字，正文 ≤ 300 字
 *     - 微信朋友圈（WECHAT_MOMENT）：无标题，正文 ≤ 200 字
 *     - 微信公众号（WECHAT_MP）：标题 ≤ 30 字，正文 ≤ 3000 字
 *
 * 如果生成的文案超出平台限制，发布时会被截断或失败。
 * 本护栏在生成后立即检查，提前发现并提醒用户。
 *
 * 【内容解析策略】
 *
 * 生成的文案通常包含标题和正文两部分，以第一个换行符分隔：
 *     - 第一行视为标题
 *     - 其余内容视为正文
 * 如果文案没有换行符，则整体视为正文（无标题）。
 *
 * 【在护栏链中的位置】
 *
 * 阶段：OUTPUT（输出端）；优先级 order=20（在内容安全检查 order=10 之后执行）。
 * 先确保内容安全，再检查是否符合平台规则。
 */
@Slf4j
@Component
public class PlatformRuleGuardrail implements Guardrail {

    /** 返回规则枚举：PLATFORM_RULE_CHECK */
    @Override
    public GuardrailRule rule() {
        return GuardrailRule.PLATFORM_RULE_CHECK;
    }

    /** 运行阶段：OUTPUT（输出端，LLM 生成之后） */
    @Override
    public Phase phase() {
        return Phase.OUTPUT;
    }

    /** 执行优先级：20（在内容安全检查 order=10 之后） */
    @Override
    public int order() {
        return 20;
    }

    /**
     * 评估生成的内容是否符合目标平台的字数限制。
     *
     * 检测流程：
     *   1. 从上下文中获取目标平台编码
     *   2. 通过 PlatformType.of() 解析平台的字数限制
     *   3. 将文案拆分为标题和正文
     *   4. 分别检查标题和正文是否超出限制
     *   5. 超限则返回 WARNING（不阻断，提醒用户裁剪）
     *
     * 注意：如果上下文中没有指定平台，则跳过检查直接放行。
     *
     * @param ctx 护栏评估上下文，包含生成的文本和目标平台
     * @return 评估结果：通过（PASS）或警告（WARNING）
     */
    @Override
    public Result evaluate(GuardrailContext ctx) {
        String text = ctx.getText();
        String platformCode = ctx.getPlatform();

        // 如果没有指定平台或文本为空，跳过检查
        if (text == null || text.isEmpty() || platformCode == null || platformCode.isEmpty()) {
            return Result.pass();
        }

        // 解析平台枚举，获取字数限制
        PlatformType platform;
        try {
            platform = PlatformType.of(platformCode);
        } catch (IllegalArgumentException e) {
            // 未知平台，跳过检查
            log.debug("【护栏】未知平台 '{}'，跳过平台规则检查", platformCode);
            return Result.pass();
        }

        // 拆分标题和正文——以第一个换行符为分隔
        String title = "";
        String body = text;
        int newlineIndex = text.indexOf('\n');
        if (newlineIndex > 0) {
            title = text.substring(0, newlineIndex).trim();
            body = text.substring(newlineIndex + 1).trim();
        }

        // 检查标题长度（仅在平台有标题限制时检查）
        if (platform.getTitleMaxLength() > 0 && title.length() > platform.getTitleMaxLength()) {
            String msg = String.format("超出%s标题字数限制（当前%d字，限制%d字）",
                    platform.getDisplayName(), title.length(), platform.getTitleMaxLength());
            log.warn("【护栏】{}, userId={}", msg, ctx.getUserId());
            return Result.warning(msg);
        }

        // 检查正文长度
        if (body.length() > platform.getBodyMaxLength()) {
            String msg = String.format("超出%s正文字数限制（当前%d字，限制%d字）",
                    platform.getDisplayName(), body.length(), platform.getBodyMaxLength());
            log.warn("【护栏】{}, userId={}", msg, ctx.getUserId());
            return Result.warning(msg);
        }

        return Result.pass();
    }
}
