package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码审查发现明细 —— 对应 `repo_analysis_finding` 表
 *
 * 一条 finding = 一个"问题 + 建议"记录。拆出成独立表便于：
 *   1. 用户按单条标注"已修复/忽略/待跟进"
 *   2. 按级别/类别分组统计
 *   3. 后续可跨分析溯源"同一个仓库某条规则历次命中"
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repo_analysis_finding")
public class RepoAnalysisFinding extends BaseEntity {

    public static final String STATUS_UNRESOLVED = "UNRESOLVED";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_IGNORED = "IGNORED";

    public static final String REASON_INVALID = "INVALID";
    public static final String REASON_ALREADY_FIXED = "ALREADY_FIXED";
    public static final String REASON_NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String REASON_OTHER = "OTHER";

    private Long analysisId;

    /** HIGH / MEDIUM / LOW */
    private String level;

    /** 规则类别：安全 / 并发 / 命名 / SQL / 异常 / ... */
    private String category;

    private String title;
    private String file;
    private String lineRange;

    private String description;
    private String suggestion;
    private String codeSnippet;

    /** 引用阿里开发手册条款 */
    private String ruleRef;

    /** UNRESOLVED / RESOLVED / IGNORED */
    private String status;

    private String resolutionNote;

    /**
     * 关闭原因（Wave 8）：
     *   INVALID         AI 误判
     *   ALREADY_FIXED   代码已修复
     *   NOT_APPLICABLE  本项目不适用
     *   OTHER           其他
     * 与 status=IGNORED/RESOLVED 配合用，仅 status != UNRESOLVED 时填值。
     */
    private String dismissedReason;
}
