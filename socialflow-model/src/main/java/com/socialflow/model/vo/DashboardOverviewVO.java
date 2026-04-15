package com.socialflow.model.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 概览 VO（Wave 3.2）—— 聚合内容 / 发布 / AI 用量等核心指标。
 */
@Data
@Builder
public class DashboardOverviewVO {

    /** 内容按状态统计：DRAFT / SCHEDULED / PUBLISHED 等 → 数量 */
    private Map<String, Long> contentByStatus;

    /** 当前用户内容总数 */
    private Long contentTotal;

    /** 发布任务按状态统计：PENDING / EXECUTING / SUCCESS / FAILED / FAILED_PERMANENT / CANCELLED */
    private Map<String, Long> publishByStatus;

    /** 待执行的定时发布任务数（status=PENDING + publish_type=SCHEDULED） */
    private Long scheduledPending;

    /** 近 7 天 AI 调用次数 */
    private Long aiCalls7d;

    /** 近 7 天 AI 累计 token 消耗 */
    private Long aiTokens7d;

    /** 近 7 天 AI 累计成本预估（美元） */
    private BigDecimal aiCost7d;

    /** 近 7 天每天的 AI 调用趋势：[{date, calls, tokens, cost}, ...] */
    private List<Map<String, Object>> aiUsageTrend;

    /** 知识库总数 */
    private Long kbTotal;

    /** 媒体素材总数 */
    private Long mediaTotal;
}
