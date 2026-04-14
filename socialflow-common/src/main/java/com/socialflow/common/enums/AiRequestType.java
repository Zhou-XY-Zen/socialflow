package com.socialflow.common.enums;

/**
 * AI请求类型枚举
 *
 * 【作用】标识每次AI调用的目的/类型，用于：
 *   1. 使用量统计：按类型分别统计AI调用次数和Token消耗
 *   2. 配额控制：不同类型的请求可能有不同的配额限制
 *   3. 日志记录：方便排查问题时快速定位是哪种AI操作出了错
 *   4. 计费分析：分析各功能的AI资源消耗情况
 *
 * 【使用场景】每次调用AI接口时，都会指定一个 AiRequestType，
 *   例如：aiService.call(prompt, LlmProvider.DEEPSEEK, AiRequestType.GENERATE);
 */
public enum AiRequestType {

    /** 生成文案——根据用户需求从零生成一篇新文案 */
    GENERATE,

    /** 改写文案——对已有文案进行改写、润色、调整语气等 */
    REWRITE,

    /** 生成标题——为已有的文案正文生成标题 */
    TITLE,

    /** 生成话题标签（Hashtag）——为文案推荐合适的 #话题标签# */
    HASHTAG,

    /** 护栏检查——调用AI对内容进行安全性/合规性检查 */
    GUARDRAIL,

    /** 评估打分——调用AI对生成的文案质量进行评分 */
    EVAL,

    /** RAG查询——检索增强生成，先从知识库检索相关内容再结合AI生成回答 */
    RAG_QUERY,

    /** Agent多轮对话——多个AI角色协作完成复杂的文案生成任务 */
    AGENT
}
