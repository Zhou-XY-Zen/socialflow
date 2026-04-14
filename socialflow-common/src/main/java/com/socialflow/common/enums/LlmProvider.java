package com.socialflow.common.enums;

/**
 * 大语言模型（LLM）提供商枚举
 *
 * 【作用】定义系统支持的AI模型提供商。
 *   系统支持多个模型提供商，用户可以根据需求选择不同的模型来生成文案。
 *   不同提供商的模型在能力、价格、速度上各有差异。
 *
 * 【使用场景】
 *   - 用户在前端选择使用哪个模型生成文案
 *   - 后端根据选择的提供商，调用对应的API接口
 *   - 配额和计费按不同提供商分别统计
 *
 * 【各提供商简介】
 *   - DEEPSEEK：深度求索，国产大模型，性价比高
 *   - QWEN：通义千问，阿里巴巴的大模型
 *   - OPENAI：ChatGPT背后的公司，GPT系列模型
 *   - CLAUDE：Anthropic公司的Claude系列模型
 */
public enum LlmProvider {

    /** 深度求索（DeepSeek）——国产开源大模型，性价比高，适合大批量文案生成 */
    DEEPSEEK,

    /** 通义千问（Qwen）——阿里巴巴的大模型，中文能力强 */
    QWEN,

    /** OpenAI——GPT系列模型（GPT-4等），英文能力强，生态成熟 */
    OPENAI,

    /** Claude——Anthropic公司的大模型，擅长长文本和分析任务 */
    CLAUDE,

    /** 智谱AI（GLM）——清华系大模型，GLM-4系列，中文能力优秀 */
    GLM
}
