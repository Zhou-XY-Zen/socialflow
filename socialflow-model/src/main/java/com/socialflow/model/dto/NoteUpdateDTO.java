package com.socialflow.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 全部字段都可选 —— 部分更新（PATCH 语义）
 */
@Data
public class NoteUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 200)
    private String title;

    private String contentMd;

    private String summary;

    private Long categoryId;

    private Integer isPinned;

    private Integer isPublic;

    private Integer status;
}
