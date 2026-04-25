package com.socialflow.service.content.pipeline;

import com.socialflow.model.dto.ContentGenerateDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ContentGenerationPipeline} 调度器的单元测试。
 *
 * <p>验证：</p>
 * <ul>
 *   <li>step 按 order 升序执行</li>
 *   <li>step 抛异常会立即中止后续 step</li>
 *   <li>空 step 列表也能正常运行</li>
 *   <li>构造函数会基于注入的列表排序，不依赖输入顺序</li>
 * </ul>
 */
@DisplayName("ContentGenerationPipeline")
class ContentGenerationPipelineTest {

    /** 记录调用顺序的 step。每次 apply 都把自己的 name 写进 sink。 */
    private static class RecordingStep implements GenerationStep {
        final String name;
        final int order;
        final List<String> sink;
        final RuntimeException toThrow;

        RecordingStep(String name, int order, List<String> sink, RuntimeException toThrow) {
            this.name = name;
            this.order = order;
            this.sink = sink;
            this.toThrow = toThrow;
        }

        @Override public String name() { return name; }
        @Override public int order() { return order; }
        @Override public void apply(GenerationContext ctx) {
            sink.add(name);
            if (toThrow != null) throw toThrow;
        }
    }

    private GenerationContext newCtx() {
        return new GenerationContext(42L, new ContentGenerateDTO());
    }

    @Test
    @DisplayName("step 按 order 升序执行（即使注入顺序乱）")
    void executesInAscendingOrder() {
        List<String> calls = new ArrayList<>();
        ContentGenerationPipeline pipeline = new ContentGenerationPipeline(List.of(
                new RecordingStep("third", 30, calls, null),
                new RecordingStep("first", 10, calls, null),
                new RecordingStep("second", 20, calls, null)));

        pipeline.run(newCtx());

        assertThat(calls).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("step 抛异常立即中止后续 step")
    void abortsOnException() {
        List<String> calls = new ArrayList<>();
        RuntimeException boom = new RuntimeException("step failed");
        ContentGenerationPipeline pipeline = new ContentGenerationPipeline(List.of(
                new RecordingStep("first", 10, calls, null),
                new RecordingStep("second", 20, calls, boom),
                new RecordingStep("third", 30, calls, null)));

        assertThatThrownBy(() -> pipeline.run(newCtx()))
                .isSameAs(boom);

        // first + second 跑了，third 没跑
        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    @DisplayName("空 step 列表 run() 不抛")
    void emptyPipeline_noThrow() {
        ContentGenerationPipeline pipeline = new ContentGenerationPipeline(List.of());
        pipeline.run(newCtx());  // 不抛即通过
    }

    @Test
    @DisplayName("相同 order 的 step 按注入顺序执行（稳定排序）")
    void sameOrder_stableSort() {
        List<String> calls = new ArrayList<>();
        ContentGenerationPipeline pipeline = new ContentGenerationPipeline(List.of(
                new RecordingStep("a", 10, calls, null),
                new RecordingStep("b", 10, calls, null),
                new RecordingStep("c", 10, calls, null)));

        pipeline.run(newCtx());

        assertThat(calls).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("step 间通过 GenerationContext 传值（写入 → 读取）")
    void contextSharedBetweenSteps() {
        List<String> readBack = new ArrayList<>();
        ContentGenerationPipeline pipeline = new ContentGenerationPipeline(List.of(
                new GenerationStep() {
                    @Override public String name() { return "writer"; }
                    @Override public int order() { return 10; }
                    @Override public void apply(GenerationContext ctx) {
                        ctx.setRagContext("hello from step1");
                    }
                },
                new GenerationStep() {
                    @Override public String name() { return "reader"; }
                    @Override public int order() { return 20; }
                    @Override public void apply(GenerationContext ctx) {
                        readBack.add(ctx.getRagContext());
                    }
                }));

        pipeline.run(newCtx());

        assertThat(readBack).containsExactly("hello from step1");
    }
}
