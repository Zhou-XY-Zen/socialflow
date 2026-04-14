package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 评估报告视图对象（VO）—— 用于向前端返回 AI 评估任务的汇总报告
 *
 * 【作用】封装一个评估任务的完整报告，包括：
 *   - 整体胜负统计（A 赢了几轮、B 赢了几轮、平了几轮）
 *   - 各维度的平均分对比
 *   - 总体平均分
 *   - 表现最好和最差的测试用例
 *   让用户一目了然地了解两套 AI 配置的效果差异。
 *
 * 【对应 API 接口（作为返回值）】
 *   GET /api/eval/task/{taskId}/report  —— 获取评估报告
 *
 * 【使用场景】
 *   评估任务执行完成后，用户在"评估报告"页面查看结果。
 *   页面展示总体对比、各维度分数图表、最佳/最差用例详情等。
 */
@Data
public class EvalReportVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评估任务 ID
     *
     * 该报告对应的评估任务的唯一标识。
     */
    private Long taskId;

    /**
     * 评估任务名称
     *
     * 用户为该评估任务起的名称。
     * 示例："GPT-4o vs Claude-3 小红书文案对比"
     */
    private String taskName;

    /**
     * 总测试用例数
     *
     * 该评估任务一共测试了多少组主题。
     */
    private Integer totalCases;

    /**
     * A 组胜出次数
     *
     * 在所有测试用例中，A 组配置生成的文案被判定为更优的次数。
     */
    private Integer winsA;

    /**
     * B 组胜出次数
     *
     * 在所有测试用例中，B 组配置生成的文案被判定为更优的次数。
     */
    private Integer winsB;

    /**
     * 平局次数
     *
     * 在所有测试用例中，A、B 两组被判定为不分上下的次数。
     * winsA + winsB + ties = totalCases
     */
    private Integer ties;

    /**
     * A 组各维度平均分
     *
     * A 组在所有测试用例上各评分维度的平均分。
     * Map 的 key 为维度名称（如 "relevance"、"style"、"fluency"），
     * value 为该维度的平均分。
     */
    private Map<String, BigDecimal> avgScoresA;

    /**
     * B 组各维度平均分
     *
     * B 组在所有测试用例上各评分维度的平均分。格式同 avgScoresA。
     */
    private Map<String, BigDecimal> avgScoresB;

    /**
     * A 组总体平均分
     *
     * A 组在所有测试用例上综合总分的平均值。
     */
    private BigDecimal overallAvgA;

    /**
     * B 组总体平均分
     *
     * B 组在所有测试用例上综合总分的平均值。
     */
    private BigDecimal overallAvgB;

    /**
     * 表现最好的测试用例列表
     *
     * 胜出方得分最高或差距最大的几组用例，用于展示"最佳表现"。
     */
    private List<CaseResult> bestCases;

    /**
     * 表现最差的测试用例列表
     *
     * 落败方得分最低或差距最大的几组用例，用于展示"需要改进"的案例。
     */
    private List<CaseResult> worstCases;

    /**
     * 单条测试用例结果内部类 —— 评估报告中展示的单个用例摘要
     *
     * 【作用】简要描述某一轮 A/B 对比的结果，
     * 用于在 bestCases 和 worstCases 列表中展示。
     */
    @Data
    public static class CaseResult implements Serializable {

        /** 序列化版本号 */
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 测试主题
         *
         * 本轮评估使用的主题。
         * 示例："春季护肤推荐"
         */
        private String topic;

        /**
         * 本轮胜出方
         *
         * 可选值："A"、"B"、"TIE"（平局）。
         */
        private String winner;

        /**
         * A 组综合得分
         *
         * A 组在本轮测试中的综合总分。
         */
        private BigDecimal scoreA;

        /**
         * B 组综合得分
         *
         * B 组在本轮测试中的综合总分。
         */
        private BigDecimal scoreB;
    }
}
