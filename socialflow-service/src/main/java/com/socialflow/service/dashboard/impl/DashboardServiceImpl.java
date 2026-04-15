package com.socialflow.service.dashboard.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.socialflow.dao.mapper.AiUsageLogMapper;
import com.socialflow.dao.mapper.ContentMapper;
import com.socialflow.dao.mapper.KnowledgeBaseMapper;
import com.socialflow.dao.mapper.MediaAssetMapper;
import com.socialflow.dao.mapper.PublishTaskMapper;
import com.socialflow.model.entity.AiUsageLog;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.KnowledgeBase;
import com.socialflow.model.entity.MediaAsset;
import com.socialflow.model.entity.PublishTask;
import com.socialflow.model.vo.DashboardOverviewVO;
import com.socialflow.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DashboardService 默认实现 —— 用 MyBatis-Plus 的 selectMaps 做聚合查询。
 *
 * <p>缓存策略：{@code overview} 用 {@code @Cacheable("dashboardOverview")} 缓存 1 分钟（在
 * {@link com.socialflow.config.CacheConfig} 配置）。其他端点高频但维度参数化，暂不缓存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ContentMapper contentMapper;
    private final PublishTaskMapper publishTaskMapper;
    private final AiUsageLogMapper aiUsageLogMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final MediaAssetMapper mediaAssetMapper;

    @Override
    @Cacheable(value = "dashboardOverview", key = "#userId")
    @Transactional(readOnly = true)
    public DashboardOverviewVO overview(Long userId) {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        // 1. 内容总数 + 按状态分组（用 selectMaps 走 SQL GROUP BY，避免拉全表）
        long contentTotal = contentMapper.selectCount(
                new LambdaQueryWrapper<Content>().eq(Content::getUserId, userId));
        Map<String, Long> contentByStatus = countContentByStatus(userId);

        // 2. 发布任务按状态：先查当前用户的所有 content_id，再按 content_id IN 过滤
        List<Long> contentIds = contentMapper.selectList(
                        new LambdaQueryWrapper<Content>()
                                .eq(Content::getUserId, userId)
                                .select(Content::getId))
                .stream().map(Content::getId).toList();

        Map<String, Long> publishByStatus = Map.of();
        long scheduledPending = 0L;
        if (!contentIds.isEmpty()) {
            List<PublishTask> tasks = publishTaskMapper.selectList(
                    new LambdaQueryWrapper<PublishTask>().in(PublishTask::getContentId, contentIds));
            publishByStatus = tasks.stream()
                    .collect(Collectors.groupingBy(
                            t -> t.getStatus() == null ? "UNKNOWN" : t.getStatus(),
                            Collectors.counting()));
            scheduledPending = tasks.stream()
                    .filter(t -> "SCHEDULED".equals(t.getPublishType())
                            && "PENDING".equals(t.getStatus()))
                    .count();
        }

        // 3. AI 用量近 7 天
        List<AiUsageLog> recentLogs = aiUsageLogMapper.selectList(
                new LambdaQueryWrapper<AiUsageLog>()
                        .eq(AiUsageLog::getUserId, userId)
                        .ge(AiUsageLog::getCreateTime, sevenDaysAgo));

        long aiCalls7d = recentLogs.size();
        long aiTokens7d = recentLogs.stream()
                .mapToLong(l -> l.getTotalTokens() == null ? 0 : l.getTotalTokens())
                .sum();
        BigDecimal aiCost7d = recentLogs.stream()
                .map(l -> l.getCostEstimate() == null ? BigDecimal.ZERO : l.getCostEstimate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. AI 用量按天分组
        Map<String, List<AiUsageLog>> byDay = recentLogs.stream()
                .filter(l -> l.getCreateTime() != null)
                .collect(Collectors.groupingBy(l -> l.getCreateTime().toLocalDate().toString()));

        List<Map<String, Object>> trend = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", e.getKey());
                    m.put("calls", (long) e.getValue().size());
                    m.put("tokens", e.getValue().stream()
                            .mapToLong(l -> l.getTotalTokens() == null ? 0 : l.getTotalTokens())
                            .sum());
                    m.put("cost", e.getValue().stream()
                            .map(l -> l.getCostEstimate() == null ? BigDecimal.ZERO : l.getCostEstimate())
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    return m;
                })
                .toList();

        // 5. KB / 媒体计数
        long kbTotal = knowledgeBaseMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getUserId, userId));
        long mediaTotal = mediaAssetMapper.selectCount(
                new LambdaQueryWrapper<MediaAsset>().eq(MediaAsset::getUserId, userId));

        return DashboardOverviewVO.builder()
                .contentTotal(contentTotal)
                .contentByStatus(contentByStatus)
                .publishByStatus(publishByStatus)
                .scheduledPending(scheduledPending)
                .aiCalls7d(aiCalls7d)
                .aiTokens7d(aiTokens7d)
                .aiCost7d(aiCost7d)
                .aiUsageTrend(trend)
                .kbTotal(kbTotal)
                .mediaTotal(mediaTotal)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aiUsage(Long userId, String dim, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<AiUsageLog> logs = aiUsageLogMapper.selectList(
                new LambdaQueryWrapper<AiUsageLog>()
                        .eq(AiUsageLog::getUserId, userId)
                        .ge(AiUsageLog::getCreateTime, since));

        java.util.function.Function<AiUsageLog, String> keyFn = switch (dim) {
            case "model" -> l -> l.getModel() == null ? "unknown" : l.getModel();
            case "requestType" -> l -> l.getRequestType() == null ? "unknown" : l.getRequestType();
            default -> l -> l.getProvider() == null ? "unknown" : l.getProvider();
        };

        return logs.stream()
                .collect(Collectors.groupingBy(keyFn))
                .entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", e.getKey());
                    m.put("calls", (long) e.getValue().size());
                    m.put("tokens", e.getValue().stream()
                            .mapToLong(l -> l.getTotalTokens() == null ? 0 : l.getTotalTokens())
                            .sum());
                    m.put("cost", e.getValue().stream()
                            .map(l -> l.getCostEstimate() == null ? BigDecimal.ZERO : l.getCostEstimate())
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    return m;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("calls"), (Long) a.get("calls")))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> publishTrends(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Long> contentIds = contentMapper.selectList(
                        new LambdaQueryWrapper<Content>()
                                .eq(Content::getUserId, userId)
                                .select(Content::getId))
                .stream().map(Content::getId).toList();
        if (contentIds.isEmpty()) {
            return List.of();
        }
        List<PublishTask> tasks = publishTaskMapper.selectList(
                new LambdaQueryWrapper<PublishTask>()
                        .in(PublishTask::getContentId, contentIds)
                        .ge(PublishTask::getCreateTime, since));

        return tasks.stream()
                .filter(t -> t.getCreateTime() != null)
                .collect(Collectors.groupingBy(t -> t.getCreateTime().toLocalDate().toString()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", e.getKey());
                    m.put("total", (long) e.getValue().size());
                    m.put("success", e.getValue().stream()
                            .filter(t -> "SUCCESS".equals(t.getStatus())).count());
                    m.put("failed", e.getValue().stream()
                            .filter(t -> t.getStatus() != null && t.getStatus().startsWith("FAILED"))
                            .count());
                    return m;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> cost(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<AiUsageLog> logs = aiUsageLogMapper.selectList(
                new LambdaQueryWrapper<AiUsageLog>()
                        .eq(AiUsageLog::getUserId, userId)
                        .ge(AiUsageLog::getCreateTime, since));

        return logs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getProvider() == null ? "unknown" : l.getProvider()))
                .entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("provider", e.getKey());
                    m.put("totalTokens", e.getValue().stream()
                            .mapToLong(l -> l.getTotalTokens() == null ? 0 : l.getTotalTokens())
                            .sum());
                    m.put("totalCost", e.getValue().stream()
                            .map(l -> l.getCostEstimate() == null ? BigDecimal.ZERO : l.getCostEstimate())
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    return m;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("totalCost"))
                        .compareTo((BigDecimal) a.get("totalCost")))
                .toList();
    }

    /**
     * 用 MyBatis-Plus QueryWrapper 走 SQL GROUP BY，避免拉全表内存分组。
     */
    private Map<String, Long> countContentByStatus(Long userId) {
        QueryWrapper<Content> q = new QueryWrapper<>();
        q.select("status", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .eq("is_deleted", 0)
                .groupBy("status");
        List<Map<String, Object>> rows = contentMapper.selectMaps(q);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String status = row.get("status") == null ? "UNKNOWN" : row.get("status").toString();
            Object cnt = row.get("cnt");
            result.put(status, cnt == null ? 0L : ((Number) cnt).longValue());
        }
        return result;
    }
}
