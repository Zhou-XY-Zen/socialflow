package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 评估结果实体类 —— 对应数据库表 `eval_result`
 *
 * 【作用】存储评估任务中每一轮 A/B 对比的详细结果。
 *   每一条记录代表一个测试主题下两套配置生成的文案及其评分。
 *
 * 【为什么需要它】
 *   评估任务的最终目的是对比两套配置的文案质量。本表记录了每轮对比的：
 *   1. 两套配置分别生成的文案内容（outputA / outputB）
 *   2. 多维度评分（相关性、风格、流畅度等）
 *   3. 综合得分和胜负结果
 *   4. AI 评审员的评判理由
 *
 * 【关联关系】
 *   - eval_result.eval_task_id → eval_task.id （所属评估任务）
 *
 * 【使用场景】
 *   - 评估任务执行过程中，每完成一轮对比就写入一条结果
 *   - 查看评估报告时，展示每轮的详细对比信息
 *   - 汇总所有结果计算整体胜率和平均分
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eval_result")
public class EvalResult extends BaseEntity {

    /**
     * 所属评估任务 ID
     *
     * 关联 eval_task.id，标识该结果属于哪个评估任务。
     */
    private Long evalTaskId;

    /**
     * 输入主题
     *
     * 本轮评估使用的测试主题。
     * 示例："春季护肤推荐"
     */
    private String inputTopic;

    /**
     * 输入平台
     *
     * 本轮评估指定的目标平台。
     * 示例："XIAOHONGSHU"
     */
    private String inputPlatform;

    /**
     * A 组输出文案
     *
     * 使用评估任务的 configA 配置生成的完整文案内容。
     */
    private String outputA;

    /**
     * B 组输出文案
     *
     * 使用评估任务的 configB 配置生成的完整文案内容。
     */
    private String outputB;

    /**
     * A 组多维度评分（JSON 格式）
     *
     * AI 评审员对 A 组文案在各个维度的评分。
     * JSON 格式示例：{"relevance": 8.5, "style": 7.0, "fluency": 9.0, "creativity": 6.5}
     * - relevance: 与主题的相关性
     * - style: 风格是否符合平台调性
     * - fluency: 文字流畅度
     * - creativity: 创意和吸引力
     */
    private String scoresA;

    /**
     * B 组多维度评分（JSON 格式）
     *
     * AI 评审员对 B 组文案在各个维度的评分。格式同 scoresA。
     */
    private String scoresB;

    /**
     * A 组综合总分
     *
     * A 组各维度评分的加权总分或平均分。
     */
    private BigDecimal totalScoreA;

    /**
     * B 组综合总分
     *
     * B 组各维度评分的加权总分或平均分。
     */
    private BigDecimal totalScoreB;

    /**
     * 本轮胜出方
     *
     * 可选值："A"（A 组胜出）、"B"（B 组胜出）、"TIE"（平局）。
     * 由 AI 评审员根据综合评分和主观判断决定。
     */
    private String winner;  // A | B | TIE

    /**
     * AI 评审员的评判理由
     *
     * AI 评审员对本轮结果的文字解释，说明为什么某一方胜出或为什么判定为平局。
     * 帮助用户理解评分背后的逻辑。
     */
    private String judgeReasoning;
}
