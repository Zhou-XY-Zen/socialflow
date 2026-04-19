package com.socialflow.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 仓库提交摘要 —— 供前端选择要审查的 commit。
 */
@Data
@Schema(description = "仓库提交摘要")
public class RepoCommitVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String sha;
    private String shortSha;
    private String author;
    private String email;
    private LocalDateTime commitTime;
    private String subject;
    private Integer changedFiles;
    private Integer additions;
    private Integer deletions;
}
