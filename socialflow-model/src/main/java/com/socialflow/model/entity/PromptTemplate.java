package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Prompt 模板实体类 —— 对应数据库表 `prompt_template`
 *
 * 【作用】存储用于指导 AI 生成文案的 Prompt 模板。
 *   每个模板包含系统提示词（system prompt）和用户提示词模板（user prompt template），
 *   以及变量定义、少样本示例等，构成一套完整的"生成配方"。
 *
 * 【为什么需要它】
 *   Prompt 的质量直接影响 AI 生成文案的效果。通过模板化管理，用户可以：
 *   1. 复用高质量的 Prompt，避免每次手动编写
 *   2. 针对不同平台（小红书、抖音等）和不同场景（种草、测评等）定制专属模板
 *   3. 系统预置一套通用模板，用户也可以自定义创建
 *
 * 【关联关系】
 *   - prompt_template.user_id → sys_user.id （创建者，系统模板该字段为空）
 *   - content.template_id → prompt_template.id （文案生成时使用的模板）
 *
 * 【使用场景】
 *   - 用户在"模板管理"页面查看、创建、编辑模板
 *   - 生成文案时选择一个模板作为生成的基础配方
 *   - AI 调用时将模板中的变量替换为实际值后发送给大模型
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_template")
public class PromptTemplate extends BaseEntity {

    /**
     * 模板名称
     *
     * 用于标识这个模板的用途，方便用户在列表中选择。
     * 示例："小红书种草笔记模板"、"抖音短视频脚本模板"
     */
    private String templateName;

    /**
     * 目标平台
     *
     * 该模板适用于哪个社交媒体平台。
     * 可选值："XIAOHONGSHU"（小红书）、"DOUYIN"（抖音）、"WECHAT_MOMENT"（朋友圈）、"WECHAT_MP"（微信公众号）等。
     */
    private String platform;

    /**
     * 模板分类
     *
     * 按业务场景分类，帮助用户快速筛选。
     * 示例："种草"、"测评"、"教程"、"促销"、"日常分享"
     */
    private String category;

    /**
     * 系统提示词（System Prompt）
     *
     * 发送给大模型的 system 角色消息，用于设定 AI 的角色和行为规则。
     * 示例："你是一位专业的小红书博主，擅长写种草笔记……"
     */
    private String systemPrompt;

    /**
     * 用户提示词模板（User Prompt Template）
     *
     * 发送给大模型的 user 角色消息模板，包含占位变量（如 {topic}、{keywords}）。
     * 实际调用时会将变量替换为用户输入的真实值。
     * 示例："请以{topic}为主题，围绕{keywords}关键词，写一篇{platform}风格的文案"
     */
    private String userPromptTemplate;

    /**
     * 变量定义（JSON 数组）
     *
     * 定义模板中用到的占位变量及其属性。
     * JSON 格式：[{"name": "topic", "required": true, "default": ""}, ...]
     * - name: 变量名，对应 userPromptTemplate 中的 {变量名}
     * - required: 是否必填
     * - default: 默认值
     */
    private String variables;

    /**
     * 少样本示例（Few-shot Examples，JSON 数组）
     *
     * 提供给大模型的参考示例，帮助 AI 理解期望的输出风格和格式。
     * JSON 格式：包含多组输入-输出对，作为 Prompt 的一部分发送给大模型。
     */
    private String fewShotExamples;

    /**
     * 期望的输出格式
     *
     * 指定 AI 生成内容的格式要求。
     * 示例："markdown"、"plain_text"、"json"
     */
    private String outputFormat;

    /**
     * 是否为系统预置模板
     *
     * 0 = 用户自建模板，1 = 系统预置模板。
     * 系统预置模板对所有用户可见且不可删除，用户自建模板仅对创建者可见。
     */
    private Integer isSystem;

    /**
     * 创建者用户 ID
     *
     * 关联 sys_user.id。系统预置模板该字段为空（null），
     * 用户自建模板记录创建该模板的用户 ID。
     */
    private Long userId;

    /**
     * 排序序号
     *
     * 用于控制模板在列表中的显示顺序，数字越小越靠前。
     */
    private Integer sortOrder;
}
