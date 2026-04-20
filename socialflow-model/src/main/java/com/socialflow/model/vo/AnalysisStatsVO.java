package com.socialflow.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 代码分析仪表盘聚合 VO。
 */
@Data
@Schema(description = "代码分析仪表盘统计")
public class AnalysisStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本月分析次数 */
    private Integer monthlyCount;

    /** 总分析次数 */
    private Integer totalCount;

    /** 平均评分（仅审查类） */
    private Double averageScore;

    /** 高风险总数 */
    private Integer totalHighRisk;

    /** 已解决风险数 */
    private Integer resolvedCount;

    /** 近 30 天每日分析次数 */
    private List<DailyPoint> dailyTrend;

    /** 风险等级分布 */
    private Integer highTotal;
    private Integer mediumTotal;
    private Integer lowTotal;

    /** 按类别命中分布 */
    private List<CategoryStat> categoryStats;

    /** 最常分析仓库 Top 5 */
    private List<RepoHot> topRepos;

    // ========= LLM Token 消耗 =========

    /** 本月总 token 消耗 */
    private Long tokensMonthly;

    /** 本月输入 token */
    private Long tokensMonthlyPrompt;

    /** 本月输出 token */
    private Long tokensMonthlyCompletion;

    /** 本月 LLM 调用次数 */
    private Integer llmCallsMonthly;

    /** 本月成功分析平均每次消耗的 token */
    private Integer tokensPerAnalysisAvg;

    // ========= Wave 8 反馈闭环 =========

    /** 累计被标 INVALID（误判）的 finding 数 */
    private Long feedbackInvalidCount;

    /** 累计 IGNORED 的 finding 数（含误判 / 已修复 / 不适用） */
    private Long feedbackIgnoredCount;

    /** 误判率：INVALID / 总 finding * 100，0-100，保留 1 位小数 */
    private Double falsePositiveRate;

    /** 当前进入屏蔽列表的规约数（INVALID >= 阈值） */
    private Integer dismissedRulesCount;

    /** 误报 Top 5 规约（被标 INVALID 次数排行）*/
    private List<RuleInvalidStat> topInvalidRules;

    @Data
    public static class RuleInvalidStat implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private String ruleRef;
        private Long count;
    }

    @Data
    public static class DailyPoint implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private LocalDate date;
        private Integer count;
    }

    @Data
    public static class CategoryStat implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private String category;
        private Integer count;
    }

    @Data
    public static class RepoHot implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private String gitUrl;
        private Integer analyzeCount;
        private Integer lastScore;
    }
}
