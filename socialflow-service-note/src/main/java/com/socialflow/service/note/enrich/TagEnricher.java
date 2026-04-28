package com.socialflow.service.note.enrich;

import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmProviderService;
import com.socialflow.service.ai.llm.LlmResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 自动打标签 —— 3~7 个简短中文/英文短语
 */
@Component
public class TagEnricher implements NoteEnricher {

    @Override public String name() { return "tags"; }

    @Override
    public void enrich(EnrichContext ctx, LlmProviderService provider,
                       LlmConfig config, NoteEnrichResult result) {
        String sys = "你是一个内容标签助理。请为给定笔记生成 3~7 个简洁、可复用的标签，" +
                     "每个标签 2~6 个字。只输出标签本身，用英文逗号分隔，不要任何额外说明、引号或编号。";
        String user = "标题：" + (ctx.title() == null ? "" : ctx.title())
                + "\n正文：\n" + SummaryEnricher.truncate(ctx.contentMd(), 4000);
        LlmResponse r = provider.chat(List.of(ChatMessage.system(sys), ChatMessage.user(user)), config);
        if (r == null || r.getContent() == null) return;

        List<String> tags = Arrays.stream(r.getContent().split("[,，;；\\n]"))
                .map(String::trim)
                .map(s -> s.replaceAll("[\"'\\[\\]【】]", ""))
                .filter(s -> !s.isEmpty() && s.length() <= 12)
                .distinct()
                .limit(7)
                .toList();
        result.setTags(tags.isEmpty() ? Collections.emptyList() : tags);
    }
}
