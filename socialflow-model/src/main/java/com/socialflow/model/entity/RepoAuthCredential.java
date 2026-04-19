package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Git 仓库访问凭证 —— 对应 `repo_auth_credential` 表。
 *
 * 每条凭证绑定一个 {userId + gitHost}，克隆仓库时按 URL 的 host 匹配，
 * 同 host 下可以有多条，优先用 isDefault=1 的那条；没 default 则让用户选。
 *
 * token 字段持久化的是 AES-256-GCM 加密密文，运行时临时解密使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repo_auth_credential")
public class RepoAuthCredential extends BaseEntity {

    private Long userId;

    /** 自定义昵称，如 "我的 GitHub 个人" / "公司 GitLab" */
    private String nickname;

    /** Git Host：github.com / gitee.com / gitlab.company.com / 10.0.0.5:3000 */
    private String gitHost;

    /** 认证方式：TOKEN（个人访问令牌，推荐）/ PASSWORD（账号密码） */
    private String authType;

    private String username;

    /** AES-256-GCM 加密后的 Base64 串 */
    private String tokenEncrypted;

    /** 展示用的掩码，如 ghp_****f8a */
    private String tokenHint;

    /** 同一 host 下的默认凭证：0=非默认 / 1=默认 */
    private Integer isDefault;

    private LocalDateTime lastUsedAt;

    /** 连接测试状态：UNKNOWN / SUCCESS / FAILED */
    private String testStatus;

    private String testMessage;
}
