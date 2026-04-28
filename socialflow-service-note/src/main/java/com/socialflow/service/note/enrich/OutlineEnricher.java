package com.socialflow.service.note.enrich;

import org.springframework.stereotype.Component;

import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmProviderService;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大纲提取 —— 直接抓 Markdown H1/H2/H3，无需 LLM（零 token 消耗）
 *
 * 对绝大多数已写好结构的笔记，规则化抽取效果不输 LLM。
 * 如果完全没标题（单段长文），可在后续版本切换到 LLM 兜底。
 */
@Component
public class OutlineEnricher implements NoteEnricher {

    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+?)$", Pattern.MULTILINE);

    @Override public String name() { return "outline"; }

    @Override
    public void enrich(EnrichContext ctx, LlmProviderService provider,
                       LlmConfig config, NoteEnrichResult result) {
        if (ctx.contentMd() == null) return;
        Matcher m = HEADING.matcher(ctx.contentMd());
        List<String> outline = new ArrayList<>();
        while (m.find() && outline.size() < 30) {
            String level = m.group(1);
            String text = m.group(2).trim();
            outline.add("  ".repeat(level.length() - 1) + text);
        }
        if (!outline.isEmpty()) result.setOutline(outline);
    }
}
