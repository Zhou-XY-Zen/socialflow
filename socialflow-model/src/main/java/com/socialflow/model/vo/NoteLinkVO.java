package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 笔记链接 VO（图谱边）
 *
 * 比 NoteLink 实体多两端的标题，用于前端渲染时不再回查
 */
@Data
public class NoteLinkVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long srcNoteId;
    private String srcTitle;
    private Long dstNoteId;
    private String dstTitle;
    private String linkType;     // explicit / semantic
    private Float similarity;
}
