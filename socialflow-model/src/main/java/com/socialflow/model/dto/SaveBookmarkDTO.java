package com.socialflow.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 添加/更新仓库书签请求。
 */
@Data
@Schema(description = "仓库书签")
public class SaveBookmarkDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "书签 ID；新增时留空")
    private Long id;

    @NotBlank
    @Schema(description = "自定义昵称")
    private String nickname;

    @NotBlank
    @Schema(description = "Git 仓库 URL")
    private String gitUrl;

    @Schema(description = "默认分支")
    private String branch;

    @Schema(description = "标签（逗号分隔）")
    private String tags;
}
