package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 笔记历史版本 —— 仅在自动保存 / 冲突合并 / commit 入库时插入新行，从不更新
 */
@Data
@TableName("note_version")
public class NoteVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID)
    private Long id;

    private Long noteId;

    private Integer version;

    private String title;

    private String contentMd;

    private String changeSummary;

    private Long sourceImportItemId;

    private LocalDateTime createTime;
}
