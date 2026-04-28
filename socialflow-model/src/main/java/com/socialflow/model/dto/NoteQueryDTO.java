package com.socialflow.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 笔记列表查询条件
 */
@Data
public class NoteQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 标题/正文模糊搜索 */
    private String keyword;

    private Long categoryId;

    /** true 表示只返回 category_id IS NULL 的笔记（"未分类"侧栏按钮用）；
     *  与 categoryId 互斥，同时传时 categoryId 优先 */
    private Boolean uncategorizedOnly;

    /** 默认 1=正常；前端切换"回收站"传 3 */
    private Integer status;

    /** updated / created / pinned-first */
    private String sortBy;

    private Long pageNum;

    private Long pageSize;
}
