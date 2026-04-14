package com.socialflow.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 评估任务创建请求 DTO —— 用于创建 AI 文案 A/B 对比评估任务的接口入参
 *
 * 【作用】封装创建评估任务时需要的全部参数：
 *   包括两套待对比的 AI 配置（configA / configB）和一组测试主题（testTopics）。
 *   系统会用这两套配置分别为每个测试主题生成文案，然后由 AI 评审打分对比。
 *
 * 【对应 API 接口】
 *   POST /api/eval/task  —— 创建评估任务
 *
 * 【使用场景】
 *   用户在"评估中心"页面填写任务名称、两套 AI 配置和多个测试主题后，
 *   点击"开始评估"提交。系统异步执行评估，完成后用户可查看评估报告。
 */
@Data
public class EvalTaskCreateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评估任务名称（必填）
     *
     * 用户为本次评估起的名称，便于后续识别。
     * 示例："GPT-4o vs Claude-3 小红书种草文案对比"
     */
    @NotBlank
    private String name;

    /**
     * A 组 AI 配置（必填）
     *
     * 第一套 AI 生成配置，使用 Map 格式传递。
     * 必须包含的键值对：
     * - "model": AI 模型名称，如 "gpt-4o"
     * - "templateId": Prompt 模板 ID，如 1
     * - "temperature": 温度参数，如 0.7
     */
    @NotNull
    private Map<String, Object> configA;  // {model, templateId, temperature}

    /**
     * B 组 AI 配置（必填）
     *
     * 第二套 AI 生成配置，与 configA 对照。格式同 configA。
     */
    @NotNull
    private Map<String, Object> configB;

    /**
     * 测试主题列表（必填，至少一个）
     *
     * 需要测试的主题列表。每个主题会分别使用 A、B 两套配置生成文案。
     * 主题越多，评估越全面，但耗时和 Token 消耗也越多。
     */
    @NotEmpty
    private List<TestTopic> testTopics;

    /**
     * 测试主题内部类 —— 描述单个测试用例的参数
     *
     * 【作用】定义一个测试主题所需的信息，包括主题名称、目标平台和关键词。
     * 评估系统会用这些参数来调用 AI 生成文案。
     */
    @Data
    public static class TestTopic implements Serializable {

        /** 序列化版本号 */
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 测试主题
         *
         * 本轮测试要生成文案的主题。
         * 示例："春季护肤好物推荐"
         */
        private String topic;

        /**
         * 目标平台
         *
         * 本轮测试的目标平台。
         * 示例："XIAOHONGSHU"
         */
        private String platform;

        /**
         * 关键词列表
         *
         * 本轮测试中希望文案包含的关键词。
         * 示例：["护肤", "防晒", "平价"]
         */
        private List<String> keywords;
    }
}
