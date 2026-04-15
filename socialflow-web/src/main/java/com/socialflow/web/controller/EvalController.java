package com.socialflow.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.exception.NotFoundException;
import com.socialflow.common.result.R;
import com.socialflow.dao.mapper.EvalResultMapper;
import com.socialflow.dao.mapper.EvalTaskMapper;
import com.socialflow.model.dto.EvalTaskCreateDTO;
import com.socialflow.model.entity.EvalResult;
import com.socialflow.model.entity.EvalTask;
import com.socialflow.model.vo.EvalReportVO;
import com.socialflow.service.ai.eval.EvalService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测任务控制器 —— 管理 AI 内容生成质量的评估任务。
 *
 * 本控制器处理的基础 URL 路径为 {@code /api/v1/eval}，提供以下功能：
 *     - 创建评测任务
 *     - 异步执行评测任务
 *     - 获取评测报告
 *
 * 评测（Eval）是衡量 AI 生成内容质量的重要手段。通过创建评测任务，
 * 系统可以自动对一批生成内容进行打分和分析，帮助用户了解不同模型、
 * 不同参数配置下的生成效果，从而持续优化内容质量。
 *
 * 使用的 HTTP 方法：
 *     - POST —— 创建评测任务、触发执行
 *     - GET  —— 查询评测报告
 *
 * @see EvalService 评测业务逻辑的具体实现
 */
/*
 * @Tag           —— Swagger 文档分组标签，显示为 "eval"
 * @RestController —— REST 控制器
 * @RequestMapping —— 公共路径前缀：/api/v1/eval
 * @RequiredArgsConstructor —— Lombok 自动注入 final 依赖
 */
@Tag(name = "eval")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/eval")
@RequiredArgsConstructor
public class EvalController {

    /** 评测服务，封装评测任务的创建、执行和报告生成逻辑 */
    private final EvalService evalService;

    /**
     * 创建评测任务。
     *
     * 接口路径：POST /api/v1/eval/task
     *
     * 功能：创建一个新的评测任务。评测任务定义了需要评估的内容集合、
     * 评估维度（如流畅度、准确性、创意性等）和评分标准。
     * 创建后任务处于待执行状态，需要手动调用执行接口启动。
     *
     * @param dto 评测任务创建参数（包含评测名称、评估内容 ID 列表、评分维度等）
     * @return 统一响应体 R，包含创建的评测任务实体 EvalTask
     */
    @Operation(summary = "create eval task")
    @PostMapping("/task")
    public R<EvalTask> createTask(@Valid @RequestBody EvalTaskCreateDTO dto) {
        return R.ok(evalService.createTask(StpUtil.getLoginIdAsLong(), dto));
    }

    /**
     * 异步执行评测任务。
     *
     * 接口路径：POST /api/v1/eval/task/{taskId}/run
     *
     * 功能：触发指定评测任务的异步执行。调用后立即返回（不阻塞），
     * 评测过程在后台线程中运行。评测完成后，可以通过报告接口查看结果。
     *
     * 评测执行过程：系统会逐条对内容进行 AI 打分，生成详细的评估报告。
     *
     * @param taskId 评测任务 ID（从 URL 路径中获取）
     * @return 统一响应体 R，无数据体（表示任务已成功提交执行）
     */
    @Operation(summary = "run eval task asynchronously")
    @PostMapping("/task/{taskId}/run")
    public R<Void> runTask(@PathVariable Long taskId) {
        evalService.runTask(StpUtil.getLoginIdAsLong(), taskId);
        return R.ok();
    }

    /**
     * 获取评测报告。
     *
     * 接口路径：GET /api/v1/eval/task/{taskId}/report
     *
     * 功能：获取指定评测任务的详细评测报告。报告包含每条内容的评分、
     * 各维度的平均分、整体评估摘要等信息。
     *
     * 注意：如果评测任务尚未完成，返回的报告可能是部分结果或空报告。
     *
     * @param taskId 评测任务 ID（从 URL 路径中获取）
     * @return 统一响应体 R，包含评测报告详情 EvalReportVO
     */
    @Operation(summary = "fetch eval report")
    @GetMapping("/task/{taskId}/report")
    public R<EvalReportVO> report(@PathVariable Long taskId) {
        return R.ok(evalService.getReport(StpUtil.getLoginIdAsLong(), taskId));
    }

