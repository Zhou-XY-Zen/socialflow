package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 导入子项 VO —— 审阅页右栏每行
 *
 * parsedMd 仅在审阅页"原文预览"时才需要，列表场景不返回。
 */
@Data
public class NoteImportItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long taskId;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String parseStatus;

    private String enrichStatus;

    private String parsedTitle;

    /** 仅审阅页详情接口返回 */
    private String parsedMd;

    /** AI 富化产物 JSON（前端 JSON.parse） */
    private String aiPayload;

    private Long conflictWithNoteId;

    private String conflictWithNoteTitle;

    private String resolution;

    private Long finalNoteId;

    private String errorMsg;
}
