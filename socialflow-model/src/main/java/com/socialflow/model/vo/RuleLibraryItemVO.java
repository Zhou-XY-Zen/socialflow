package com.socialflow.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规约库前端 VO。
 *
 * 字段对齐 RuleLibraryItem，但 id 用 String 避免雪花 ID 在 JS 端精度丢失。
 */
@Data
public class RuleLibraryItemVO {

    private String id;          // BIGINT → String 防 JS 精度丢失
    private String code;
    private String topCategory;
    private String subCategory;
    private String level;
    private String title;
    private String body;
    private String description;
    private String exampleGood;
    private String exampleBad;
    private Integer enabled;
    private Integer isCustom;
    private String source;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updateTime;
}
