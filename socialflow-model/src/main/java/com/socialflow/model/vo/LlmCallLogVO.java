package com.socialflow.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * LLM 调用日志展示 VO。
 */
@Data
@Schema(description = "LLM 调用明细")
public class LlmCallLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long analysisId;
    private String stage;
    private String stageLabel;
    private String provider;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long latencyMs;
    private Integer success;
    private String errorMsg;
    private LocalDateTime createTime;
}
