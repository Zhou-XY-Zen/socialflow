package com.socialflow.service.note.parser;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析器统一产出
 */
@Data
@Builder
public class ParsedNote {

    /** 解析推断的标题（来自 H1 / Front Matter / 文件名） */
    private String title;

    /** Markdown 正文 */
    private String contentMd;

    /** 解析期间的非致命警告（图片解析失败等），最终入审阅页提示 */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public void warn(String msg) {
        if (warnings == null) warnings = new ArrayList<>();
        warnings.add(msg);
    }
}
