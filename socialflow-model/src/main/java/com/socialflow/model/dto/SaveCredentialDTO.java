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

    @NotBlank(message = "Git host 不能为空")
    @Schema(description = "Git Host，例 github.com / gitee.com / gitlab.company.com")
    private String gitHost;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "Personal Access Token / 密码。编辑时留空代表不修改")
    private String token;

    /** 同 host 下是否设为默认 */
    private Integer isDefault;
}
