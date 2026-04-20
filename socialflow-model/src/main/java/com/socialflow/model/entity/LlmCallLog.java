package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * LLM 调用明细日志。
 *
 * 一次分析可能涉及多次 LLM 调用（分模块审阅 + 分文件审查 + 最终汇总），
 * 每次调用都落一条 log，便于分析详情页展开链路 + 仪表盘聚合统计。
 *
 * 黄山版 5.1.5：表必备 id / create_time / update_time / is_deleted 四字段，本类已对齐。
 * 软删除（is_deleted=1）一般用于人工误录或回收期清理；正常日志不删。
 */
@Data
@TableName("llm_call_log")
public class LlmCallLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long analysisId;
    private Long userId;

    /** 阶段标识，如 MODULE_SUMMARY_socialflow-service / FILE_REVIEW_UserService.java / FINAL */
    private String stage;
    /** 人类可读标签 */
    private String stageLabel;

    private String provider;
    private String model;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    private Long latencyMs;

    private Integer success;
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 黄山版 5.1.5：表必备 update_time */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 黄山版 5.1.5：表必备 is_deleted（逻辑删除：0 未删 / 1 已删） */
    @TableLogic
    private Integer isDeleted;
}
