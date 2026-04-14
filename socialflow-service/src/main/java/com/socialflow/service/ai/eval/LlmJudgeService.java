package com.socialflow.service.ai.eval;

import java.util.Map;

/**
 * LLM-as-Judge（用 LLM 当裁判）评分服务接口。
 *
 * 【什么是 LLM-as-Judge（LLM 充当裁判）？】
 *
 * 传统的内容质量评估需要大量人工审阅，成本高且速度慢。
 * LLM-as-Judge 是一种创新的自动化评估方法：用一个强大的 LLM（如 GPT-4）
 * 充当"裁判"，按照预定义的评分标准对 AI 生成的内容进行打分。
 * 研究表明，LLM 裁判的评分与人类专家的一致性很高，可以大幅提升评测效率。
 *
 * 【评分维度】
 *
 * 裁判 LLM 会从多个维度对内容进行 1.0-5.0 分的打分，例如：
 *     - 内容相关性（是否切题）
 *     - 语言流畅度（是否通顺自然）
 *     - 创意性（是否有新颖角度）
 *     - 平台适配度（是否符合目标平台的风格）
 * 各维度的权重在 {@link EvalService} 中配置。
 *
 * 【位置偏差（Position Bias）消除】
 *
 * 研究发现，LLM 裁判容易对"先出现的"内容给更高分（位置偏差）。
 * 为消除此偏差，实现类应随机交换 A/B 的顺序再提交给 LLM 裁判，
 * 然后将分数对应回正确的位置。
 */
public interface LlmJudgeService {

    /**
     * 对一个主题的 A/B 两份内容进行裁判打分。
     *
     * @param topic    测试主题（如"咖啡探店"）
     * @param platform 目标平台编码
     * @param outputA  配置 A 生成的内容
     * @param outputB  配置 B 生成的内容
     * @return 裁判裁决结果，包含各维度得分、总分、胜者和推理说明
     */
    JudgeVerdict judge(String topic, String platform, String outputA, String outputB);

    /**
     * 裁判裁决结果记录。
     *
     * @param scoresA   配置 A 的各维度得分，key 为维度名（如"relevance"），value 为分数（1.0-5.0）
     * @param scoresB   配置 B 的各维度得分
     * @param totalA    配置 A 的加权总分
     * @param totalB    配置 B 的加权总分
     * @param winner    胜者标识："A"、"B" 或 "TIE"（平局）
     * @param reasoning LLM 裁判的评分推理说明（解释为什么给出这样的分数）
     */
    record JudgeVerdict(
            Map<String, Double> scoresA,
            Map<String, Double> scoresB,
            double totalA,
            double totalB,
            String winner,
            String reasoning
    ) {}
}
