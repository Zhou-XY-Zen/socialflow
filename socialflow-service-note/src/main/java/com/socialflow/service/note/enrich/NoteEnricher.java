package com.socialflow.service.note.enrich;

import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmProviderService;

import java.util.List;

/**
 * 单一富化任务接口
 *
 * 实现类只关心"做一件事"：拿到 parsedMd + 标题 → 输出某一种结构化产物，
 * 然后由 NoteEnrichService 并发调度并合并进 NoteEnrichResult。
 */
public interface NoteEnricher {

    /** 用于日志和失败列表展示 */
    String name();

    /**
     * 执行富化。实现里可调 provider.chat(...) 同步拿结果。
     *
     * @param ctx       上下文：parsedMd / parsedTitle / 已有分类列表
     * @param provider  已 resolved 的 LLM provider
     * @param config    已构建好的 LlmConfig（含解密 API Key）
     * @param result    回填到这里
     */
    void enrich(EnrichContext ctx, LlmProviderService provider,
                LlmConfig config, NoteEnrichResult result);

    /**
     * 上下文 record，所有 enricher 共享
     */
    record EnrichContext(String title, String contentMd, List<String> existingCategories) {}
}