    /**
     * 查询当前用户的评测任务列表。
     *
     * 接口路径：GET /api/v1/eval/tasks
     *
     * 功能：返回当前登录用户创建的所有评测任务，按创建时间倒序排列。
     * 用于"评估中心"页面展示任务列表及其执行状态。
     *
     * @return 统一响应体 R，包含评测任务列表
     */
    @Operation(summary = "list eval tasks")
    @GetMapping("/tasks")
    public R<List<EvalTask>> listTasks() {
        return R.ok(evalService.listTasks(StpUtil.getLoginIdAsLong()));
    }

    /**
     * 删除评测任务及其所有评测结果。
     *
     * 接口路径：DELETE /api/v1/eval/task/{taskId}
     *
     * @param taskId 要删除的评测任务 ID
     * @return 统一响应体 R，无数据体
     */
    @Operation(summary = "delete eval task")
    @DeleteMapping("/task/{taskId}")
    public R<Void> deleteTask(@PathVariable Long taskId) {
        evalService.deleteTask(StpUtil.getLoginIdAsLong(), taskId);
        return R.ok();
    }

    private final EvalTaskMapper evalTaskMapper;
    private final EvalResultMapper evalResultMapper;

    /**
     * Wave 4.6 - 导出评估报告为 CSV。
     *
     * <p>每行 = 一条 EvalResult，列：topic / platform / scoreA / scoreB / winner /
     * judgeReasoning。前端可直接下载到 Excel 打开。</p>
     *
     * <p>默认 CSV 格式（轻量、无需 POI 依赖）。如需 xlsx 后续可加 commons-csv 或 POI 实现。</p>
     */
    @Operation(summary = "export eval report as CSV (Wave 4.6)")
    @GetMapping("/task/{taskId}/export")
    public ResponseEntity<byte[]> exportReport(@PathVariable Long taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        EvalTask task = evalTaskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new NotFoundException("评估任务不存在或无权访问: " + taskId);
        }
        List<EvalResult> results = evalResultMapper.selectList(
                new LambdaQueryWrapper<EvalResult>()
                        .eq(EvalResult::getEvalTaskId, taskId)
                        .orderByAsc(EvalResult::getCreateTime));

        StringBuilder csv = new StringBuilder();
        // BOM 让 Excel 识别 UTF-8（中文不乱码）
        csv.append('\uFEFF');
        csv.append("topic,platform,total_score_a,total_score_b,winner,reasoning\n");
        for (EvalResult r : results) {
            csv.append(csvEscape(r.getInputTopic())).append(',')
                    .append(csvEscape(r.getInputPlatform())).append(',')
                    .append(r.getTotalScoreA() == null ? "" : r.getTotalScoreA()).append(',')
                    .append(r.getTotalScoreB() == null ? "" : r.getTotalScoreB()).append(',')
                    .append(csvEscape(r.getWinner())).append(',')
                    .append(csvEscape(r.getJudgeReasoning())).append('\n');
        }
        // 末尾追加汇总信息（含 p-value）
        csv.append("\nSUMMARY\n");
        csv.append("task_name,").append(csvEscape(task.getName())).append('\n');
        csv.append("status,").append(csvEscape(task.getStatus())).append('\n');
        csv.append("total_cases,").append(task.getTotalCases() == null ? "" : task.getTotalCases()).append('\n');
        csv.append("completed_cases,").append(task.getCompletedCases() == null ? "" : task.getCompletedCases()).append('\n');
        csv.append("p_value,").append(task.getPValue() == null ? "" : task.getPValue()).append('\n');

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);

        String filename = "eval_" + taskId + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    /** CSV 字段转义：含逗号/引号/换行的字段用双引号包裹，内部引号 → 双引号 */
    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean needsQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        String escaped = s.replace("\"", "\"\"");
        return needsQuote ? "\"" + escaped + "\"" : escaped;
    }
}
