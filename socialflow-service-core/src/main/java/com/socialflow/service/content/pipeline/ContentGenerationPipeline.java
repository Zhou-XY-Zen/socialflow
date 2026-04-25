package com.socialflow.service.content.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 内容生成 pipeline 调度器 —— 按 {@link GenerationStep#order()} 依次执行所有注册的 step。
 *
 * <p>Spring 自动注入所有 {@link GenerationStep} 实现，构造时按 order 排序。
 * 业务侧（{@code ContentServiceImpl}）只需调用 {@link #run(GenerationContext)}，
 * 不必关心具体步骤组成 —— 这是把"过程性 5 步代码"换成"声明式 step 列表"的关键。</p>
 *
 * <p>每一步的执行耗时都打 INFO 日志，配合 traceId 便于排查"哪一步慢了"。</p>
 */
@Slf4j
@Component
public class ContentGenerationPipeline {

    /** 已按 order 升序排好的 step 列表 */
    private final List<GenerationStep> orderedSteps;

    public ContentGenerationPipeline(List<GenerationStep> steps) {
        this.orderedSteps = steps.stream()
                .sorted(Comparator.comparingInt(GenerationStep::order))
                .toList();
        log.info("[GenerationPipeline] registered {} steps: {}",
                orderedSteps.size(),
                orderedSteps.stream().map(s -> s.order() + ":" + s.name()).toList());
    }

    /**
     * 执行整条 pipeline。
     * 任一 step 抛异常会立即中止后续步骤，异常向上传递。
     */
    public void run(GenerationContext ctx) {
        for (GenerationStep step : orderedSteps) {
            long start = System.nanoTime();
            try {
                step.apply(ctx);
            } catch (RuntimeException e) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                log.warn("[GenerationPipeline] step {} 失败 userId={} durationMs={}: {}",
                        step.name(), ctx.getUserId(), ms, e.getMessage());
                throw e;
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.debug("[GenerationPipeline] step {} 完成 userId={} durationMs={}",
                    step.name(), ctx.getUserId(), ms);
        }
    }
}
