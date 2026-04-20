package com.socialflow.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘专用聚合 Mapper —— 用 SQL GROUP BY / SUM 替代内存聚合。
 *
 * 【为什么独立一个 Mapper】
 *   与 RepoAnalysisMapper 的 BaseMapper 职责区分：前者是 CRUD，后者是聚合只读。
 *   返回值用 {@code Map<String, Object>} 保持字段灵活，避免为每个聚合新建 VO。
 */
@Mapper
public interface RepoAnalysisDashboardMapper {

    /**
     * 单 SQL 汇总"总数 / 月度数 / 评分均值 / 高危/中危/低危累计"。
     * deleted=0 跳过逻辑删除的记录；status 不限，保持与原实现一致。
     */
    @Select("""
            SELECT
              COUNT(*)                                                            AS total_count,
              SUM(CASE WHEN create_time >= #{monthStart} THEN 1 ELSE 0 END)       AS monthly_count,
              SUM(CASE WHEN status = 'SUCCESS' AND create_time >= #{monthStart}
                       THEN 1 ELSE 0 END)                                         AS monthly_success,
              AVG(overall_score)                                                  AS avg_score,
              COALESCE(SUM(high_count),   0)                                      AS high_total,
              COALESCE(SUM(medium_count), 0)                                      AS medium_total,
              COALESCE(SUM(low_count),    0)                                      AS low_total
            FROM repo_analysis
            WHERE user_id = #{userId}
              AND is_deleted = 0
            """)
    Map<String, Object> summary(@Param("userId") Long userId,
                                @Param("monthStart") java.time.LocalDateTime monthStart);

    /** 近 30 天每日分析次数 */
    @Select("""
            SELECT DATE(create_time) AS day, COUNT(*) AS cnt
            FROM repo_analysis
            WHERE user_id = #{userId}
              AND is_deleted = 0
              AND create_time >= #{fromDate}
            GROUP BY DATE(create_time)
            ORDER BY day
            """)
    List<Map<String, Object>> dailyTrend(@Param("userId") Long userId,
                                         @Param("fromDate") java.time.LocalDateTime fromDate);

    /** Top N 热门仓库（含最新一次评分） */
    @Select("""
            SELECT t.git_url AS gitUrl, t.cnt AS analyzeCount,
                   (SELECT overall_score FROM repo_analysis r2
                    WHERE r2.user_id = #{userId} AND r2.git_url = t.git_url AND r2.is_deleted = 0
                    ORDER BY r2.create_time DESC LIMIT 1) AS lastScore
            FROM (
              SELECT git_url, COUNT(*) AS cnt
              FROM repo_analysis
              WHERE user_id = #{userId} AND is_deleted = 0 AND git_url IS NOT NULL
              GROUP BY git_url
              ORDER BY cnt DESC
              LIMIT #{limit}
            ) t
            """)
    List<Map<String, Object>> topRepos(@Param("userId") Long userId, @Param("limit") int limit);

    /** 已解决 finding 总数（属于当前用户的分析） */
    @Select("""
            SELECT COUNT(*) FROM repo_analysis_finding f
            JOIN repo_analysis a ON a.id = f.analysis_id
            WHERE a.user_id = #{userId} AND a.is_deleted = 0
              AND f.status = 'RESOLVED' AND f.is_deleted = 0
            """)
    long resolvedFindingCount(@Param("userId") Long userId);

    /** Finding 分类分布（当前用户） */
    @Select("""
            SELECT f.category AS category, COUNT(*) AS cnt
            FROM repo_analysis_finding f
            JOIN repo_analysis a ON a.id = f.analysis_id
            WHERE a.user_id = #{userId} AND a.is_deleted = 0
              AND f.is_deleted = 0 AND f.category IS NOT NULL
            GROUP BY f.category
            ORDER BY cnt DESC
            """)
    List<Map<String, Object>> categoryStats(@Param("userId") Long userId);

    /** 本月 LLM Token 汇总 */
    @Select("""
            SELECT
              COALESCE(SUM(total_tokens), 0)      AS total_tokens,
              COALESCE(SUM(prompt_tokens), 0)     AS prompt_tokens,
              COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
              COUNT(*)                            AS call_count
            FROM llm_call_log
            WHERE user_id = #{userId}
              AND create_time >= #{monthStart}
              AND is_deleted = 0
            """)
    Map<String, Object> monthlyTokenSummary(@Param("userId") Long userId,
                                            @Param("monthStart") java.time.LocalDateTime monthStart);

    /** 本地日期行 */
    record DailyCountRow(LocalDate day, int cnt) {}
}
