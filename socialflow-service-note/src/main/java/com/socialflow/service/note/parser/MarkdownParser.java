package com.socialflow.service.note.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 直传 + Front Matter 抽取
 *
 * 优先级：YAML Front Matter title > 第一行 H1 > 文件名（去扩展名）
 */
@Component
public class MarkdownParser implements NoteParser {

    private static final Pattern FRONT_MATTER =
            Pattern.compile("^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n", Pattern.MULTILINE);
    private static final Pattern FM_TITLE =
            Pattern.compile("^title\\s*:\\s*['\"]?([^'\"\\n]+)['\"]?\\s*$", Pattern.MULTILINE);
    private static final Pattern H1 =
            Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("md", "markdown", "mdown");
    }

    @Override
    public ParsedNote parse(String fileName, InputStream in) throws IOException {
        String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        String body = raw;
        String title = null;

        Matcher fm = FRONT_MATTER.matcher(raw);
        if (fm.find()) {
            String fmBlock = fm.group(1);
            body = raw.substring(fm.end());
            Matcher t = FM_TITLE.matcher(fmBlock);
            if (t.find()) title = t.group(1).trim();
        }
        if (title == null) {
            Matcher h1 = H1.matcher(body);
            if (h1.find()) title = h1.group(1).trim();
        }
        if (title == null) {
            title = stripExt(fileName);
        }

        return ParsedNote.builder().title(title).contentMd(body).build();
    }

    static String stripExt(String fileName) {
        if (fileName == null) return "未命名";
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String name = slash < 0 ? fileName : fileName.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
