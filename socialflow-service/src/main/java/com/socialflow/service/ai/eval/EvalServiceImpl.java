package com.socialflow.service.ai.eval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.dao.mapper.EvalResultMapper;
import com.socialflow.dao.mapper.EvalTaskMapper;
import com.socialflow.model.dto.EvalTaskCreateDTO;
import com.socialflow.model.entity.EvalResult;
import com.socialflow.model.entity.EvalTask;
import com.socialflow.model.vo.EvalReportVO;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmResponse;
import com.socialflow.service.ai.llm.LlmRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 内容质量 A/B 评测编排服务实现。
 *
 * 负责评测任务的完整生命周期：创建 -> 异步执行 -> 生成报告。
 * 执行过程中会调用 LLM 分别生成文案，再通过 LlmJudgeService 进行评分对比。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalServiceImpl implements EvalService {

    private final EvalTaskMapper evalTaskMapper;
    private final EvalResultMapper evalResultMapper;
    private final LlmJudgeService llmJudgeService;
    private final LlmRouter llmRouter;

    @Value("${socialflow.ai.system-api-key}")
    private String systemApiKey;

    @Value("${socialflow.ai.default-provider}")
    private String defaultProvider;

    @Override
    public EvalTask createTask(Long userId, EvalTaskCreateDTO dto) {
        // 创建评测任务实体
        EvalTask task = new EvalTask();
        task.setUserId(userId);
        task.setName(dto.getName());

        // 将配置和主题列表序列化为 JSON 字符串存储
        task.setConfigA(JsonUtil.toJson(dto.getConfigA()));
        task.setConfigB(JsonUtil.toJson(dto.getConfigB()));
        task.setTestTopics(JsonUtil.toJson(dto.getTestTopics()));

        // 初始状态：待执行
        task.setStatus("PENDING");
        task.setTotalCases(dto.getTestTopics().size());
        task.setCompletedCases(0);

        // 插入数据库
        evalTaskMapper.insert(task);
        log.info("创建评测任务成功: taskId={}, name={}, totalCases={}", task.getId(), task.getName(), task.getTotalCases());

        return task;
    }

    @Override
    public void runTask(Long userId, Long taskId) {
        // 加载任务并校验归属
        EvalTask task = evalTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("评测任务不存在: " + taskId);
        }
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此评测任务");
        }

        // 更新任务状态为运行中
        task.setStatus("RUNNING");
        task.setCompletedCases(0);
        evalTaskMapper.updateById(task);

        // 异步执行评测流程
        CompletableFuture.runAsync(() -> doRunTask(task));
    }

    /**
     * 异步执行评测任务的核心逻辑。
     *
     * 遍历每个测试主题，分别用配置 A 和配置 B 生成文案，
     * 然后调用 LLM 裁判进行评分对比，将结果写入数据库。
     */
    private void doRunTask(EvalTask task) {
        try {
            // 反序列化测试主题列表
            List<EvalTaskCreateDTO.TestTopic> testTopics = JsonUtil.fromJson(
                    task.getTestTopics(), new TypeReference<>() {});

            // 反序列化配置 A 和配置 B
            Map<String, Object> configAMap = JsonUtil.fromJson(
                    task.getConfigA(), new TypeReference<>() {});
            Map<String, Object> configBMap = JsonUtil.fromJson(
                    task.getConfigB(), new TypeReference<>() {});

            // 解析配置 A 的 LLM 参数
            LlmProvider providerA = resolveProvider(configAMap);
            LlmConfig llmConfigA = buildLlmConfig(configAMap, providerA);

            // 解析配置 B 的 LLM 参数
            LlmProvider providerB = resolveProvider(configBMap);
            LlmConfig llmConfigB = buildLlmConfig(configBMap, providerB);

            // 逐个主题执行评测
            for (EvalTaskCreateDTO.TestTopic testTopic : testTopics) {
                String topic = testTopic.getTopic();
                String platform = testTopic.getPlatform();
                List<String> keywords = testTopic.getKeywords();

                log.info("评测进行中: taskId={}, topic={}, platform={}", task.getId(), topic, platform);

                // 构建生成文案的提示词
                String userContent = buildGenerationPrompt(topic, platform, keywords);
                List<ChatMessage> messages = List.of(
                        ChatMessage.system("你是专业文案写手，擅长为各大社交媒体平台撰写高质量文案。"),
                        ChatMessage.user(userContent)
                );

                // 使用配置 A 生成文案
                String outputA;
                try {
                    LlmResponse responseA = llmRouter.get(providerA).chat(messages, llmConfigA);
                    outputA = responseA.getContent();
                } catch (Exception e) {
                    log.error("配置 A 生成文案失败: topic={}", topic, e);
                    outputA = "[生成失败] " + e.getMessage();
                }

                // 使用配置 B 生成文案
                String outputB;
                try {
                    LlmResponse responseB = llmRouter.get(providerB).chat(messages, llmConfigB);
                    outputB = responseB.getContent();
                } catch (Exception e) {
                    log.error("配置 B 生成文案失败: topic={}", topic, e);
                    outputB = "[生成失败] " + e.getMessage();
                }

                // 调用 LLM 裁判对两份文案进行评分
                LlmJudgeService.JudgeVerdict verdict = llmJudgeService.judge(topic, platform, outputA, outputB);

                // 创建评测结果并写入数据库
                EvalResult result = new EvalResult();
                result.setEvalTaskId(task.getId());
                result.setInputTopic(topic);
                result.setInputPlatform(platform);
                result.setOutputA(outputA);
                result.setOutputB(outputB);
                result.setScoresA(JsonUtil.toJson(verdict.scoresA()));
                result.setScoresB(JsonUtil.toJson(verdict.scoresB()));
                result.setTotalScoreA(BigDecimal.valueOf(verdict.totalA()));
                result.setTotalScoreB(BigDecimal.valueOf(verdict.totalB()));
                result.setWinner(verdict.winner());
                result.setJudgeReasoning(verdict.reasoning());

                evalResultMapper.insert(result);

                // 更新任务进度
                task.setCompletedCases(task.getCompletedCases() + 1);
                evalTaskMapper.updateById(task);

                log.info("评测完成一轮: taskId={}, topic={}, winner={}, scoreA={}, scoreB={}",
                        task.getId(), topic, verdict.winner(), verdict.totalA(), verdict.totalB());
            }

            // 全部主题评测完成
            task.setStatus("COMPLETED");
            evalTaskMapper.updateById(task);
            log.info("评测任务全部完成: taskId={}, name={}", task.getId(), task.getName());

        } catch (Exception e) {
            // 任何未捕获的异常导致任务失败
            log.error("评测任务执行失败: taskId={}", task.getId(), e);
            task.setStatus("FAILED");
            evalTaskMapper.updateById(task);
        }
    }

    @Override
    public EvalReportVO getReport(Long userId, Long taskId) {
        // 加载任务并校验归属
        EvalTask task = evalTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("评测任务不存在: " + taskId);
        }
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException("无权查看此评测报告");
        }

        // 查询该任务下的所有评测结果
        List<EvalResult> results = evalResultMapper.selectList(
                new LambdaQueryWrapper<EvalResult>()
                        .eq(EvalResult::getEvalTaskId, taskId)
                        .orderByAsc(EvalResult::getCreateTime));

        // 统计胜负
        int winsA = 0;
        int winsB = 0;
        int ties = 0;

        // 累积各维度分数，用于计算平均值
        Map<String, BigDecimal> sumScoresA = new HashMap<>();
        Map<String, BigDecimal> sumScoresB = new HashMap<>();
        BigDecimal sumTotalA = BigDecimal.ZERO;
        BigDecimal sumTotalB = BigDecimal.ZERO;

        // 用于排序找出最佳和最差用例
        List<EvalReportVO.CaseResult> allCases = new ArrayList<>();

        for (EvalResult result : results) {
            // 统计胜负
            switch (result.getWinner()) {
                case "A" -> winsA++;
                case "B" -> winsB++;
                default -> ties++;
            }

            // 累积总分
            if (result.getTotalScoreA() != null) {
                sumTotalA = sumTotalA.add(result.getTotalScoreA());
            }
            if (result.getTotalScoreB() != null) {
                sumTotalB = sumTotalB.add(result.getTotalScoreB());
            }

            // 解析并累积各维度分数
            accumulateScores(sumScoresA, result.getScoresA());
            accumulateScores(sumScoresB, result.getScoresB());

            // 构建用例结果
            EvalReportVO.CaseResult caseResult = new EvalReportVO.CaseResult();
            caseResult.setTopic(result.getInputTopic());
            caseResult.setWinner(result.getWinner());
            caseResult.setScoreA(result.getTotalScoreA());
            caseResult.setScoreB(result.getTotalScoreB());
            allCases.add(caseResult);
        }

        int total = results.size();

        // 计算各维度平均分
        Map<String, BigDecimal> avgScoresA = calculateAvgScores(sumScoresA, total);
        Map<String, BigDecimal> avgScoresB = calculateAvgScores(sumScoresB, total);

        // 计算总体平均分
        BigDecimal overallAvgA = total > 0
                ? sumTotalA.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal overallAvgB = total > 0
                ? sumTotalB.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 按 A-B 得分差排序：差值最大的是 bestCases（A 表现最好），差值最小的是 worstCases（B 表现最好）
        allCases.sort(Comparator.comparing(c -> {
            BigDecimal sa = c.getScoreA() != null ? c.getScoreA() : BigDecimal.ZERO;
            BigDecimal sb = c.getScoreB() != null ? c.getScoreB() : BigDecimal.ZERO;
            return sb.subtract(sa); // 升序：A 优势最大的排前面
        }));

        // 取前 3 个为 bestCases（A 表现最好的用例）
        List<EvalReportVO.CaseResult> bestCases = allCases.stream()
                .limit(3)
                .toList();

        // 取后 3 个为 worstCases（B 表现最好的用例），逆序取
        List<EvalReportVO.CaseResult> worstCases = allCases.stream()
                .sorted(Comparator.comparing(c -> {
                    BigDecimal sa = ((EvalReportVO.CaseResult) c).getScoreA() != null
                            ? ((EvalReportVO.CaseResult) c).getScoreA() : BigDecimal.ZERO;
                    BigDecimal sb = ((EvalReportVO.CaseResult) c).getScoreB() != null
                            ? ((EvalReportVO.CaseResult) c).getScoreB() : BigDecimal.ZERO;
                    return sa.subtract(sb); // 升序：B 优势最大的排前面
                }))
                .limit(3)
                .toList();

        // 组装报告 VO
        EvalReportVO report = new EvalReportVO();
        report.setTaskId(taskId);
        report.setTaskName(task.getName());
        report.setTotalCases(task.getTotalCases());
        report.setWinsA(winsA);
        report.setWinsB(winsB);
        report.setTies(ties);
        report.setAvgScoresA(avgScoresA);
        report.setAvgScoresB(avgScoresB);
        report.setOverallAvgA(overallAvgA);
        report.setOverallAvgB(overallAvgB);
        report.setBestCases(bestCases);
        report.setWorstCases(worstCases);

        return report;
    }

    @Override
    public List<EvalTask> listTasks(Long userId) {
        // 按创建时间倒序查询当前用户的所有评测任务
        return evalTaskMapper.selectList(
                new LambdaQueryWrapper<EvalTask>()
                        .eq(EvalTask::getUserId, userId)
                        .orderByDesc(EvalTask::getCreateTime));
    }

    @Override
    public void deleteTask(Long userId, Long taskId) {
        // 加载任务并校验归属
        EvalTask task = evalTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("评测任务不存在: " + taskId);
        }
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException("无权删除此评测任务");
        }
        // 先删除该任务下的所有评测结果
        evalResultMapper.delete(
                new LambdaQueryWrapper<EvalResult>()
                        .eq(EvalResult::getEvalTaskId, taskId));
        // 再删除任务本身
        evalTaskMapper.deleteById(taskId);
        log.info("删除评测任务成功: taskId={}, name={}", taskId, task.getName());
    }

    // ====================== 私有辅助方法 ======================

    /**
     * 构建文案生成提示词。
     */
    private String buildGenerationPrompt(String topic, String platform, List<String> keywords) {
        StringBuilder sb = new StringBuilder();
        sb.append("请写一篇关于「").append(topic).append("」的").append(platform).append("文案。");
        if (keywords != null && !keywords.isEmpty()) {
            sb.append("\n请包含以下关键词：").append(String.join("、", keywords));
        }
        sb.append("\n要求：内容原创、符合平台调性、有吸引力。");
        return sb.toString();
    }

    /**
     * 从配置 Map 中解析 LLM Provider。
     * 如果配置中未指定 provider，使用系统默认 provider。
     */
    private LlmProvider resolveProvider(Map<String, Object> configMap) {
        Object providerObj = configMap.get("provider");
        if (providerObj != null) {
            try {
                return LlmProvider.valueOf(providerObj.toString().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("无效的 provider 配置: {}，使用默认 provider", providerObj);
            }
        }
        return LlmProvider.valueOf(defaultProvider.toUpperCase());
    }

    /**
     * 根据配置 Map 构建 LlmConfig 对象。
     */
    private LlmConfig buildLlmConfig(Map<String, Object> configMap, LlmProvider provider) {
        // 获取模型名称，默认使用 provider 对应的默认模型
        String model = configMap.containsKey("model")
                ? configMap.get("model").toString()
                : getDefaultModel(provider);

        // 获取温度参数，默认 0.7
        Double temperature = 0.7;
        if (configMap.containsKey("temperature")) {
            temperature = Double.parseDouble(configMap.get("temperature").toString());
        }

        return LlmConfig.builder()
                .model(model)
                .apiKey(systemApiKey)
                .temperature(temperature)
                .maxTokens(2048)
                .build();
    }

    /**
     * 获取 Provider 的默认模型名称。
     */
    private String getDefaultModel(LlmProvider provider) {
        return switch (provider) {
            case DEEPSEEK -> "deepseek-reasoner";
            case QWEN -> "qwen-max";
            case OPENAI -> "gpt-4o";
            case CLAUDE -> "claude-3-5-sonnet-20241022";
            case GLM -> "glm-4";
        };
    }

    /**
     * 累积各维度分数到汇总 Map。
     */
    private void accumulateScores(Map<String, BigDecimal> sumMap, String scoresJson) {
        if (scoresJson == null || scoresJson.isBlank()) {
            return;
        }
        try {
            Map<String, Object> scores = JsonUtil.fromJson(scoresJson, new TypeReference<>() {});
            for (Map.Entry<String, Object> entry : scores.entrySet()) {
                if (entry.getValue() instanceof Number num) {
                    BigDecimal value = BigDecimal.valueOf(num.doubleValue());
                    sumMap.merge(entry.getKey(), value, BigDecimal::add);
                }
            }
        } catch (Exception e) {
            log.warn("解析评分 JSON 失败: {}", scoresJson, e);
        }
    }

    /**
     * 计算各维度的平均分。
     */
    private Map<String, BigDecimal> calculateAvgScores(Map<String, BigDecimal> sumMap, int count) {
        Map<String, BigDecimal> avgMap = new HashMap<>();
        if (count == 0) {
            return avgMap;
        }
        BigDecimal divisor = BigDecimal.valueOf(count);
        for (Map.Entry<String, BigDecimal> entry : sumMap.entrySet()) {
            avgMap.put(entry.getKey(), entry.getValue().divide(divisor, 2, RoundingMode.HALF_UP));
        }
        return avgMap;
    }
}
