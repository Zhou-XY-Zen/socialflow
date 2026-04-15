package com.socialflow.service.dashboard;

import com.socialflow.model.vo.DashboardOverviewVO;

import java.util.List;
import java.util.Map;

/**
 * Dashboard 数据聚合服务（Wave 3.2）。
 *
 * <p>修复审计发现的"数据有但无端点"问题：{@code ai_usage_log} / {@code publish_task} /
 * {@code eval_result} 表都已经在写数据，但前端没有任何聚合 API 可用。</p>
 */
public interface DashboardService {

    /**
     * 当前用户的总览：内容/发布/AI 用量/KB/媒体素材计数 + 近 7 天趋势。
     */
    DashboardOverviewVO overview(Long userId);

    /**
     * AI 用量明细按维度聚合。
     * @param userId 当前用户
     * @param dim    分组维度：provider / model / requestType
     * @param days   时间窗口（天数），如 7 / 30
     * @return [{key, calls, tokens, cost}, ...] 按 calls 倒序
     */
    List<Map<String, Object>> aiUsage(Long userId, String dim, int days);

    /**
     * 发布趋势按天聚合。
     * @return [{date, total, success, failed}, ...]
     */
    List<Map<String, Object>> publishTrends(Long userId, int days);

    /**
     * 成本明细按 provider 聚合（近 N 天）。
     * @return [{provider, totalTokens, totalCost}, ...]
     */
    List<Map<String, Object>> cost(Long userId, int days);
}
