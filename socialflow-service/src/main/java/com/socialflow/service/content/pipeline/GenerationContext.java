package com.socialflow.service.content.pipeline;

import com.socialflow.model.dto.ContentGenerateDTO;
import com.socialflow.model.entity.Content;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmResponse;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 内容生成 Pipeline 的执行上下文 —— 所有 step 共享的"工作内存"。
 *
 * <p>每个 step 从这里读自己需要的输入、写自己产出的输出。这样 step 之间通过共享对象
 * 传值，不需要硬编码上一步返回什么 / 下一步接什么 —— 后续插入新 step 不必修改其他人。</p>
 *
 * <p>字段按 pipeline 中的填充顺序组织：</p>
 * <ol>
 *   <li>{@link #userId} / {@link #dto} —— 入口阶段就提供，整条 pipeline 共享</li>
 *   <li>{@link #ragContext} —— RagRetrievalStep 写入</li>
 *   <li>{@link #variables} / {@link #messages} —— PromptRenderStep 写入</li>
 *   <li>{@link #config} / {@link #provider} —— LlmInvocationStep 准备</li>
 *   <li>{@link #response} —— LlmInvocationStep 写入</li>
 *   <li>{@link #guardrailWarnings} —— OutputGuardrailStep 累积</li>
 *   <li>{@link #savedContent} —— PersistenceStep 写入</li>
 * </ol>
 */
@Data
public class GenerationContext {

    /** 当前用户 ID */
    private final Long userId;

    /** 客户端原始请求（不可变，所有 step 共享读取） */
    private final ContentGenerateDTO dto;

    /** RAG 检索得到的参考资料文本；未启用 RAG 时为 null */
    private String ragContext;

    /** PromptRenderStep 计算出的模板变量映射 */
    private Map<String, Object> variables;

    /** 渲染后的对话消息列表，发给 LLM */
    private List<ChatMessage> messages;

    /** 本次 LLM 调用的运行时配置（含 provider / model / API Key 等） */
    private LlmConfig config;

    /** 本次实际选用的 provider 名称（如 "deepseek"），用于日志和 fallback 标记 */
    private String providerUsed;

    /** LLM 调用的完整响应（含正文 + token 用量 + fallback 标记） */
    private LlmResponse response;

    /** 输出护栏命中的警告（不致命，只收集，最终返回前端） */
    private final List<String> guardrailWarnings = new ArrayList<>();

    /** 持久化后的 Content 实体（含 DB 生成的 ID） */
    private Content savedContent;

    public GenerationContext(Long userId, ContentGenerateDTO dto) {
        this.userId = userId;
        this.dto = dto;
    }
}
