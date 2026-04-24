package com.socialflow.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 代码分析用户级 LLM 配置 DTO —— PUT /api/v1/code-analysis/config/llm 的请求体。
 */
@Data
public class CodeAnalysisConfigDTO {

    @NotBlank(message = "provider 不能为空")
    @Pattern(regexp = "^(DEEPSEEK|QWEN|GLM|OPENAI|CLAUDE)$",
             message = "provider 只能是 DEEPSEEK / QWEN / GLM / OPENAI / CLAUDE")
    private String provider;

    @NotBlank(message = "model 不能为空")
    private String model;

    @DecimalMin(value = "0.00", message = "temperature 不能小于 0")
    @DecimalMax(value = "1.00", message = "temperature 不能大于 1")
    private BigDecimal temperature;
}
