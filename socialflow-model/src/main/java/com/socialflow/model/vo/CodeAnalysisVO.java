package com.socialflow.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 代码分析结果 VO —— 返回给前端的完整结果。
 *
 * 项目概览只填 summaryMd / techStack / languageStats / mermaid；
 * 提交审查还会填 overallScore + findings + 计数。
 */
@Data
@Schema(description = "代码分析结果")
public class CodeAnalysisVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String gitUrl;
    private String branch;
    private String commitSha;
    private String baseRef;
    private String headRef;

    @Schema(description = "PROJECT_OVERVIEW / COMMIT_REVIEW / DIFF_REVIEW")
    private String analysisType;

    @Schema(description = "PENDING / RUNNING / SUCCESS / FAILED")
    private String status;

    private String stage;
    private Integer progressPercent;
    private String progressMessage;

    @Schema(description = "0-100 综合评分")
    private Integer overallScore;

    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;

    @Schema(description = "Markdown 总结")
    private String summaryMd;

    @Schema(description = "技术栈数组")
    private List<String> techStack;

    @Schema(description = "语言行数占比")
    private List<LanguageStatVO> languageStats;

    @Schema(description = "流程图 Mermaid 代码")
    private String mermaidCode;

    @Schema(description = "审查发现列表")
    private List<CodeFindingVO> findings;

    private Integer isFavorite;
    private String shareToken;
    private String tags;

    private String errorMsg;
    private Long durationMs;
    private Integer llmTokensUsed;

    private LocalDateTime createTime;
}
