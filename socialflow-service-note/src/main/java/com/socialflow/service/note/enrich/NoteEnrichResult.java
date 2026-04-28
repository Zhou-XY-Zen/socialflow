package com.socialflow.service.note.enrich;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AI 富化结果（合并所有 enricher 的产物）
 *
 * 字段全部可选 —— 单个 enricher 失败不影响其他字段。
 *
 * 并发约定：4 个 enricher 在 NoteEnrichService 里并发跑：
 *   - summary / tags / outline / categoryGuess 各由不同 enricher 写，**字段不重叠**
 *   - failures 由所有 enricher 共写 → 用 CopyOnWriteArrayList
 */
@Data
@Builder
public class NoteEnrichResult {

    private String summary;          // TL;DR 150 字
    private List<String> tags;       // 自动打标签 3~7 个
    private List<String> outline;    // 大纲 H1/H2
    private String categoryGuess;    // AI 建议的分类名（在已有分类里挑，否则空）

    private boolean enriched;        // 整体是否走了富化（false=用户没配 key 等被跳过）

    @Builder.Default
    private List<String> failures = new CopyOnWriteArrayList<>();
}
