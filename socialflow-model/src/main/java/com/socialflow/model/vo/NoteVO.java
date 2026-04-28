package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 笔记 VO —— 列表 + 详情共用。
 *
 * 列表查询时 contentMd 不返回（节省带宽）；详情查询时才填充。
 */
@Data
public class NoteVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String summary;

    /** 详情接口才返回 */
    private String contentMd;

    private Long categoryId;

    private String categoryName;

    private Integer wordCount;

    private Integer isPinned;

    private Integer isPublic;

    private String slug;

    private Integer status;

    private String sourceType;

    private LocalDateTime publishedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
