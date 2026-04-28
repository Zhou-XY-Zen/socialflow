package com.socialflow.service.note.parser;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * docx / pdf / rtf / odt 等 → Markdown（经过 plain text 中转）
 *
 * 暂时只抽纯文本（Tika 默认给的就是纯文本）。后续若要保留格式，可以换 docx4j /
 * pdfbox 自己写转换；现阶段优先把内容拿到，富化阶段 AI 会重新组织结构。
 */
@Component
public class TikaParser implements NoteParser {

    private static final int MAX_CHARS = 5_000_000;

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("docx", "doc", "pdf", "rtf", "odt", "epub");
    }

    @Override
    public ParsedNote parse(String fileName, InputStream in) throws IOException {
        BodyContentHandler handler = new BodyContentHandler(MAX_CHARS);
        Metadata meta = new Metadata();
        try {
            new AutoDetectParser().parse(in, handler, meta, new ParseContext());
        } catch (SAXException | TikaException e) {
            throw new IOException("Tika 解析失败：" + e.getMessage(), e);
        }
        String text = handler.toString();
        String title = meta.get(TikaCoreProperties.TITLE);
        if (title == null || title.isBlank()) {
            title = MarkdownParser.stripExt(fileName);
        }
        // 简单段落归一：>=2 个换行视作段落分隔
        String md = text.replaceAll("[\\t\\x0B\\f\\r]+", " ")
                        .replaceAll("\\n{3,}", "\n\n")
                        .trim();
        return ParsedNote.builder().title(title).contentMd(md).build();
    }
}
