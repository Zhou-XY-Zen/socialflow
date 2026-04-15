package com.socialflow.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.R;
import com.socialflow.model.vo.DashboardOverviewVO;
import com.socialflow.service.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Dashboard 控制器（Wave 3.2）—— 把已有但无端点的统计数据暴露给前端。
 *
 * <p>修复了审计发现的 GAP：{@code ai_usage_log} / {@code publish_task} 等表已经在写
 * 数据，但前端没法看。本 controller 提供 4 个聚合视角。</p>
 */
@Tag(name = "dashboard", description = "用户仪表盘聚合统计")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 总览：内容/发布/AI 用量/KB/媒体素材计数 + 近 7 天趋势。
     * <p>结果缓存 1min（CacheConfig 配置），多次刷新不重算。</p>
     */
    @Operation(summary = "dashboard overview")
    @GetMapping("/overview")
    public R<DashboardOverviewVO> overview() {
        return R.ok(dashboardService.overview(StpUtil.getLoginIdAsLong()));
    }

    /**
     * AI 用量明细分组。
     * @param dim provider | model | requestType（默认 provider）
     * @param days 时间窗口（默认 7）
     */
    @Operation(summary = "AI usage breakdown")
    @GetMapping("/ai-usage")
    public R<List<Map<String, Object>>> aiUsage(
            @RequestParam(defaultValue = "provider") String dim,
            @RequestParam(defaultValue = "7") int days) {
        return R.ok(dashboardService.aiUsage(StpUtil.getLoginIdAsLong(), dim, days));
    }

    /** 发布趋势按天聚合（默认近 30 天）。 */
    @Operation(summary = "publish trends per day")
    @GetMapping("/publish-trends")
    public R<List<Map<String, Object>>> publishTrends(
            @RequestParam(defaultValue = "30") int days) {
        return R.ok(dashboardService.publishTrends(StpUtil.getLoginIdAsLong(), days));
    }

    /** 成本明细按 provider 聚合（默认近 30 天）。 */
    @Operation(summary = "AI cost breakdown by provider")
    @GetMapping("/cost")
    public R<List<Map<String, Object>>> cost(
            @RequestParam(defaultValue = "30") int days) {
        return R.ok(dashboardService.cost(StpUtil.getLoginIdAsLong(), days));
    }
}
