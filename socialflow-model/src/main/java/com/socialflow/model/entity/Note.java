package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 笔记实体 —— 对应数据库表 {@code note}
 *
 * 知识中枢模块的核心实体。一条 note 既可以是用户手写的，
 * 也可以来自上传流水线（source_type=upload/url/clip）。
 *
 * 状态机：
 *   1=正常 → 用户在工作区可见
 *   2=草稿 → 编辑器自动保存中、未确认发布
 *   3=回收站 → 30 天后定时任务彻底删除
 *
 * 双向链接通过独立表 {@code note_link} 表达，本实体不冗余。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "note", autoResultMap = true)
public class Note extends BaseEntity {

    private Long userId;

    private String title;

    /** Markdown 原文，可能很大（MEDIUMTEXT），列表查询时记得 select 必要字段 */
    private String contentMd;

    private String summary;

    /** AI 抽取的大纲：JSON 数组，前端用于侧栏目录树。MyBatis-Plus 用 JsonTypeHandler 自动转 */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String aiOutline;

    private Long categoryId;

    private Integer wordCount;

    private Integer readScore;

    private Integer isPinned;

    private Integer isPublic;

    /** 公开博客的 URL slug，user_id + slug 唯一 */
    private String slug;

    /** 1=正常 2=草稿 3=回收站 */
    private Integer status;

    /** manual / upload / url / clip */
    private String sourceType;

    /** 来源引用：原文件名、URL、import_item_id */
    private String sourceRef;

    private LocalDateTime publishedAt;
}
