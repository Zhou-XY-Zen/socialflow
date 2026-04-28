package com.socialflow.service.note.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 文件解析器统一接口
 *
 * 实现类按"支持的扩展名 + MIME"声明能力，由 NoteParserRegistry 路由。
 */
public interface NoteParser {

    /** 支持的小写扩展名集合（不含点） */
    Set<String> supportedExtensions();

    /**
     * @param fileName 原始文件名（含扩展名，用作 fallback 标题）
     * @param in       字节流（调用方负责关闭）
     */
    ParsedNote parse(String fileName, InputStream in) throws IOException;
}
