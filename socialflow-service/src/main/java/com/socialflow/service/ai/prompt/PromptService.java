package com.socialflow.service.ai.prompt;

import com.socialflow.service.ai.llm.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * Prompt（提示词）模板渲染管道服务接口。
 *
 * 【什么是 Prompt Engineering（提示词工程）？】
 *
 * Prompt Engineering 是一门设计和优化"提示词"（即发送给 LLM 的文本指令）的技术。
 * 高质量的提示词能让 LLM 生成更准确、更符合预期的内容。提示词工程包括：
 *     - 系统提示词（System Prompt）：定义 AI 的角色和行为规范
 *     - 模板变量：用占位符（如 {@code {{topic}}}）实现动态内容填充
 *     - Few-Shot 示例：提供几组"输入-输出"样例，让 AI 学习期望的格式
 *     - RAG 上下文注入：将检索到的参考资料嵌入提示词，帮助 AI 生成有据可依的回答
 *
 * 【模板渲染管道（Pipeline）的完整流程】
 *     - 加载模板：根据模板 ID 或平台默认配置，从数据库加载提示词模板
 *     - 解析变量定义：识别模板中所有 {@code {{variable}}} 占位符
 *     - 校验必填变量：确保所有必填变量都在传入的 variables 中有值
 *     - 替换占位符：将 {@code {{variable}}} 替换为实际值
 *     - 注入 RAG 上下文：如果有检索到的参考资料，将其插入用户提示词的上下文区域
 *     - 追加 Few-Shot 示例：以 assistant/user 消息对的形式追加示例
 *     - 返回最终消息列表：输出可以直接发送给 LLM 的 {@code messages[]} 数组
 *
 * 【在系统中的位置】
 *
 * 本服务位于业务层和 LLM 调用层之间。业务层提供原始参数（主题、平台、关键词等），
 * 本服务将其渲染为标准的 {@link ChatMessage} 列表，再传给
 * {@link com.socialflow.service.ai.llm.LlmProviderService} 执行生成。
 */
public interface PromptService {

    /**
     * 将模板渲染为可直接发送给 LLM 的消息数组。
     *
     * 这是提示词渲染的核心方法，完成从模板到最终对话消息的完整转换。
     *
     * @param templateId 提示词模板 ID；传 null 则使用该平台的默认模板
     * @param platform   目标平台编码（如 "xiaohongshu"、"douyin" 等），
     *                   不同平台可能使用不同风格的提示词模板
     * @param variables  变量映射表，key 为模板中的变量名，value 为实际值；
     *                   例如 {@code {topic: "咖啡探店", tone: "活泼"}}
     * @param ragContext 可选的 RAG 检索上下文字符串（已拼接好的参考资料文本）；
     *                   如果不需要 RAG 辅助则传 null
     * @return 渲染后的 ChatMessage 列表，按 system → user → (few-shot pairs) 排列
     */
    List<ChatMessage> render(Long templateId,
                             String platform,
                             Map<String, Object> variables,
                             String ragContext);

    /**
     * 简单的行内模板渲染（不生成消息结构）。
     *
     * 适用于只需要单纯替换占位符、不需要构造完整对话消息的场景，
     * 例如渲染日志模板、通知文本等。
     *
     * @param template  包含 {@code {{variable}}} 占位符的模板字符串
     * @param variables 变量映射表
     * @return 替换占位符后的纯文本字符串
     */
    String renderInline(String template, Map<String, Object> variables);

    /**
     * 模板预览（Wave 4.4）—— 用样例变量"试渲染"模板，并返回变量诊断（缺失/未使用）。
     *
     * <p>不写库，纯函数。前端在用户编辑模板时可调用，看渲染效果 + 验证完整性。</p>
     *
     * @param templateId 模板 ID（必填）
     * @param sampleVars 用户提供的样例变量
     * @return 包含渲染结果和变量诊断的 VO
     */
    com.socialflow.model.vo.TemplatePreviewVO preview(Long templateId, Map<String, Object> sampleVars);
}
