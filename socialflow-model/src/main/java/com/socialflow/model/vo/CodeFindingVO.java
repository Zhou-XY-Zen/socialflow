package com.socialflow.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单条审查发现 VO。
 */
@Data
@Schema(description = "代码审查发现项")
public class CodeFindingVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long analysisId;

    @Schema(description = "HIGH / MEDIUM / LOW")
    private String level;

    @Schema(description = "规则类别")
    private String category;

    private String title;
    private String file;
    private String lineRange;

    private String description;
    private String suggestion;
    private String codeSnippet;

    @Schema(description = "引用的阿里规约条款")
    private String ruleRef;

    @Schema(description = "UNRESOLVED / RESOLVED / IGNORED")
    private String status;

    private String resolutionNote;
}
