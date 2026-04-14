package com.socialflow.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 格式转换工具类。
 *
 * 提供两种转换能力：
 *   1. stripMarkdown  —— 去除所有 Markdown 语法标记，返回纯文本（适用于微信文本群发等场景）
 *   2. markdownToHtml —— 将 Markdown 转换为 HTML（适用于微信公众号图文/草稿等场景）
 *
 * 覆盖的 Markdown 语法：
 *   - 标题（#、##、### ...）
 *   - 粗体（**text**、__text__）
 *   - 斜体（*text*、_text_）
 *   - 粗斜体（***text***）
 *   - 删除线（~~text~~）
 *   - 行内代码（`code`）
 *   - 链接（[text](url)）
 *   - 图片（![alt](url)）
 *   - 无序列表（- item、* item）
 *   - 有序列表（1. item）
 *   - 分割线（---、***、___）
 *   - 引用（> text）
 *   - 代码块（```lang ... ```）
 */
public final class MarkdownUtil {

    private MarkdownUtil() {
        // 工具类禁止实例化
    }

    // ==================== 1. 去除 Markdown → 纯文本 ====================

    /**
     * 去除所有 Markdown 语法标记，返回可读的纯文本。
     * 用途：微信公众号纯文本群发、短信、摘要等不支持富文本的场景。
     *
     * @param markdown 原始 Markdown 文本
     * @return 去除标记后的纯文本
     */
    public static String stripMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        String text = markdown;

        // 1. 去除代码块（```...```），保留代码内容
        text = text.replaceAll("```[a-zA-Z]*\\n?", "");
        text = text.replaceAll("```", "");

        // 2. 去除图片标记 ![alt](url) → alt
        text = text.replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1");

        // 3. 去除链接标记 [text](url) → text
        text = text.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");

        // 4. 去除标题标记（行首 # 号），保留文字
        text = text.replaceAll("(?m)^#{1,6}\\s+", "");

        // 5. 去除粗斜体 ***text*** → text
        text = text.replaceAll("\\*{3}(.+?)\\*{3}", "$1");

        // 6. 去除粗体 **text** 或 __text__ → text
        text = text.replaceAll("\\*{2}(.+?)\\*{2}", "$1");
        text = text.replaceAll("__(.+?)__", "$1");

        // 7. 去除斜体 *text* 或 _text_ → text（注意不要误伤普通下划线）
        text = text.replaceAll("(?<![\\w*])\\*([^*]+?)\\*(?![\\w*])", "$1");
        text = text.replaceAll("(?<![\\w_])_([^_]+?)_(?![\\w_])", "$1");

        // 8. 去除删除线 ~~text~~ → text
        text = text.replaceAll("~~(.+?)~~", "$1");

        // 9. 去除行内代码 `code` → code
        text = text.replaceAll("`([^`]+)`", "$1");

        // 10. 去除分割线（独占一行的 ---、***、___）
        text = text.replaceAll("(?m)^\\s*[-*_]{3,}\\s*$", "");

        // 11. 去除引用标记 > → 保留文字
        text = text.replaceAll("(?m)^>\\s?", "");

        // 12. 去除无序列表标记（- 或 * 开头），保留文字
        text = text.replaceAll("(?m)^\\s*[-*+]\\s+", "");

        // 13. 去除有序列表标记（1. 2. 开头），保留文字
        text = text.replaceAll("(?m)^\\s*\\d+\\.\\s+", "");

        // 14. 合并多余空行（连续三行以上空行压缩为两行）
        text = text.replaceAll("\\n{3,}", "\n\n");

