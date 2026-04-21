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

    /**
     * 用户自定义分析诉求 / 重点关注方向 —— 可选。
     * 若提供，LLM 会在默认报告之上深度覆盖这些诉求，追求"读完不用看源码就能懂"的详尽度。
     * 长度无硬性限制，但过长会线性增加 prompt token 消耗；服务端做 8000 字符软截断。
     * 示例：
     *   "重点说明评估中心的打分算法，以及内容如何经过多 Agent 协作生成"
     *   "我想把这个项目接入公司系统，请列出所有关键扩展点和接口契约"
     *   "详细分析缓存降级、限流和熔断是怎么做的"
     */
    @Schema(description = "自定义分析诉求，越详细越好；为空时走默认模板")
    private String userRequirements;
}
