package com.socialflow.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 内容两个版本之间的差异视图 —— 给前端"版本对比"页面使用。
 *
 * <p>不返回完整的 unified diff，只返回字段级别的 before/after。
 * 前端可以选择简单红绿展示，也可以接入 diff-match-patch 之类的库做行级 diff。</p>
 */
@Data
@Builder
public class ContentVersionDiffVO {

    /** 内容 ID */
    private Long contentId;

    /** 较早的版本号（base） */
    private Integer fromVersion;

    /** 较晚的版本号（head） */
    private Integer toVersion;

    /** 字段级差异列表 —— 仅包含发生了变化的字段 */
    private List<FieldDiff> changes;

    @Data
    @Builder
    public static class FieldDiff {
        /** 字段名，例如 {@code "title"} / {@code "body"} / {@code "tags"} */
        private String field;

        /** 较早版本的值（可能为 null） */
        private String before;

        /** 较晚版本的值（可能为 null） */
        private String after;

        /** 字符长度增量：after.length - before.length（null 视为 0），便于前端展示 +N/-N */
        private Integer charsDelta;
    }
}