        return text.trim();
    }

    // ==================== 2. Markdown → HTML ====================

    /**
     * 将 Markdown 文本转换为 HTML。
     * 用途：微信公众号图文草稿、网页预览等支持 HTML 的场景。
     *
     * <p>转换逻辑按行处理，不使用第三方库，覆盖常见语法。</p>
     *
     * @param markdown 原始 Markdown 文本
     * @return HTML 字符串
     */
    public static String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "<p></p>";
        }

        // 先处理代码块（跨行语法，需要在逐行处理之前完成）
        String text = processCodeBlocks(markdown);

        String[] lines = text.split("\n");
        StringBuilder html = new StringBuilder();

        boolean inList = false;       // 是否在无序列表中
        boolean inOrderedList = false; // 是否在有序列表中

        for (String line : lines) {
            String trimmed = line.trim();

            // 空行：关闭列表，插入空段落间距
            if (trimmed.isEmpty()) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                if (inOrderedList) {
                    html.append("</ol>");
                    inOrderedList = false;
                }
                continue;
            }

            // 分割线
            if (trimmed.matches("^[-*_]{3,}$")) {
                html.append("<hr/>");
                continue;
            }

            // 标题（# ~ ######）
            Matcher headerMatcher = Pattern.compile("^(#{1,6})\\s+(.+)$").matcher(trimmed);
            if (headerMatcher.matches()) {
                int level = headerMatcher.group(1).length();
                String content = processInline(headerMatcher.group(2));
                html.append("<h").append(level).append(">")
                    .append(content)
                    .append("</h").append(level).append(">");
                continue;
            }

            // 引用 > text
            if (trimmed.startsWith(">")) {
                String content = processInline(trimmed.replaceFirst("^>\\s?", ""));
                html.append("<blockquote>").append(content).append("</blockquote>");
                continue;
            }

            // 无序列表项（- item / * item / + item）
            Matcher ulMatcher = Pattern.compile("^[-*+]\\s+(.+)$").matcher(trimmed);
            if (ulMatcher.matches()) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(processInline(ulMatcher.group(1))).append("</li>");
                continue;
            }

            // 有序列表项（1. item）
            Matcher olMatcher = Pattern.compile("^\\d+\\.\\s+(.+)$").matcher(trimmed);
            if (olMatcher.matches()) {
                if (!inOrderedList) {
                    html.append("<ol>");
                    inOrderedList = true;
                }
                html.append("<li>").append(processInline(olMatcher.group(1))).append("</li>");
                continue;
            }

            // 关闭未结束的列表
            if (inList) {
                html.append("</ul>");
                inList = false;
            }
            if (inOrderedList) {
                html.append("</ol>");
                inOrderedList = false;
            }

            // 普通段落
            html.append("<p>").append(processInline(trimmed)).append("</p>");
        }

        // 结尾时关闭未结束的列表
        if (inList) html.append("</ul>");
        if (inOrderedList) html.append("</ol>");

        String result = html.toString();
        return result.isEmpty() ? "<p></p>" : result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 处理代码块（```lang ... ```）：转换为 <pre><code> 标签。
     */
    private static String processCodeBlocks(String text) {
        // 将 ```lang\n...\n``` 转换为 <pre><code>...</code></pre>
        Pattern codeBlock = Pattern.compile("```[a-zA-Z]*\\n([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher matcher = codeBlock.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String code = escapeHtml(matcher.group(1).trim());
            matcher.appendReplacement(sb, "<pre><code>" + Matcher.quoteReplacement(code) + "</code></pre>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 处理行内 Markdown 语法（粗体、斜体、删除线、行内代码、链接、图片）。
     */
    private static String processInline(String line) {
        String result = escapeHtml(line);

        // 图片 ![alt](url) → <img>（公众号场景一般忽略，但保留标记）
        result = result.replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)", "<img src=\"$2\" alt=\"$1\" />");

        // 链接 [text](url) → <a>
        result = result.replaceAll("\\[([^\\]]*)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");

        // 行内代码 `code` → <code>
        result = result.replaceAll("`([^`]+)`", "<code>$1</code>");

        // 粗斜体 ***text*** → <strong><em>
        result = result.replaceAll("\\*{3}(.+?)\\*{3}", "<strong><em>$1</em></strong>");

        // 粗体 **text** → <strong>
        result = result.replaceAll("\\*{2}(.+?)\\*{2}", "<strong>$1</strong>");

        // 斜体 *text* → <em>
        result = result.replaceAll("(?<![\\w*])\\*([^*]+?)\\*(?![\\w*])", "<em>$1</em>");

        // 删除线 ~~text~~ → <del>
        result = result.replaceAll("~~(.+?)~~", "<del>$1</del>");

        return result;
    }

    /**
     * HTML 特殊字符转义（&、<、>、"）。
     * 注意：只转义基础字符，避免破坏 Markdown 标记的正则匹配。
     * 由于 processInline 在转义后执行正则，这里只转义 & 和纯文本中的 < >。
     */
    private static String escapeHtml(String text) {
        // 注意顺序：先转义 &，再转义 < > "
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
