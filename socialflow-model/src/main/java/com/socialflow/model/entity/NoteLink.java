package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 笔记图谱边
 *
 * link_type:
 *   - explicit  ：用户在 Markdown 里写 [[标题]] 显式建立
 *   - semantic  ：导入/编辑时由 embedding 余弦相似度 > 阈值自动建立
 */
@Data
@TableName("note_link")
public class NoteLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long srcNoteId;

    private Long dstNoteId;

    private String linkType;

    private Float similarity;

    private LocalDateTime createTime;
}
