package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 凭证下挂的 Git 仓库 —— 对应 `repo_credential_project` 表。
 * 一个凭证可以有多个项目；父级 {@link RepoAuthCredential}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repo_credential_project")
public class RepoCredentialProject extends BaseEntity {

    private Long credentialId;
    private String nickname;
    private String gitUrl;
    private String branch;
    private LocalDateTime lastUsedAt;
}
