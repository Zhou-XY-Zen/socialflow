package com.socialflow.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增 / 更新 Git 仓库凭证请求。
 *
 * token 为空时表示不修改已有 token（用于编辑场景，避免密文往返泄漏）。
 */
@Data
@Schema(description = "保存 Git 仓库凭证")
public class SaveCredentialDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 编辑时传；新增留空 */
    private Long id;

    @NotBlank(message = "昵称不能为空")
    private String nickname;

    @Schema(description = "Git Host；若留空后端会从 defaultRepoUrl 自动提取。例 github.com / gitee.com / gitlab.company.com")
    private String gitHost;

    @Schema(description = "认证方式：TOKEN（推荐）/ PASSWORD；留空默认 TOKEN",
            allowableValues = {"TOKEN", "PASSWORD"})
    private String authType;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "Token 或密码。编辑时留空代表不修改")
    private String token;

    @Schema(description = "常用仓库完整 URL（可选）。用于精准测试连接 + 前端快速发起分析",
            example = "https://gitlab.company.com/team/backend.git")
    private String defaultRepoUrl;

    /** 同 host 下是否设为默认 */
    private Integer isDefault;
}
