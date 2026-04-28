package com.socialflow.service.note.enrich;

import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmProviderService;
import com.socialflow.service.ai.llm.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自动分类 —— 在已有分类列表里挑一个，挑不到则不返回（避免乱建）
 */
@Component
public class CategoryEnricher implements NoteEnricher {

    @Override public String name() { return "category"; }

    @Override
    public void enrich(EnrichContext ctx, LlmProviderService provider,
                       LlmConfig config, NoteEnrichResult result) {
        if (ctx.existingCategories() == null || ctx.existingCategories().isEmpty()) return;
        String options = String.join(" / ", ctx.existingCategories());

        String sys = "你是一个笔记归类助理。给定一段笔记和现有分类列表，" +
                     "请从列表中挑选最匹配的一个分类名直接返回；如果都不合适，返回 NONE。" +
                     "只输出分类名或 NONE，不要任何解释。";
        String user = "现有分类：[" + options + "]\n\n标题：" + (ctx.title() == null ? "" : ctx.title())
                + "\n正文：\n" + SummaryEnricher.truncate(ctx.contentMd(), 3000);
        LlmResponse r = provider.chat(List.of(ChatMessage.system(sys), ChatMessage.user(user)), config);
        if (r == null || r.getContent() == null) return;

        String guess = r.getContent().trim().replaceAll("[\"'.,。；;]", "");
        if (guess.isEmpty() || "NONE".equalsIgnoreCase(guess)) return;
        // 只在确实命中现有分类时回填
        for (String c : ctx.existingCategories()) {
            if (c.equalsIgnoreCase(guess)) {
                result.setCategoryGuess(c);
                return;
            }
        }
    }
}
