package com.socialflow.service.ai.guardrails.impl;

import com.socialflow.common.enums.GuardrailRule;
import com.socialflow.service.ai.guardrails.Guardrail;
import com.socialflow.service.ai.guardrails.GuardrailContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 输出内容安全护栏——检查 LLM 生成的内容是否包含不安全信息。
 *
 * 【为什么需要输出端安全检查？】
 *
 * LLM 生成的内容有时会包含不应出现在公开文案中的敏感信息：
 *     - 手机号码：可能泄露用户隐私
 *     - 可疑 URL：可能包含钓鱼链接
 *     - 过多特殊字符：可能是模型幻觉或格式混乱
 * 这些信息一旦发布到社交媒体，可能造成隐私泄露或安全风险。
 *
 * 【检测策略】
 *
 * 使用正则表达式匹配常见的个人信息和不安全模式：
 *     1. 中国大陆手机号（1[3-9]开头的11位数字）
 *     2. 可疑钓鱼 URL（包含 login、verify、account 等敏感路径）
 *     3. 连续特殊字符过多（可能是乱码或格式异常）
 *
 * 【在护栏链中的位置】
 *
 * 阶段：OUTPUT（输出端）；优先级 order=10（输出端最先执行）。
 * 安全检查应优先于其他输出端规则（如平台规则检查），
 * 尽早发现并标记潜在风险。
 */
@Slf4j
@Component
public class OutputSafetyGuardrail implements Guardrail {

    /**
     * 中国大陆手机号匹配正则。
     *
     * 规则说明：
     *     - 1[3-9]：以 1 开头，第二位为 3-9（涵盖移动、联通、电信等所有号段）
     *     - \d{9}：后续 9 位数字
     *     - 使用单词边界 \b 避免误匹配更长的数字串
     */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /**
     * 可疑钓鱼 URL 匹配正则。
     *
     * 检测包含常见钓鱼路径关键词的 URL：
     *     - login / signin / verify：登录或验证页面
     *     - account / password / reset：账户或密码相关
     * 这些词出现在 URL 路径中时，可能是钓鱼链接。
     */
    private static final Pattern PHISHING_URL_PATTERN =
            Pattern.compile("https?://[^\\s]*(login|signin|verify|account|password|reset)[^\\s]*",
                    Pattern.CASE_INSENSITIVE);

    /**
     * 连续特殊字符检测正则。
     *
     * 匹配连续 10 个以上非中文、非英文、非数字、非常用标点的特殊字符。
     * 这通常意味着模型输出了乱码或格式严重混乱。
     */
    private static final Pattern EXCESSIVE_SPECIAL_CHARS_PATTERN =
            Pattern.compile("[^\\u4e00-\\u9fa5a-zA-Z0-9\\uff0c\\u3002\\uff01\\uff1f\\u3001\\uff1b\\uff1a\\u201c\\u201d\\u2018\\u2019\\uff08\\uff09\\-\\s]{10,}");

    /** 返回规则枚举：CONTENT_SAFETY */
    @Override
    public GuardrailRule rule() {
        return GuardrailRule.CONTENT_SAFETY;
    }

    /** 运行阶段：OUTPUT（输出端，LLM 生成之后） */
    @Override
    public Phase phase() {
        return Phase.OUTPUT;
    }

    /** 执行优先级：10（输出端最先执行） */
    @Override
    public int order() {
        return 10;
    }

    /**
     * 评估 LLM 生成的输出内容是否包含不安全信息。
     *
     * 检测流程（发现问题返回 WARNING 而非 BLOCKED，不阻断生成但提醒审核）：
     *   1. 检测手机号码——可能泄露隐私
     *   2. 检测可疑钓鱼 URL——可能包含恶意链接
     *   3. 检测过多特殊字符——可能是模型幻觉输出
     *
     * @param ctx 护栏评估上下文，包含 LLM 生成的输出文本
     * @return 评估结果：通过（PASS）或警告（WARNING）
     */
    @Override
    public Result evaluate(GuardrailContext ctx) {
        String text = ctx.getText();
        if (text == null || text.isEmpty()) {
            return Result.pass();
        }

        // 检测手机号码——可能泄露个人隐私信息
        if (PHONE_PATTERN.matcher(text).find()) {
            log.warn("【护栏】输出内容包含手机号码, userId={}", ctx.getUserId());
            return Result.warning("输出内容中包含疑似手机号码，请确认是否需要脱敏处理");
        }

        // 检测可疑钓鱼 URL——可能包含恶意链接
        if (PHISHING_URL_PATTERN.matcher(text).find()) {
            log.warn("【护栏】输出内容包含可疑URL, userId={}", ctx.getUserId());
            return Result.warning("输出内容中包含疑似钓鱼URL，请人工审核链接安全性");
        }

        // 检测过多特殊字符——可能是模型幻觉或乱码
        if (EXCESSIVE_SPECIAL_CHARS_PATTERN.matcher(text).find()) {
            log.warn("【护栏】输出内容包含过多特殊字符, userId={}", ctx.getUserId());
            return Result.warning("输出内容中包含过多特殊字符，可能存在格式异常");
        }

        return Result.pass();
    }
}
