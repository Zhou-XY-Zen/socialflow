package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 评估任务实体类 —— 对应数据库表 `eval_task`
 *
 * 【作用】存储 AI 文案质量对比评估任务的配置和进度信息。
 *   评估任务采用 A/B 测试的方式：用户指定两套 AI 配置（如不同模型、不同模板、不同温度参数），
 *   系统自动使用同一组测试主题分别生成文案，然后由 AI 评审员打分对比。
 *
 * 【为什么需要它】
 *   用户在选择 AI 模型和 Prompt 模板时，往往需要对比不同配置的效果。
 *   评估任务提供了一种系统化的对比方式，帮助用户找到最佳的生成配置，
 *   而不是凭感觉选择。
 *
 * 【关联关系】
 *   - eval_task.user_id → sys_user.id （创建者）
 *   - eval_result.eval_task_id → eval_task.id （该任务下的每一轮评估结果）
 *
 * 【使用场景】
 *   - 用户在"评估中心"页面创建一个新的评估任务
 *   - 系统异步执行任务：对每个测试主题分别用配置 A 和配置 B 生成文案，再评分
 *   - 用户查看评估报告，了解哪个配置的整体效果更好
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eval_task")
public class EvalTask extends BaseEntity {

    /**
     * 创建者用户 ID
     *
     * 关联 sys_user.id，标识是哪个用户创建的评估任务。
     */
    private Long userId;

    /**
     * 任务名称
     *
     * 用户为评估任务起的名称，便于识别和管理。
     * 示例："GPT-4o vs Claude-3 小红书文案对比"
     */
    private String name;

    /**
     * A 组配置（JSON 格式）
     *
     * 第一套 AI 生成配置。
     * JSON 格式：{"model": "gpt-4o", "templateId": 1, "temperature": 0.7}
     * - model: 使用的大模型
     * - templateId: 使用的 Prompt 模板 ID
     * - temperature: 温度参数（控制生成的随机性）
     */
    private String configA;

    /**
     * B 组配置（JSON 格式）
     *
     * 第二套 AI 生成配置，与 configA 对照。
     * JSON 格式同 configA。
     */
    private String configB;

    /**
     * 测试主题列表（JSON 数组）
     *
     * 评估任务要测试的主题集合，每个主题都会分别用 A、B 两套配置生成文案。
     * JSON 格式：[{"topic": "春季护肤", "platform": "XIAOHONGSHU", "keywords": ["护肤","防晒"]}, ...]
     */
    private String testTopics;

    /**
     * 任务状态
     *
     * 标识评估任务的执行进度。
     * 可选值："PENDING"（等待执行）、"RUNNING"（执行中）、
     *         "COMPLETED"（已完成）、"FAILED"（执行失败）
     */
    private String status;

    /**
     * 总测试用例数
     *
     * 等于测试主题列表的长度，即需要对比评估的总轮次。
     */
    private Integer totalCases;

    /**
     * 已完成的测试用例数
     *
     * 记录已经完成评估的轮次。
     * completedCases / totalCases 可以计算出任务进度百分比。
     */
    private Integer completedCases;

    /**
     * 配对 t-test 的 p-value（Wave 4.6）。
     *
     * <p>对所有 (totalScoreA - totalScoreB) 配对差做单样本 t 检验，
     * 反映 A/B 两组得分差异在统计意义上是否显著。一般规则：</p>
     * <ul>
     *   <li>p &lt; 0.05 - 显著差异，A/B 真的不一样</li>
     *   <li>p &gt;= 0.05 - 差异不显著，差异可能只是随机波动</li>
     * </ul>
     * <p>样本数 &lt; 2 时为 null。</p>
     */
    private java.math.BigDecimal pValue;
}
