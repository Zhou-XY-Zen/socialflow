package com.socialflow.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 语言行数占比（项目概览用）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LanguageStatVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String language;
    private Integer fileCount;
    private Long totalLines;
    /** 占总代码行数的百分比（0-100） */
    private Double percent;
}
