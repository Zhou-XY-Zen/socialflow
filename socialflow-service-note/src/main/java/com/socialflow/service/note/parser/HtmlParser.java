package com.socialflow.service.note.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * HTML → Markdown 朴素转换
 *
 * 不追求完美还原，只挑 P0 必要标签：标题、段落、列表、链接、图片、代码块、引用。
 * URL 剪藏场景下 HtmlClipFetcher 会先用 Readability 抽正文，再喂给本解析器。
 */
@Component
public class HtmlParser implements NoteParser {

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("html", "htm");
    }

    @Override
    public ParsedNote parse(String fileName, InputStream in) throws IOException {
        String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(raw);
        String title = doc.title();
        if (title == null || title.isBlank()) {
            title = MarkdownParser.stripExt(fileName);
        }

        // 去掉无关元素
        doc.select("script,style,noscript,iframe,nav,header,footer,form,aside").remove();

        StringBuilder md = new StringBuilder();
        Element body = doc.body();
        if (body != null) {
            walk(body, md);
        }

        return ParsedNote.builder()
                .title(title.trim())
                .contentMd(md.toString().replaceAll("\\n{3,}", "\n\n").trim())
                .build();
    }

    private void walk(Node node, StringBuilder out) {
        if (node instanceof TextNode tn) {
            out.append(tn.text());
            return;
        }
        if (!(node instanceof Element el)) return;

        String tag = el.tagName();
        switch (tag) {
            case "h1" -> { out.append("\n# "); childrenToText(el, out); out.append("\n\n"); }
            case "h2" -> { out.append("\n## "); childrenToText(el, out); out.append("\n\n"); }
            case "h3" -> { out.append("\n### "); childrenToText(el, out); out.append("\n\n"); }
            case "h4" -> { out.append("\n#### "); childrenToText(el, out); out.append("\n\n"); }
            case "h5", "h6" -> { out.append("\n##### "); childrenToText(el, out); out.append("\n\n"); }
            case "p"  -> { for (Node c : el.childNodes()) walk(c, out); out.append("\n\n"); }
            case "br" -> out.append("\n");
            case "strong", "b" -> { out.append("**"); childrenToText(el, out); out.append("**"); }
            case "em", "i"     -> { out.append("*"); childrenToText(el, out); out.append("*"); }
            case "code" -> {
                if (el.parent() != null && "pre".equals(el.parent().tagName())) {
                    out.append("\n```\n").append(el.text()).append("\n```\n");
                } else {
                    out.append('`').append(el.text()).append('`');
                }
            }
            case "pre" -> {
                if (el.children().isEmpty() || !"code".equals(el.child(0).tagName())) {
                    out.append("\n```\n").append(el.text()).append("\n```\n");
                } else {
                    for (Node c : el.childNodes()) walk(c, out);
                }
            }
            case "blockquote" -> { out.append("\n> "); childrenToText(el, out); out.append("\n\n"); }
            case "ul" -> {
                for (Element li : el.select("> li")) {
                    out.append("- ");
                    for (Node c : li.childNodes()) walk(c, out);
                    out.append('\n');
                }
                out.append('\n');
            }
            case "ol" -> {
                int i = 1;
                for (Element li : el.select("> li")) {
                    out.append(i++).append(". ");
                    for (Node c : li.childNodes()) walk(c, out);
                    out.append('\n');
                }
                out.append('\n');
            }
            case "a" -> {
                String href = el.attr("href");
                out.append('[');
                childrenToText(el, out);
                out.append(']').append('(').append(href).append(')');
            }
            case "img" -> {
                String src = el.attr("src");
                String alt = el.attr("alt");
                out.append("![").append(alt).append("](").append(src).append(")");
            }
            default -> { for (Node c : el.childNodes()) walk(c, out); }
        }
    }

    private void childrenToText(Element el, StringBuilder out) {
        for (Node c : el.childNodes()) walk(c, out);
    }
}
