package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class NoteCategoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long parentId;

    private String name;

    private Integer sortOrder;

    private String color;

    /** 该分类下笔记数（实时统计） */
    private Integer noteCount;

    /** 子分类，前端树形展示时填充 */
    private List<NoteCategoryVO> children;
}
