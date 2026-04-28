package com.socialflow.service.note.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Jupyter Notebook → Markdown
 *
 * markdown cells 直接拼；code cells 用 ```{language} 包裹；
 * 输出（execution_count, outputs）暂时丢弃，避免噪音。
 */
@Component
public class IpynbParser implements NoteParser {

    private static final ObjectMapper M = new ObjectMapper();

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("ipynb");
    }

    @Override
    public ParsedNote parse(String fileName, InputStream in) throws IOException {
        JsonNode root = M.readTree(in);
        String lang = root.path("metadata").path("kernelspec").path("language").asText("python");

        StringBuilder md = new StringBuilder();
        JsonNode cells = root.path("cells");
        if (cells.isArray()) {
            for (JsonNode cell : cells) {
                String type = cell.path("cell_type").asText();
                String src = joinSource(cell.path("source"));
                if ("markdown".equals(type)) {
                    md.append(src).append("\n\n");
                } else if ("code".equals(type) && !src.isBlank()) {
                    md.append("```").append(lang).append('\n')
                      .append(src).append("\n```\n\n");
                }
            }
        }
        return ParsedNote.builder()
                .title(MarkdownParser.stripExt(fileName))
                .contentMd(md.toString().trim())
                .build();
    }

    /** ipynb 的 source 可以是字符串或字符串数组 */
    private String joinSource(JsonNode src) {
        if (src.isTextual()) return src.asText();
        if (src.isArray()) {
            StringBuilder s = new StringBuilder();
            for (JsonNode n : src) s.append(n.asText());
            return s.toString();
        }
        return "";
    }
}
