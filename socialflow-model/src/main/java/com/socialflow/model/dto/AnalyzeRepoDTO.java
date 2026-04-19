package com.socialflow.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 代码分析通用请求 DTO —— 覆盖项目概览 / 提交审查 / 对比分析三种场景。
 *
 * 约定：
 *   - 项目概览：仅 gitUrl + 可选 branch
 *   - 提交审查：gitUrl + commitSha（branch 可选）
 *   - 对比分析：gitUrl + baseRef + headRef
 */
@Data
@Schema(description = "代码分析请求")
public class AnalyzeRepoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "git 仓库地址不能为空")
    @Schema(description = "Git 仓库 URL（HTTPS 或 SSH）", example = "https://github.com/user/repo.git")
    private String gitUrl;

    @Schema(description = "分支名，默认 main", example = "main")
    private String branch;

    @Schema(description = "提交审查必填：完整 40 位 SHA 或前缀")
    private String commitSha;

    @Schema(description = "对比分析 base 引用（commit sha 或分支名）")
    private String baseRef;

    @Schema(description = "对比分析 head 引用")
    private String headRef;

    @Schema(description = "克隆深度；提交审查默认 50；项目概览默认 1")
    private Integer cloneDepth;

    @Schema(description = "排除目录（相对路径），默认排除 node_modules/target/dist/.git")
    private java.util.List<String> excludeDirs;

    @Schema(description = "最大分析文件数，默认 200")
    private Integer maxFiles;
}
