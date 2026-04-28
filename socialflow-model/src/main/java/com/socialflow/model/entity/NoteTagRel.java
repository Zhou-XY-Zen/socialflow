package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 笔记 ↔ 标签关联表（复合主键，不继承 BaseEntity）
 */
@Data
@TableName("note_tag_rel")
public class NoteTagRel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long noteId;

    private Long tagId;

    private LocalDateTime createTime;
}
