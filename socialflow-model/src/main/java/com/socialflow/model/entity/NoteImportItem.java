package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导入子项 —— task 下每个文件一行
 *
 * parsed_md 在 commit 后会被清空（避免膨胀）；ai_payload JSON 中保留富化产物
 * 以便用户事后回顾"AI 当时给我的标签是啥"。
 */
@Data
@TableName(value = "note_import_item", autoResultMap = true)
public class NoteImportItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private Long userId;

    private String filePath;

    private String fileName;

    private Long fileSize;

    private String fileHash;

    private String contentHash;

    private String parseStatus;

    private String enrichStatus;

    private String parsedMd;

    private String parsedTitle;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private String aiPayload;

    private Long conflictWithNoteId;

    private String resolution;

    private Long finalNoteId;

    private String errorMsg;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
