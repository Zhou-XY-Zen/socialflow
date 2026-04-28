package com.socialflow.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审阅页修改单个 import_item 的入库设置
 */
@Data
public class NoteImportItemUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String parsedTitle;

    private Long categoryId;

    private Integer isPublic;

    /** skip / create / overwrite / merge */
    private String resolution;
}
