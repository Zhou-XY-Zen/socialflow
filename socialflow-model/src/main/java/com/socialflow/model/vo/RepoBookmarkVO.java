package com.socialflow.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 仓库书签 VO。
 */
@Data
@Schema(description = "仓库书签")
public class RepoBookmarkVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String nickname;
    private String gitUrl;
    private String branch;
    private String tags;
    private LocalDateTime lastAnalyzedAt;
    private Integer lastScore;
    private LocalDateTime createTime;
}
