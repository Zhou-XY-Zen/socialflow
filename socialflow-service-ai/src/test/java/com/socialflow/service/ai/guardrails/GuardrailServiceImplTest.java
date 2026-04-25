package com.socialflow.service.ai.guardrails;

import com.socialflow.common.enums.GuardrailRule;
import com.socialflow.common.exception.GuardrailException;
import com.socialflow.dao.mapper.GuardrailLogMapper;
import com.socialflow.model.entity.GuardrailLog;
import com.socialflow.service.ai.guardrails.impl.GuardrailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link GuardrailServiceImpl} 的单元测试 —— 责任链行为的全覆盖：
 *
 * <ul>
 *   <li>护栏总开关 enabled=false 时跳过所有检查</li>
 *   <li>按 phase 隔离（INPUT 检查不会触发 OUTPUT 规则）</li>
 *   <li>按 order 升序执行</li>
 *   <li>BLOCKED 立即抛 GuardrailException 并写审计日志，后续规则不再执行</li>
 *   <li>WARNING 写审计日志但继续后续规则</li>
 *   <li>PASS 不写日志、不打断</li>
 *   <li>审计日志写入失败不会影响主流程（吞掉异常）</li>
 *   <li>GuardrailException 上携带 ruleName 和 triggerType（INPUT/OUTPUT）</li>
 *   <li>输入文本超过 500 字会被截断（避免日志表字段溢出）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GuardrailServiceImplTest {

    @Mock
    private GuardrailLogMapper guardrailLogMapper;

    /**
     * 测试用的 Guardrail Stub：可配置 phase / order / 评估结果 / 调用计数器。
     * 没用 Mockito 的 mock，是因为 chain 行为依赖大量字段交互，手写更直观。
     */
    private static class StubGuardrail implements Guardrail {
        final GuardrailRule rule;
        final Phase phase;
        final int order;
        final Result result;
        final AtomicInteger invocations = new AtomicInteger();

        StubGuardrail(GuardrailRule rule, Phase phase, int order, Result result) {
            this.rule = rule;
            this.phase = phase;
            this.order = order;
            this.result = result;
        }

        @Override
        public GuardrailRule rule() { return rule; }
        @Override
        public Phase phase() { return phase; }
        @Override
        public int order() { return order; }
        @Override
        public Result evaluate(GuardrailContext ctx) {
            invocations.incrementAndGet();
            return result;
        }
    }

    private GuardrailServiceImpl buildService(List<Guardrail> guardrails, boolean enabled) {
        GuardrailServiceImpl svc = new GuardrailServiceImpl(guardrails, guardrailLogMapper);
        ReflectionTestUtils.setField(svc, "enabled", enabled);
        return svc;
    }

    @Nested
    @DisplayName("总开关")
    class MasterSwitch {

        @Test
        @DisplayName("enabled=false 时 checkInput 跳过所有规则")
        void disabled_skipsAllChecks_input() {
            StubGuardrail blocking = new StubGuardrail(
                    GuardrailRule.SENSITIVE_WORD, Guardrail.Phase.INPUT, 10,
                    Guardrail.Result.blocked("hit"));
            GuardrailServiceImpl svc = buildService(List.of(blocking), false);

            svc.checkInput(1L, "anything");

            assertThat(blocking.invocations.get()).isZero();
            verify(guardrailLogMapper, never()).insert(any(GuardrailLog.class));
        }

        @Test
        @DisplayName("enabled=false 时 checkOutput 跳过所有规则")
        void disabled_skipsAllChecks_output() {
            StubGuardrail blocking = new StubGuardrail(
                    GuardrailRule.CONTENT_SAFETY, Guardrail.Phase.OUTPUT, 10,
                    Guardrail.Result.blocked("nsfw"));
            GuardrailServiceImpl svc = buildService(List.of(blocking), false);

            svc.checkOutput(1L, "anything", "XIAOHONGSHU", null);

            assertThat(blocking.invocations.get()).isZero();
        }
    }

    @Nested
    @DisplayName("责任链执行顺序")
    class Ordering {

        @Test
        @DisplayName("checkInput 只跑 INPUT 阶段规则，不会触发 OUTPUT 规则")
        void phaseIsolation_input() {
            StubGuardrail input1 = new StubGuardrail(
                    GuardrailRule.INPUT_LENGTH_CHECK, Guardrail.Phase.INPUT, 10, Guardrail.Result.pass());
            StubGuardrail output1 = new StubGuardrail(
                    GuardrailRule.HALLUCINATION, Guardrail.Phase.OUTPUT, 10, Guardrail.Result.pass());
            GuardrailServiceImpl svc = buildService(List.of(input1, output1), true);

            svc.checkInput(1L, "hello");

            assertThat(input1.invocations.get()).isEqualTo(1);
            assertThat(output1.invocations.get()).isZero();
        }

        @Test
        @DisplayName("规则按 order 升序执行（10 → 20 → 30）")
        void executionOrder_byAscendingOrder() {
            List<String> callOrder = new ArrayList<>();
            Guardrail r10 = new StubGuardrail(
                    GuardrailRule.INPUT_LENGTH_CHECK, Guardrail.Phase.INPUT, 10, Guardrail.Result.pass()) {
                @Override public Result evaluate(GuardrailContext ctx) { callOrder.add("r10"); return Result.pass(); }
            };
            Guardrail r20 = new StubGuardrail(
                    GuardrailRule.SENSITIVE_WORD, Guardrail.Phase.INPUT, 20, Guardrail.Result.pass()) {
                @Override public Result evaluate(GuardrailContext ctx) { callOrder.add("r20"); return Result.pass(); }
            };
            Guardrail r30 = new StubGuardrail(
                    GuardrailRule.PROMPT_INJECTION, Guardrail.Phase.INPUT, 30, Guardrail.Result.pass()) {
                @Override public Result evaluate(GuardrailContext ctx) { callOrder.add("r30"); return Result.pass(); }
            };
            // 故意打乱注册顺序，验证排序是否生效
            GuardrailServiceImpl svc = buildService(List.of(r30, r10, r20), true);

            svc.checkInput(1L, "hello");

            assertThat(callOrder).containsExactly("r10", "r20", "r30");
        }
    }

    @Nested
    @DisplayName("BLOCKED / WARNING / PASS 行为")
    class Actions {

        @Test
        @DisplayName("BLOCKED 立即抛 GuardrailException 且后续规则不再执行")
        void blocked_shortCircuitsChain() {
            StubGuardrail first = new StubGuardrail(
                    GuardrailRule.SENSITIVE_WORD, Guardrail.Phase.INPUT, 10,
                    Guardrail.Result.blocked("命中敏感词：xxx"));
            StubGuardrail second = new StubGuardrail(
                    GuardrailRule.PROMPT_INJECTION, Guardrail.Phase.INPUT, 20,
                    Guardrail.Result.pass());
            GuardrailServiceImpl svc = buildService(List.of(first, second), true);

            Throwable thrown = catchThrowable(() -> svc.checkInput(42L, "bad input"));

            assertThat(thrown).isInstanceOf(GuardrailException.class);
            GuardrailException gex = (GuardrailException) thrown;
            assertThat(gex.getRuleName()).isEqualTo("SENSITIVE_WORD");
            assertThat(gex.getTriggerType()).isEqualTo("INPUT");
            assertThat(gex.getMessage()).contains("命中敏感词");
            assertThat(first.invocations.get()).isEqualTo(1);
            assertThat(second.invocations.get()).isZero();
        }

        @Test
        @DisplayName("BLOCKED 在抛异常前会写一条审计日志")
        void blocked_writesAuditLog() {
            StubGuardrail blocking = new StubGuardrail(
                    GuardrailRule.SENSITIVE_WORD, Guardrail.Phase.INPUT, 10,
                    Guardrail.Result.blocked("命中敏感词"));
            GuardrailServiceImpl svc = buildService(List.of(blocking), true);

            assertThatThrownBy(() -> svc.checkInput(42L, "bad input"))
                    .isInstanceOf(GuardrailException.class);

            ArgumentCaptor<GuardrailLog> captor = ArgumentCaptor.forClass(GuardrailLog.class);
            verify(guardrailLogMapper, times(1)).insert(captor.capture());
            GuardrailLog log = captor.getValue();
            assertThat(log.getUserId()).isEqualTo(42L);
            assertThat(log.getRuleName()).isEqualTo("SENSITIVE_WORD");
            assertThat(log.getTriggerType()).isEqualTo("INPUT");
            assertThat(log.getInputText()).isEqualTo("bad input");
            assertThat(log.getActionTaken()).isEqualTo("BLOCKED");
            assertThat(log.getReason()).contains("命中敏感词");
        }

        @Test
        @DisplayName("WARNING 写日志但继续后续规则")
        void warning_continuesChainAndLogs() {
            StubGuardrail warning = new StubGuardrail(
                    GuardrailRule.BRAND_TONE, Guardrail.Phase.OUTPUT, 10,
                    Guardrail.Result.warning("语气稍激进"));
            StubGuardrail next = new StubGuardrail(
                    GuardrailRule.PLATFORM_RULE_CHECK, Guardrail.Phase.OUTPUT, 20,
                    Guardrail.Result.pass());
            GuardrailServiceImpl svc = buildService(List.of(warning, next), true);

            svc.checkOutput(1L, "some output", "XIAOHONGSHU", null);

            assertThat(warning.invocations.get()).isEqualTo(1);
            assertThat(next.invocations.get()).isEqualTo(1);
            verify(guardrailLogMapper, times(1)).insert(any(GuardrailLog.class));
        }

        @Test
        @DisplayName("PASS 不写日志，不打断")
        void pass_noLogNoInterrupt() {
            StubGuardrail r1 = new StubGuardrail(
                    GuardrailRule.INPUT_LENGTH_CHECK, Guardrail.Phase.INPUT, 10, Guardrail.Result.pass());
            StubGuardrail r2 = new StubGuardrail(
                    GuardrailRule.SENSITIVE_WORD, Guardrail.Phase.INPUT, 20, Guardrail.Result.pass());
            GuardrailServiceImpl svc = buildService(List.of(r1, r2), true);

            svc.checkInput(1L, "all clean");

            assertThat(r1.invocations.get()).isEqualTo(1);
            assertThat(r2.invocations.get()).isEqualTo(1);
            verify(guardrailLogMapper, never()).insert(any(GuardrailLog.class));
        }
    }

    @Nested
    @DisplayName("健壮性")
    class Robustness {

        @Test
        @DisplayName("写审计日志失败时不影响主流程（异常被吞掉）")
        void logWriteFailure_doesNotAffectFlow() {
            org.mockito.Mockito.when(guardrailLogMapper.insert(any(GuardrailLog.class)))
                    .thenThrow(new RuntimeException("DB down"));
            StubGuardrail warning = new StubGuardrail(
                    GuardrailRule.BRAND_TONE, Guardrail.Phase.OUTPUT, 10,
                    Guardrail.Result.warning("语气稍激进"));
            GuardrailServiceImpl svc = buildService(List.of(warning), true);

            // 不应抛出
            svc.checkOutput(1L, "out", "XIAOHONGSHU", null);

            verify(guardrailLogMapper, times(1)).insert(any(GuardrailLog.class));
        }

        @Test
        @DisplayName("超过 500 字符的输入文本在审计日志中被截断")
        void longInputText_truncatedInLog() {
            String longText = "A".repeat(800);
            StubGuardrail blocking = new StubGuardrail(
                    GuardrailRule.SENSITIVE_WORD, Guardrail.Phase.INPUT, 10,
                    Guardrail.Result.blocked("hit"));
            GuardrailServiceImpl svc = buildService(List.of(blocking), true);

            assertThatThrownBy(() -> svc.checkInput(1L, longText))
                    .isInstanceOf(GuardrailException.class);

            ArgumentCaptor<GuardrailLog> captor = ArgumentCaptor.forClass(GuardrailLog.class);
            verify(guardrailLogMapper).insert(captor.capture());
            assertThat(captor.getValue().getInputText()).hasSize(500);
        }

        @Test
        @DisplayName("没有任何规则时也能正常返回")
        void noRulesRegistered_returnsNormally() {
            GuardrailServiceImpl svc = buildService(Collections.emptyList(), true);

            svc.checkInput(1L, "hello");
            svc.checkOutput(1L, "world", "XIAOHONGSHU", null);

            verify(guardrailLogMapper, never()).insert(any(GuardrailLog.class));
        }
    }

}
