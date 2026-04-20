package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 阿里巴巴《Java 开发手册（黄山版）》规约库 —— 对应 `rule_library` 表（V15 引入）
 *
 * 设计目标：
 *   1. 把内存 Holder 持久化到 DB，支持启停某条规约（enabled 字段）
 *   2. 允许用户自定义新规约（is_custom=1）
 *   3. 支持规约升级 —— 新版 PDF 抽取后只 upsert 变化条目
 *
 * 数据来源：
 *   启动时 RuleLibraryInitRunner 从 classpath rules/huangshan_rules.json upsert 进表，
 *   保留用户对 enabled 字段的修改不被覆盖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rule_library")
public class RuleLibraryItem extends BaseEntity {

    /** 规约编号，如 1.1.1 / 5.1.5，全局唯一 */
    private String code;

    /** 一级大类：编程规约/异常日志/单元测试/安全规约/MySQL数据库/工程结构/设计规约 */
    private String topCategory;

    /** 二级小节：命名风格/集合处理/...，可空（设计规约等无子节）*/
    private String subCategory;

    /** MANDATORY / RECOMMENDED / REFERENCE */
    private String level;

    /** 规约首句，UI 卡片显示 */
    private String title;

    /** 规约主体正文（不含说明/正反例） */
    private String body;

    /** 说明 */
    private String description;

    /** 正例 */
    private String exampleGood;

    /** 反例 */
    private String exampleBad;

    /** 启用：1 启用 / 0 禁用 */
    private Integer enabled;

    /** 是否用户自定义：0 黄山版 / 1 用户加 */
    private Integer isCustom;

    /** 来源：huangshan-1.7.1 / user-custom */
    private String source;
}
