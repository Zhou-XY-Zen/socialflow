package com.socialflow.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 凭证下的仓库项目 VO。
 */
@Data
@Schema(description = "凭证下的 Git 仓库")
public class RepoCredentialProjectVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long credentialId;
    private String nickname;
    private String gitUrl;
    private String branch;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createTime;
}
