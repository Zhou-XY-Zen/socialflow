package com.socialflow.service.ai.guardrails.impl;

import com.socialflow.common.exception.GuardrailException;
import com.socialflow.dao.mapper.GuardrailLogMapper;
import com.socialflow.model.entity.GuardrailLog;
import com.socialflow.service.ai.guardrails.Guardrail;
import com.socialflow.service.ai.guardrails.GuardrailContext;
import com.socialflow.service.ai.guardrails.GuardrailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 护栏服务的默认实现——基于责任链模式（Chain of Responsibility）。
 *
 * 【什么是责任链模式？】
 *
 * 责任链模式是一种行为设计模式：将多个处理者（本系统中的护栏规则）组成一条链，
 * 请求沿着链依次传递，每个处理者决定是否处理该请求以及是否继续传递。
 * 在本系统中，每条护栏规则就是链上的一个节点：
 *     - 如果判定为 PASS（通过），继续传递给下一条规则
 *     - 如果判定为 WARNING（警告），记录日志后继续传递
 *     - 如果判定为 BLOCKED（阻断），记录日志并抛出异常，链终止
 *
 * 【自动注册机制】
 *
 * 所有实现 {@link Guardrail} 接口的类都通过 {@code @Component} 注册为 Spring Bean，
 * Spring 自动注入到构造函数的 {@code guardrails} 列表中。
 * 执行时按 {@link Guardrail#phase()} 分区、按 {@link Guardrail#order()} 排序。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardrailServiceImpl implements GuardrailService {

    /** 所有注册的护栏规则 Bean 列表（由 Spring 自动注入） */
    private final List<Guardrail> guardrails;

    /** 护栏日志 Mapper，用于将触发记录持久化到 guardrail_log 表 */
    private final GuardrailLogMapper guardrailLogMapper;

    /** 护栏总开关：可通过配置动态关闭所有护栏检查（默认启用） */
    @Value("${socialflow.ai.guardrails.enabled:true}")
    private boolean enabled;

    /**
     * 执行输入端护栏检查。
     *
     * 构建只包含 userId 和 text 的上下文，然后运行所有 INPUT 阶段的规则。
     */
    @Override
    public void checkInput(Long userId, String text) {
        // 如果护栏总开关关闭，直接跳过所有检查
        if (!enabled) return;
        // 构建输入端的上下文对象（输入端不需要 platform 和 sourceContext）
        GuardrailContext ctx = GuardrailContext.builder()
                .userId(userId).text(text).build();
        // 运行 INPUT 阶段的所有护栏规则
        runPhase(Guardrail.Phase.INPUT, ctx);
    }

    /**
     * 执行输出端护栏检查。
     *
     * 构建包含完整信息的上下文（含平台和 RAG 参考资料），然后运行所有 OUTPUT 阶段的规则。
     */
    @Override
    public void checkOutput(Long userId, String text, String platform, String sourceContext) {
        // 如果护栏总开关关闭，直接跳过所有检查
        if (!enabled) return;
        // 构建输出端的完整上下文对象
        GuardrailContext ctx = GuardrailContext.builder()
                .userId(userId).text(text)
                .platform(platform).sourceContext(sourceContext).build();
        // 运行 OUTPUT 阶段的所有护栏规则
        runPhase(Guardrail.Phase.OUTPUT, ctx);
    }

    /**
     * 运行指定阶段的所有护栏规则——责任链的核心逻辑。
     *
     * 执行流程：
     *     - 从所有注册的护栏中筛选出属于当前阶段（INPUT 或 OUTPUT）的规则
     *     - 按 order 值升序排序（数值小的先执行）
     *     - 依次执行每条规则的 evaluate 方法
     *     - 根据返回结果决定：PASS 则继续、WARNING 则记录后继续、BLOCKED 则终止
     *
     * @param phase 当前执行阶段（INPUT 或 OUTPUT）
     * @param ctx   护栏评估上下文
     */
    private void runPhase(Guardrail.Phase phase, GuardrailContext ctx) {
        guardrails.stream()
                // 第一步：只保留属于当前阶段的规则
                .filter(g -> g.phase() == phase)
                // 第二步：按 order 升序排序，确保优先级高的规则先执行
                .sorted(Comparator.comparingInt(Guardrail::order))
                // 第三步：依次执行每条规则
                .forEach(g -> {
                    // 调用规则的评估方法
                    Guardrail.Result result = g.evaluate(ctx);

                    // 如果结果是 BLOCKED（阻断），记录日志并抛出异常终止链
                    if (result.action() == Guardrail.Action.BLOCKED) {
                        writeLog(ctx, g, result);
                        throw new GuardrailException(
                                g.rule().name(), phase.name(), result.reason());
                    }

                    // 如果结果是 WARNING（警告），记录日志但不中断，继续执行后续规则
                    if (result.action() == Guardrail.Action.WARNING) {
                        writeLog(ctx, g, result);
                        log.warn("guardrail warning: rule={} reason={}", g.rule(), result.reason());
                    }
                    // 如果结果是 PASS（通过），什么都不做，继续下一条规则
                });
    }

    /**
     * 将护栏触发记录写入数据库审计日志。
     *
     * 记录哪个用户、哪条规则、在哪个阶段被触发，以及触发原因。
     * 写入操作使用 try-catch 包裹，确保日志写入失败不会影响主流程。
     *
     * @param ctx    护栏上下文
     * @param g      触发的护栏规则
     * @param result 评估结果
     */
    private void writeLog(GuardrailContext ctx, Guardrail g, Guardrail.Result result) {
        try {
            GuardrailLog row = new GuardrailLog();
            row.setUserId(ctx.getUserId());         // 记录用户 ID
            row.setRuleName(g.rule().name());       // 记录规则名称
            row.setTriggerType(g.phase().name());   // 记录触发阶段（INPUT / OUTPUT）

            // 根据阶段不同，分别记录输入文本或输出文本
            if (g.phase() == Guardrail.Phase.INPUT) {
                row.setInputText(trunc(ctx.getText(), 500));  // 截断到 500 字符防止日志过大
            } else {
                row.setOutputText(trunc(ctx.getText(), 500));
            }

            row.setReason(result.reason());            // 记录触发原因
            row.setActionTaken(result.action().name()); // 记录采取的动作
            guardrailLogMapper.insert(row);             // 持久化到数据库
        } catch (Exception e) {
            // 日志写入失败不应影响主流程，仅打印错误日志
            log.error("failed to persist guardrail log", e);
        }
    }

    /**
     * 字符串截断工具方法。
     *
     * 将过长的文本截取前 n 个字符，防止写入数据库时超出字段长度限制。
     *
     * @param s 原始字符串
     * @param n 最大长度
     * @return 截断后的字符串（null 安全）
     */
    private static String trunc(String s, int n) {
        return s == null ? null : (s.length() <= n ? s : s.substring(0, n));
    }
}
