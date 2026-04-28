package com.socialflow.service.note.parser;

import com.socialflow.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按扩展名路由到对应解析器
 */
@Component
public class NoteParserRegistry {

    private final Map<String, NoteParser> byExt = new HashMap<>();

    public NoteParserRegistry(List<NoteParser> parsers) {
        for (NoteParser p : parsers) {
            for (String ext : p.supportedExtensions()) {
                byExt.put(ext.toLowerCase(Locale.ROOT), p);
            }
        }
    }

    public NoteParser pick(String fileName) {
        String ext = extOf(fileName);
        NoteParser p = byExt.get(ext);
        if (p == null) {
            throw new BusinessException("暂不支持该文件类型：." + ext);
        }
        return p;
    }

    public boolean supports(String fileName) {
        return byExt.containsKey(extOf(fileName));
    }

    private String extOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
