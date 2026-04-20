package com.socialflow.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新单条 finding 状态 —— 用户在审查结果页标注"已修复 / 忽略 / 待跟进"。
 */
@Data
@Schema(description = "Finding 状态更新")
public class FindingStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Schema(description = "UNRESOLVED / RESOLVED / IGNORED", allowableValues = {"UNRESOLVED", "RESOLVED", "IGNORED"})
    private String status;

    @Schema(description = "用户备注")
    private String resolutionNote;

    @Schema(description = "关闭原因（Wave 8）：INVALID 误判 / ALREADY_FIXED 已修复 / NOT_APPLICABLE 不适用 / OTHER",
            allowableValues = {"INVALID", "ALREADY_FIXED", "NOT_APPLICABLE", "OTHER"})
    private String dismissedReason;
}
