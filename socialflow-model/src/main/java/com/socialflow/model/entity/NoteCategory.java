package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 笔记分类（最多两级，parent_id=NULL 即顶级）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("note_category")
public class NoteCategory extends BaseEntity {

    private Long userId;

    private Long parentId;

    private String name;

    private Integer sortOrder;

    private String color;
}
