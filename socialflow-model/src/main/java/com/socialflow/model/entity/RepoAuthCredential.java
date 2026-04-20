package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Git 仓库访问凭证 —— 对应 `repo_auth_credential` 表。
 *
 * 每条凭证绑定一个 {userId + gitHost}，克隆仓库时按 URL 的 host 匹配，
 * 同 host 下可以有多条，优先用 isDefault=1 的那条；没 default 则让用户选。
 *
 * 【敏感字段处理】
 *   - tokenEncrypted：持久化的是 AES-256-GCM 密文；加 @JsonIgnore + @ToString.Exclude
 *     避免经 Spring 序列化回前端或被日志框架打印。
 *   - plainToken：运行时解密后的明文，仅在内存中流转，带 @TableField(exist=false)
 *     确保 MyBatis-Plus 不把它写库；同样 @JsonIgnore + @ToString.Exclude。
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

    /** AES-256-GCM 加密后的 Base64 串；永不输出到 JSON/日志 */
    @JsonIgnore
    @ToString.Exclude
    private String tokenEncrypted;

    /**
     * 运行时解密后的明文 token，仅内存使用、不落库、不序列化。
     * 被 {@code CredentialService#resolveForUrl} 填充，供 GitRepoService 克隆瞬间读取后应尽快丢弃。
     */
    @TableField(exist = false)
    @JsonIgnore
    @ToString.Exclude
    private transient String plainToken;

    /** 展示用的掩码，如 ghp_****f8a */
    private String tokenHint;

    /** 常用仓库 URL（可选）：测试连接用它做 ls-remote，前端快速触发分析也用它 */
    private String defaultRepoUrl;

    /** 同一 host 下的默认凭证：0=非默认 / 1=默认 */
    private Integer isDefault;

    private LocalDateTime lastUsedAt;

    /** 连接测试状态：UNKNOWN / SUCCESS / FAILED */
    private String testStatus;

    private String testMessage;
}
