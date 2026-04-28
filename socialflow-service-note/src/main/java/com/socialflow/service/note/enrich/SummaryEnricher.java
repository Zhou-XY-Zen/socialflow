package com.socialflow.service.note.enrich;

import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmProviderService;
import com.socialflow.service.ai.llm.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TL;DR 摘要 —— 150 字以内
 */
@Component
public class SummaryEnricher implements NoteEnricher {

    @Override public String name() { return "summary"; }

    @Override
    public void enrich(EnrichContext ctx, LlmProviderService provider,
                       LlmConfig config, NoteEnrichResult result) {
        String sys = "你是一个善于提炼要点的笔记助理。请用 150 字以内为给定笔记写一段中文摘要，" +
                     "突出核心结论与要点，不要列出无意义的目录式描述。直接给出摘要文本，无需任何前后缀。";
        String user = "笔记标题：" + nullSafe(ctx.title()) + "\n\n正文：\n" + truncate(ctx.contentMd(), 6000);
        LlmResponse r = provider.chat(List.of(ChatMessage.system(sys), ChatMessage.user(user)), config);
        if (r != null && r.getContent() != null) {
            result.setSummary(r.getContent().trim());
        }
    }

    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
    static String nullSafe(String s) { return s == null ? "" : s; }
}
