package com.socialflow.service.note.enrich;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.dao.mapper.NoteCategoryMapper;
import com.socialflow.model.entity.NoteCategory;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmProviderService;
import com.socialflow.service.ai.llm.LlmRouter;
import com.socialflow.service.user.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 关键设计：
 *   - 用户没配 API Key → 整个 enrich 静默跳过（result.enriched=false），不抛错
 *   - 单个 enricher 抛错 → 仅记录到 failures，不影响其他 enricher
 *   - 每个 enricher 30s 超时，避免某家 LLM 慢拖死整批
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteEnrichService {

    private final List<NoteEnricher> enrichers;
    private final LlmRouter llmRouter;
    private final ApiKeyService apiKeyService;
    private final NoteCategoryMapper categoryMapper;

    private static final int PER_ENRICHER_TIMEOUT_SEC = 30;
    private static final String DEFAULT_MODEL = "deepseek-chat";

    public NoteEnrichResult enrich(Long userId, String parsedTitle, String parsedMd) {
        NoteEnrichResult result = NoteEnrichResult.builder()
                .enriched(false)
                .build();

        // 用户没配默认 provider 或 key → 静默跳过
        LlmProvider provider;
        String apiKey;
        try {
            provider = apiKeyService.resolveDefaultProvider(userId);
            apiKey = apiKeyService.getDecryptedKey(userId, provider);
        } catch (RuntimeException e) {
            log.info("user {} has no default provider, skip enrich", userId);
            return result;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.info("user {} has no API key for {}, skip enrich", userId, provider);
            return result;
        }

        LlmProviderService svc = llmRouter.get(provider);
        LlmConfig config = LlmConfig.builder()
                .model(DEFAULT_MODEL)
                .temperature(0.3)
                .maxTokens(800)
                .apiKey(apiKey)
                .userId(userId)
                .build();

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
                    // failures 是 CopyOnWriteArrayList，无需外部同步
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
                // 已在 catch 内记录
            }
        }
        result.setEnriched(true);
        return result;
    }
}
