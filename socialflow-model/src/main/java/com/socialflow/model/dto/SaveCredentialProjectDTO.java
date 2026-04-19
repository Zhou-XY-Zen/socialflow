package com.socialflow.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 在某凭证下新增/更新一个仓库项目。
 */
@Data
@Schema(description = "凭证下的仓库项目")
public class SaveCredentialProjectDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 编辑时必填 */
    private Long id;

    @Schema(description = "仓库昵称，可留空默认取 URL 末尾仓库名")
    private String nickname;

    @NotBlank(message = "Git URL 不能为空")
    @Schema(description = "完整 Git 仓库 URL", example = "https://github.com/user/repo.git")
    private String gitUrl;

    @Schema(description = "默认分支", example = "main")
    private String branch;
}
