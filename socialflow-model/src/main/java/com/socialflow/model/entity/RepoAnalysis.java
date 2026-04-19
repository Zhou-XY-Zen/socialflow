package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码分析主记录 —— 对应 `repo_analysis` 表
 *
 * 承载三种分析类型：
 *   - PROJECT_OVERVIEW：根据整个仓库生成项目介绍/结构分析
 *   - COMMIT_REVIEW   ：针对某一次提交做代码审查
 *   - DIFF_REVIEW     ：两个 ref 之间的对比审查
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repo_analysis")
public class RepoAnalysis extends BaseEntity {

    private Long userId;
    private String gitUrl;
    private String branch;
    private String commitSha;
    private String baseRef;
    private String headRef;

    /** PROJECT_OVERVIEW / COMMIT_REVIEW / DIFF_REVIEW */
    private String analysisType;

    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    private String stage;
    private Integer progressPercent;
    private String progressMessage;

    private Integer overallScore;
    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;

    private String summaryMd;
    private String techStackJson;
    private String languageStatsJson;
    private String mermaidCode;

    private Integer isFavorite;
    private String shareToken;
    private String tags;

    private String errorMsg;
    private Long durationMs;
    private Integer llmTokensUsed;
}
