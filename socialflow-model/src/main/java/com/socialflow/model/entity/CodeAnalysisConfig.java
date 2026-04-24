package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 代码分析用户级 LLM 配置（code_analysis_config 表）。
 *
 * 每用户一条（unique(user_id)），优先级高于 application.yml 的 socialflow.code-analysis.*。
 * 未配置时 Service 回退到 yml 默认值，不会抛错。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("code_analysis_config")
public class CodeAnalysisConfig extends BaseEntity {

    private Long userId;

    /** Provider 大写字符串：DEEPSEEK / QWEN / GLM / OPENAI / CLAUDE */
    private String provider;

    /** 模型名：deepseek-v4-pro / deepseek-v4-flash / qwen-plus / ... */
    private String model;

    /** 采样温度 0.00-1.00，默认 0.30 */
    private BigDecimal temperature;
}
