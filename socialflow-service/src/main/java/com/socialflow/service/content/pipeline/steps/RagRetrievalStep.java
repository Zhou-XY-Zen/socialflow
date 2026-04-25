package com.socialflow.service.content.pipeline.steps;

import com.socialflow.service.ai.rag.RagPipelineService;
import com.socialflow.service.content.pipeline.GenerationContext;
import com.socialflow.service.content.pipeline.GenerationStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RAG 检索 step —— 如果用户指定了 kbId，从知识库取 top-K 相关文档作为参考资料。
 *
 * <p>未指定 kbId 直接跳过，不抛异常 —— 普通用户场景占多数，强制知识库会拉低体验。</p>
 *
 * <p>Order=20 —— 必须在 PromptRender 之前，让 RAG 文本能注入到用户提示词中。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagRetrievalStep implements GenerationStep {

    private final RagPipelineService ragPipelineService;

    /** 默认拉取 top-5 个相关文档片段。后续可改为读配置 / DTO。 */
    private static final int DEFAULT_TOP_K = 5;

    @Override
    public String name() {
        return "RagRetrieval";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void apply(GenerationContext ctx) {
        Long kbId = ctx.getDto().getKbId();
        if (kbId == null) {
            return;
        }
        String context = ragPipelineService.retrieveAsContext(
                ctx.getUserId(), kbId, ctx.getDto().getTopic(), DEFAULT_TOP_K);
        ctx.setRagContext(context);
        log.debug("[RagRetrievalStep] kb={} hit, contextLength={}",
                kbId, context == null ? 0 : context.length());
    }
}
