package com.socialflow.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Git 仓库凭证 VO —— 返回给前端时 **token 只返回掩码**，绝不回传明文。
 */
@Data
@Schema(description = "Git 仓库凭证")
public class RepoAuthCredentialVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String nickname;
    private String gitHost;
    private String username;

    /** 掩码：ghp_****f8a；前端永远拿不到明文 */
    private String tokenHint;

    private Integer isDefault;
    private String testStatus;
    private String testMessage;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createTime;
}
