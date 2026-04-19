package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 仓库书签 —— 对应 `repo_bookmark` 表
 *
 * 用户常用仓库收藏，方便一键再分析。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repo_bookmark")
public class RepoBookmark extends BaseEntity {

    private Long userId;
    private String nickname;
    private String gitUrl;
    private String branch;
    private String tags;
    private LocalDateTime lastAnalyzedAt;
    private Integer lastScore;
}
