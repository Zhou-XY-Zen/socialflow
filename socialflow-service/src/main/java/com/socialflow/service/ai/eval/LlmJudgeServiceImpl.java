package com.socialflow.service.ai.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmResponse;
import com.socialflow.service.ai.llm.LlmRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-Judge 评分服务实现。
 *
 * 使用 LLM 充当裁判，对两份文案进行多维度打分对比。
 * 内置位置偏差消除机制：随机交换 A/B 顺序后再提交给 LLM 裁判。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmJudgeServiceImpl implements LlmJudgeService {

    private final LlmRouter llmRouter;

    @Value("${socialflow.ai.system-api-key}")
    private String systemApiKey;

    @Value("${socialflow.ai.default-provider}")
    private String defaultProvider;

    /** 用于从 LLM 输出中提取 JSON 块的正则 */
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");

    /** 各评分维度的权重配置 */
    private static final Map<String, Double> DIMENSION_WEIGHTS = Map.of(
            "relevance", 0.25,
            "style", 0.25,
            "fluency", 0.15,
            "creativity", 0.15,
            "format", 0.10,
            "faithfulness", 0.10
    );

    /** 不含 faithfulness 时的权重配置（faithfulness 的权重均分给其他维度） */
    private static final Map<String, Double> DIMENSION_WEIGHTS_NO_FAITH = Map.of(
            "relevance", 0.27,
            "style", 0.27,
            "fluency", 0.17,
            "creativity", 0.17,
            "format", 0.12
    );

    @Override
    public JudgeVerdict judge(String topic, String platform, String outputA, String outputB) {
        try {
            // 随机交换 A/B 顺序，消除位置偏差
            boolean swapped = new Random().nextBoolean();
            String first = swapped ? outputB : outputA;
            String second = swapped ? outputA : outputB;

            // 构建系统提示词
            String systemPrompt = "你是一名资深的内容质量评审专家。请对以下两段社交媒体文案进行评分对比。";

            // 构建用户提示词，要求 LLM 输出 JSON 格式
            String userPrompt = buildUserPrompt(topic, platform, first, second);

            // 构建消息列表
            List<ChatMessage> messages = List.of(
                    ChatMessage.system(systemPrompt),
                    ChatMessage.user(userPrompt)
            );

            // 构建 LLM 调用配置
            LlmProvider provider = LlmProvider.valueOf(defaultProvider.toUpperCase());
            LlmConfig config = LlmConfig.builder()
                    .model(getModelForProvider(provider))
                    .apiKey(systemApiKey)
                    .temperature(0.1)  // 低温度，保证评分稳定性
                    .maxTokens(2048)
                    .build();

            // 调用 LLM 裁判
            LlmResponse response = llmRouter.get(provider).chat(messages, config);
            String content = response.getContent();
            log.debug("LLM 裁判原始输出: {}", content);

            // 解析 LLM 输出中的 JSON
            Map<String, Object> parsed = extractJson(content);

            // 提取两方的分数
            @SuppressWarnings("unchecked")
            Map<String, Object> rawScoresFirst = (Map<String, Object>) parsed.get("scoresA");
            @SuppressWarnings("unchecked")
            Map<String, Object> rawScoresSecond = (Map<String, Object>) parsed.get("scoresB");
            String reasoning = parsed.getOrDefault("reasoning", "").toString();

            // 转为 Double 类型的分数 Map
            Map<String, Double> scoresFirst = toDoubleMap(rawScoresFirst);
            Map<String, Double> scoresSecond = toDoubleMap(rawScoresSecond);

            // 计算加权总分
            boolean hasFaithfulness = scoresFirst.containsKey("faithfulness");
            Map<String, Double> weights = hasFaithfulness ? DIMENSION_WEIGHTS : DIMENSION_WEIGHTS_NO_FAITH;

            double totalFirst = calculateWeightedTotal(scoresFirst, weights);
            double totalSecond = calculateWeightedTotal(scoresSecond, weights);

            // 如果之前交换了顺序，现在换回来
            Map<String, Double> finalScoresA;
            Map<String, Double> finalScoresB;
            double totalA;
            double totalB;
            if (swapped) {
                finalScoresA = scoresSecond;
                finalScoresB = scoresFirst;
                totalA = totalSecond;
                totalB = totalFirst;
            } else {
                finalScoresA = scoresFirst;
                finalScoresB = scoresSecond;
                totalA = totalFirst;
                totalB = totalSecond;
            }

            // 判定胜者：差值小于 0.3 判定为平局
            String winner;
            double diff = totalA - totalB;
            if (Math.abs(diff) < 0.3) {
                winner = "TIE";
            } else {
                winner = diff > 0 ? "A" : "B";
            }

            return new JudgeVerdict(finalScoresA, finalScoresB, totalA, totalB, winner, reasoning);

        } catch (Exception e) {
            log.error("LLM 裁判评分失败，返回默认平局裁决: topic={}, platform={}", topic, platform, e);
            // 解析失败时返回默认平局裁决
            return buildDefaultTieVerdict();
        }
    }

    /**
     * 构建用户提示词，引导 LLM 输出结构化 JSON 评分结果。
     */
    private String buildUserPrompt(String topic, String platform, String contentFirst, String contentSecond) {
        return """
                【评测任务】
                主题：%s
                目标平台：%s

                【文案 A】
                %s

                【文案 B】
                %s

                【评分要求】
                请从以下维度分别对文案 A 和文案 B 进行评分（1.0-5.0 分）：
                - relevance（相关性）：文案与主题的匹配程度
                - style（风格适配度）：是否符合目标平台的内容风格
                - fluency（流畅度）：语言是否通顺自然
                - creativity（创意性）：是否有新颖的角度或表达
                - format（格式规范）：排版、标签、emoji 等是否符合平台规范
                - faithfulness（忠实度）：是否准确传达主题信息，无虚假内容

                【输出格式】
                请严格按以下 JSON 格式输出，不要包含其他内容：
                ```json
                {
                  "scoresA": {"relevance": 4.0, "style": 3.5, "fluency": 4.5, "creativity": 3.0, "format": 4.0, "faithfulness": 4.0},
                  "scoresB": {"relevance": 3.5, "style": 4.0, "fluency": 4.0, "creativity": 4.5, "format": 3.5, "faithfulness": 3.5},
                  "reasoning": "简要说明评分理由..."
                }
                ```
                """.formatted(topic, platform, contentFirst, contentSecond);
    }

    /**
     * 从 LLM 输出文本中提取 JSON 对象。
     * LLM 可能在 JSON 前后包含说明文字或 markdown 代码块标记。
     */
    private Map<String, Object> extractJson(String text) {
        // 先尝试直接解析
        try {
            return JsonUtil.fromJson(text.trim(), new TypeReference<>() {});
        } catch (Exception ignored) {
            // 直接解析失败，尝试从文本中提取 JSON 块
        }

        // 从文本中查找 JSON 块
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            String jsonStr = matcher.group();
            return JsonUtil.fromJson(jsonStr, new TypeReference<>() {});
        }

        throw new IllegalStateException("无法从 LLM 输出中提取 JSON");
    }

    /**
     * 将 Object 类型的分数 Map 转为 Double 类型。
     */
    private Map<String, Double> toDoubleMap(Map<String, Object> raw) {
        Map<String, Double> result = new HashMap<>();
        if (raw == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() instanceof Number num) {
                result.put(entry.getKey(), num.doubleValue());
            }
        }
        return result;
    }

    /**
     * 根据权重配置计算加权总分。
     */
    private double calculateWeightedTotal(Map<String, Double> scores, Map<String, Double> weights) {
        double total = 0.0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            String dim = entry.getKey();
            double weight = entry.getValue();
            double score = scores.getOrDefault(dim, 3.0); // 缺失维度默认 3.0（中等分）
            total += score * weight;
        }
        return Math.round(total * 100.0) / 100.0; // 保留两位小数
    }

    /**
     * 根据 Provider 获取默认模型名称。
     */
    private String getModelForProvider(LlmProvider provider) {
        return switch (provider) {
            case DEEPSEEK -> "deepseek-reasoner";
            case QWEN -> "qwen-max";
            case OPENAI -> "gpt-4o";
            case CLAUDE -> "claude-3-5-sonnet-20241022";
            case GLM -> "glm-4";
        };
    }

    /**
     * 构建默认的平局裁决结果（评分解析失败时使用）。
     */
    private JudgeVerdict buildDefaultTieVerdict() {
        Map<String, Double> neutralScores = Map.of(
                "relevance", 3.0,
                "style", 3.0,
                "fluency", 3.0,
                "creativity", 3.0,
                "format", 3.0
        );
        double neutralTotal = 3.0;
        return new JudgeVerdict(neutralScores, neutralScores, neutralTotal, neutralTotal, "TIE", "评分解析失败，默认判定为平局");
    }
}
