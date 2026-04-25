package com.socialflow.service.ai.eval;

import com.socialflow.common.annotation.Experimental;
import com.socialflow.model.dto.EvalTaskCreateDTO;
import com.socialflow.model.entity.EvalTask;
import com.socialflow.model.vo.EvalReportVO;

import java.util.List;

/**
 * AI 内容质量 A/B 评测编排服务接口。
 *
 * 【什么是 A/B 评测？】
 *
 * A/B 评测是一种用于比较两套 AI 配置优劣的实验方法。具体做法是：
 *     - 准备一组测试主题（如"咖啡探店"、"健身打卡"等）
 *     - 对每个主题，分别使用配置 A 和配置 B 各生成一篇内容
 *     - 由评审（人工或 AI）对两篇内容进行打分
 *     - 汇总所有主题的得分，统计哪套配置的整体表现更好
 * 这里的"配置"可以是不同的模型、不同的提示词模板、不同的参数组合等。
 *
 * 【评测的意义】
 *
 * 在调优 AI 系统时，仅凭主观感受很难判断修改是否带来了真正的提升。
 * A/B 评测提供了客观、量化的对比依据，帮助开发者做出数据驱动的决策。
 *
 * 【在系统中的位置】
 *
 * 评测是一个离线 / 异步流程：用户创建评测任务后，系统在后台异步执行，
 * 完成后生成评测报告。评测结果存入 {@code eval_result} 表，
 * 由 {@link LlmJudgeService} 负责具体的打分逻辑。
 */
@Experimental(since = "Wave 4.6",
        value = "LLM-as-Judge 打分稳定，但配对 t 检验置信区间、维度权重、模型自评偏差校正"
                + "等仍可能调整；EvalReportVO 字段后续可能新增。")
public interface EvalService {

    /**
     * 创建一个待执行的评测任务。
     *
     * 任务初始状态为 PENDING（待执行），需要手动调用 {@link #runTask} 启动。
     *
     * @param userId 创建者用户 ID
     * @param dto    评测任务参数（包含配置 A、配置 B、测试主题列表等）
     * @return 创建后的评测任务实体
     */
    EvalTask createTask(Long userId, EvalTaskCreateDTO dto);

    /**
     * 异步启动评测任务的执行。
     *
     * 执行流程（在后台线程中运行）：
     *     - 遍历任务中的每个测试主题
     *     - 分别用配置 A 和配置 B 生成内容
     *     - 调用 {@link LlmJudgeService} 对两份内容进行打分
     *     - 将每个主题的评测结果写入 {@code eval_result} 表
     *     - 全部完成后更新任务状态为 COMPLETED
     *
     * @param userId 执行者用户 ID
     * @param taskId 要执行的评测任务 ID
     */
    void runTask(Long userId, Long taskId);

    /**
     * 获取评测报告。
     *
     * 汇总指定评测任务的所有结果，生成包含各维度得分对比、胜负统计等
     * 信息的报告视图对象。
     *
     * @param userId 查询者用户 ID
     * @param taskId 评测任务 ID
     * @return 评测报告 VO
     */
    EvalReportVO getReport(Long userId, Long taskId);

    /**
     * 查询指定用户的所有评测任务列表，按创建时间倒序排列。
     *
     * @param userId 用户 ID
     * @return 评测任务列表
     */
    List<EvalTask> listTasks(Long userId);

    /**
     * 删除评测任务及其所有评测结果。
     *
     * @param userId 操作者用户 ID（校验任务归属）
     * @param taskId 要删除的评测任务 ID
     */
    void deleteTask(Long userId, Long taskId);
}
