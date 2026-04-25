package com.socialflow.service.content.pipeline.steps;

import com.socialflow.service.ai.guardrails.GuardrailService;
import com.socialflow.service.content.pipeline.GenerationContext;
import com.socialflow.service.content.pipeline.GenerationStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 输入护栏 step —— pipeline 的第一站。
 *
 * <p>将用户输入主题交给 {@link GuardrailService} 跑 INPUT 阶段所有规则。
 * 命中 BLOCKED 时会抛 {@link com.socialflow.common.exception.GuardrailException}，
 * 由 pipeline 调度器向上传递，整条链立即中止。</p>
 *
 * <p>Order=10 —— 任何 LLM 调用之前都必须先过这一关，避免敏感词 / prompt injection
 * 把成本（token + 时间 + 风控代价）转嫁给系统。</p>
 */
@Component
@RequiredArgsConstructor
public class InputGuardrailStep implements GenerationStep {

    private final GuardrailService guardrailService;

    @Override
    public String name() {
        return "InputGuardrail";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void apply(GenerationContext ctx) {
        guardrailService.checkInput(ctx.getUserId(), ctx.getDto().getTopic());
    }
}
