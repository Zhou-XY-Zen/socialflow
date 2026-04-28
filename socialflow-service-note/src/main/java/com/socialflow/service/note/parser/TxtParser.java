package com.socialflow.service.note.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 纯文本：内容原样塞进 contentMd（行内换行保留），标题用文件名
 */
@Component
public class TxtParser implements NoteParser {

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("txt", "text", "log");
    }

    @Override
    public ParsedNote parse(String fileName, InputStream in) throws IOException {
        String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return ParsedNote.builder()
                .title(MarkdownParser.stripExt(fileName))
                .contentMd(raw)
                .build();
    }
}
