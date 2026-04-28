package com.socialflow.service.note.enrich;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.dao.mapper.NoteCategoryMapper;
import com.socialflow.model.entity.NoteCategory;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmProviderService;
import com.socialflow.service.ai.llm.LlmRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI 富化协调器 —— 一次性把所有 enricher 并发跑完
 *
 * 设计对齐 ContentServiceImpl / MultiAgentServiceImpl / MemoryServiceImpl：
 *   - 直接注入项目级 systemApiKey + defaultProvider，不再查用户个人 key
 *   - LLM Provider 自身会做 user-key → systemApiKey 的兜底
 *   - 单 enricher 抛错 → 仅记 failures，其他继续
 *   - 单 enricher 30s 超时
 */
@Slf4j
@Service
public class NoteEnrichService {

    private final List<NoteEnricher> enrichers;
    private final LlmRouter llmRouter;
    private final NoteCategoryMapper categoryMapper;

    /** 项目级默认 LLM 提供者，由 application.yml / Nacos 注入；缺省 DEEPSEEK */
    @Value("${socialflow.ai.default-provider:DEEPSEEK}")
    private String defaultProviderCode;

    /** 项目级 system API key —— 兜底用，不进数据库 */
    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    /** 默认对话模型，可被 application.yml 覆盖 */
    @Value("${socialflow.ai.providers.deepseek.default-model:deepseek-chat}")
    private String defaultModel;

    private static final int PER_ENRICHER_TIMEOUT_SEC = 30;

    public NoteEnrichService(List<NoteEnricher> enrichers,
                              LlmRouter llmRouter,
                              NoteCategoryMapper categoryMapper) {
        this.enrichers = enrichers;
        this.llmRouter = llmRouter;
        this.categoryMapper = categoryMapper;
    }

    public NoteEnrichResult enrich(Long userId, String parsedTitle, String parsedMd) {
        NoteEnrichResult result = NoteEnrichResult.builder()
                .enriched(false)
                .build();

        LlmProviderService svc;
        try {
            svc = llmRouter.get(defaultProviderCode);
        } catch (RuntimeException e) {
            log.warn("default provider [{}] not registered: {}", defaultProviderCode, e.getMessage());
            result.setSkippedReason("默认 LLM 供应商 " + defaultProviderCode + " 未注册到 LlmRouter");
            return result;
        }

        // systemApiKey 为空时，依赖 Provider 内部的 systemApiKey 字段（@Value 注入到 Provider 自身）
        // 也兜底，所以这里即使为 null，Provider.resolveApiKey 也会用自己持有的 systemApiKey
        LlmConfig config = LlmConfig.builder()
                .model(defaultModel)
                .temperature(0.3)
                .maxTokens(800)
                .apiKey(systemApiKey == null || systemApiKey.isBlank() ? null : systemApiKey)
                .userId(userId)
                .build();

        // 用户的现有分类列表 —— 给 CategoryEnricher 做受限选择
        List<String> existingCats = categoryMapper.selectList(
                new LambdaQueryWrapper<NoteCategory>().eq(NoteCategory::getUserId, userId)
        ).stream().map(NoteCategory::getName).toList();

        NoteEnricher.EnrichContext ctx = new NoteEnricher.EnrichContext(parsedTitle, parsedMd, existingCats);

        // 并发跑所有 enricher，每个独立超时
        List<CompletableFuture<Void>> futures = new ArrayList<>(enrichers.size());
        for (NoteEnricher e : enrichers) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    e.enrich(ctx, svc, config, result);
                } catch (Exception ex) {
                    log.warn("enricher {} failed: {}", e.name(), ex.getMessage());
                    result.getFailures().add(e.name() + ": " + ex.getMessage());
                }
            }));
        }
        for (int i = 0; i < futures.size(); i++) {
            try {
                futures.get(i).get(PER_ENRICHER_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                log.warn("enricher {} timeout after {}s", enrichers.get(i).name(), PER_ENRICHER_TIMEOUT_SEC);
                result.getFailures().add(enrichers.get(i).name() + ": timeout");
                futures.get(i).cancel(true);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ee) {
                // 已在 runAsync 的 catch 里记录
            }
        }
        result.setEnriched(true);
        return result;
    }
}
