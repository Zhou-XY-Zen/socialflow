package com.socialflow.service.ai.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.dao.mapper.PromptTemplateMapper;
import com.socialflow.model.entity.PromptTemplate;
import com.socialflow.service.ai.llm.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 提示词模板渲染管道服务 —— PromptService 的完整实现。
 *
 * 【核心职责】
 *
 * 将数据库中存储的提示词模板渲染为可以直接发送给 LLM 的 {@link ChatMessage} 列表。
 * 整个渲染过程形成一条"管道"（Pipeline），依次完成以下步骤：
 *   1. 加载模板：根据 templateId 或平台默认配置从数据库获取模板
 *   2. 变量替换：将模板中的 {@code {{variable}}} 占位符替换为实际值
 *   3. RAG 上下文注入：将检索到的参考资料追加到用户提示词中
 *   4. Few-Shot 示例追加：将示例对话作为 user/assistant 消息对添加到列表
 *   5. 构建消息列表：按 system -> few-shot pairs -> user 的顺序组织最终消息
 *
 * 【在系统中的位置】
 *
 * 本服务位于业务层和 LLM 调用层之间。业务层提供原始参数（主题、平台、关键词等），
 * 本服务将其渲染为标准的 ChatMessage 列表，再传给
 * {@link com.socialflow.service.ai.llm.LlmProviderService} 执行生成。
 *
 * 【模板加载优先级】
 *   1. 如果指定了 templateId，则直接按 ID 加载
 *   2. 如果 templateId 为 null，则查找该平台的默认系统模板（is_system=1，按 sort_order 排序取第一个）
 *   3. 如果数据库中没有任何匹配的模板，则使用硬编码的兜底提示词
 */
@Service
public class PromptServiceImpl implements PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptServiceImpl.class);

    /**
     * 兜底系统提示词 —— 当数据库中找不到任何匹配模板时使用。
     *
     * 提供一个通用的社媒文案写手角色定义，确保即使模板缺失，
     * 系统也能正常调用 LLM 生成内容。
     */
    private static final String FALLBACK_SYSTEM_PROMPT =
            "你是一名专业的社媒文案写手，擅长为各大社交媒体平台撰写高质量的原创内容。" +
            "你熟悉小红书、抖音、微信公众号、朋友圈等平台的内容风格和受众偏好，" +
            "能够根据用户需求生成吸引人的、符合平台调性的文案。" +
            "【重要】你必须严格遵守用户指定的字数限制，绝对不要超出。";

    /**
     * 兜底用户提示词模板 —— 当模板中没有定义 userPromptTemplate 时使用。
     *
     * 包含主题、关键词、字数、平台等完整变量，确保字数限制生效。
     */
    private static final String FALLBACK_USER_PROMPT_TEMPLATE =
            "请帮我撰写一篇关于「{{topic}}」的{{platform}}文案。" +
            "{{#keywords}}关键词：{{keywords}}。{{/keywords}}" +
            "【字数硬限制】全文必须控制在 {{wordCount}} 字以内，严禁超出！这是最重要的要求。";

    /** 提示词模板 Mapper —— 仍保留以兼容 preview() 中现有代码的少量直接查询；新写代码请走 lookup */
    private final PromptTemplateMapper promptTemplateMapper;

    /** 模板查询的缓存层（Spring 代理自动应用 @Cacheable） */
    private final PromptTemplateLookup templateLookup;

    public PromptServiceImpl(PromptTemplateMapper promptTemplateMapper,
                             PromptTemplateLookup templateLookup) {
        this.promptTemplateMapper = promptTemplateMapper;
        this.templateLookup = templateLookup;
    }

    /**
     * 将模板渲染为可直接发送给 LLM 的消息数组 —— 核心渲染方法。
     *
     * 【完整渲染流程】
     *   1. 加载模板（按 ID 或平台默认）
     *   2. 获取系统提示词（systemPrompt）
     *   3. 获取用户提示词模板（userPromptTemplate）并执行变量替换
     *   4. 如果有 RAG 上下文，追加到用户提示词末尾
     *   5. 构建系统消息 + 用户消息
     *   6. 如果模板包含 Few-Shot 示例，解析并插入到消息列表中
     *   7. 返回完整的消息列表
     *
     * @param templateId 提示词模板 ID；传 null 则使用该平台的默认模板
     * @param platform   目标平台编码（如 "XIAOHONGSHU"、"DOUYIN" 等）
     * @param variables  变量映射表，key 为模板中的变量名，value 为实际值
     * @param ragContext 可选的 RAG 检索上下文字符串；不需要时传 null
     * @return 渲染后的 ChatMessage 列表
     */
    @Override
    public List<ChatMessage> render(Long templateId,
                                    String platform,
                                    Map<String, Object> variables,
                                    String ragContext) {

        // ===== 第一步：加载模板 =====
        PromptTemplate template = loadTemplate(templateId, platform);

        // ===== 第二步：获取系统提示词 =====
        String systemPrompt;
        if (template != null && template.getSystemPrompt() != null && !template.getSystemPrompt().isBlank()) {
            systemPrompt = template.getSystemPrompt();
        } else {
            // 没有模板或模板中未定义系统提示词，使用兜底默认值
            systemPrompt = FALLBACK_SYSTEM_PROMPT;
            log.debug("【模板渲染】未找到系统提示词，使用兜底默认值");
        }

        // ===== 第三步：获取并渲染用户提示词 =====
        String userPromptTemplate;
        if (template != null && template.getUserPromptTemplate() != null
                && !template.getUserPromptTemplate().isBlank()) {
            userPromptTemplate = template.getUserPromptTemplate();
        } else {
            // 没有模板或模板中未定义用户提示词模板，使用兜底默认值
            userPromptTemplate = FALLBACK_USER_PROMPT_TEMPLATE;
            log.debug("【模板渲染】未找到用户提示词模板，使用兜底默认值");
        }

        // 执行变量替换：将 {{variable}} 占位符替换为实际值
        String renderedUserPrompt = replaceVariables(userPromptTemplate, variables);

        // ===== 第四步：注入 RAG 上下文（如果有的话） =====
        if (ragContext != null && !ragContext.isBlank()) {
            renderedUserPrompt = appendRagContext(renderedUserPrompt, ragContext);
            log.debug("【模板渲染】已注入 RAG 参考资料，长度={}字符", ragContext.length());
        }

        // ===== 第五步：构建消息列表 =====
        List<ChatMessage> messages = new ArrayList<>();

        // 添加系统消息（设定 AI 的角色和行为准则）
        messages.add(ChatMessage.system(systemPrompt));

        // ===== 第六步：解析并插入 Few-Shot 示例 =====
        if (template != null && template.getFewShotExamples() != null
                && !template.getFewShotExamples().isBlank()) {
            List<ChatMessage> fewShotMessages = parseFewShotExamples(template.getFewShotExamples());
            if (!fewShotMessages.isEmpty()) {
                messages.addAll(fewShotMessages);
                log.debug("【模板渲染】已添加 {} 条 Few-Shot 示例消息", fewShotMessages.size());
            }
        }

        // 添加用户消息（包含变量替换和 RAG 上下文的最终用户提示词）
        messages.add(ChatMessage.user(renderedUserPrompt));

        log.debug("【模板渲染完成】模板ID={}, 平台={}, 总消息数={}", templateId, platform, messages.size());

        return messages;
    }

    /**
     * 简单的行内模板渲染 —— 仅执行 {{variable}} 占位符替换。
     *
     * 适用于不需要构建完整对话消息结构的场景，
     * 例如渲染通知文本、日志模板、邮件标题等。
     *
     * @param template  包含 {@code {{variable}}} 占位符的模板字符串
     * @param variables 变量映射表
     * @return 替换占位符后的纯文本字符串
     */
    @Override
    public String renderInline(String template, Map<String, Object> variables) {
        if (template == null || template.isBlank()) {
            return "";
        }
        return replaceVariables(template, variables);
    }

    // ============================== 私有辅助方法 ==============================

    /**
     * 从数据库加载提示词模板。
     *
     * 【加载优先级】
     *   1. 如果 templateId 不为 null，直接按 ID 查询
     *   2. 如果 templateId 为 null 但 platform 有值，查找该平台的默认系统模板
     *      查询条件：platform 匹配 AND is_system = 1，按 sort_order 升序排列取第一条
     *   3. 如果都没找到，返回 null（调用方会使用兜底默认值）
     *
     * @param templateId 模板 ID，可为 null
     * @param platform   平台编码，可为 null
     * @return 匹配的模板实体；未找到时返回 null
     */
    /**
     * 模板预览（Wave 4.4）—— 把 systemPrompt 和 userPromptTemplate 用 sampleVars
     * 试渲染一遍，并扫描所有 {{var}} / {{#var}} 出现的变量名，给出"缺失/未使用"诊断。
     */
    @Override
    public com.socialflow.model.vo.TemplatePreviewVO preview(Long templateId, Map<String, Object> sampleVars) {
        PromptTemplate template = loadTemplate(templateId, null);
        if (template == null) {
            throw new com.socialflow.common.exception.NotFoundException("模板不存在: " + templateId);
        }
        String sysTpl = template.getSystemPrompt() != null ? template.getSystemPrompt() : "";
        String userTpl = template.getUserPromptTemplate() != null ? template.getUserPromptTemplate() : "";

        // 1. 扫描所有占位符变量名
        java.util.Set<String> declared = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("\\{\\{(\\w+)}}").matcher(sysTpl + "\n" + userTpl);
        while (m1.find()) declared.add(m1.group(1));
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("\\{\\{#(\\w+)}}").matcher(sysTpl + "\n" + userTpl);
        while (m2.find()) declared.add(m2.group(1));

        Map<String, Object> safeVars = sampleVars == null ? java.util.Map.of() : sampleVars;

        // 2. 计算 used / missing / unused
        java.util.List<String> missing = declared.stream()
                .filter(v -> !safeVars.containsKey(v) || safeVars.get(v) == null
                        || (safeVars.get(v) instanceof String && ((String) safeVars.get(v)).isEmpty()))
                .toList();
        java.util.List<String> used = declared.stream()
                .filter(v -> safeVars.containsKey(v) && safeVars.get(v) != null)
                .toList();
        java.util.List<String> unused = safeVars.keySet().stream()
                .filter(v -> !declared.contains(v))
                .toList();

        // 3. 渲染（缺失变量按原样保留，便于前端高亮）
        String renderedSys = replaceVariables(sysTpl, safeVars);
        String renderedUser = replaceVariables(userTpl, safeVars);

        return com.socialflow.model.vo.TemplatePreviewVO.builder()
                .renderedSystemPrompt(renderedSys)
                .renderedUserPrompt(renderedUser)
                .declaredVariables(new java.util.ArrayList<>(declared))
                .usedVariables(used)
                .missingVariables(missing)
                .unusedVariables(unused)
                .build();
    }

    private PromptTemplate loadTemplate(Long templateId, String platform) {
        // 优先按 ID 查找（命中 promptTemplate cache TTL=10min）
        if (templateId != null) {
            PromptTemplate template = templateLookup.findById(templateId);
            if (template != null) {
                log.debug("【模板加载】按ID加载模板成功，ID={}, 名称={}", templateId, template.getTemplateName());
                return template;
            }
            log.warn("【模板加载】指定的模板ID={}不存在，将尝试加载平台默认模板", templateId);
        }

        // 按平台查找默认系统模板
        if (platform != null && !platform.isBlank()) {
            PromptTemplate template = templateLookup.findDefaultByPlatform(platform);
            if (template != null) {
                log.debug("【模板加载】按平台加载默认模板成功，平台={}, 名称={}", platform, template.getTemplateName());
                return template;
            }
            log.debug("【模板加载】平台={}没有默认系统模板，将使用兜底提示词", platform);
        }

        // 没有找到任何匹配的模板
        log.debug("【模板加载】未找到任何匹配模板（templateId={}, platform={}），将使用兜底提示词",
                templateId, platform);
        return null;
    }

    /**
     * 执行模板变量替换 —— 将 {@code {{variable}}} 占位符替换为实际值。
     *
     * 【替换规则】
     *   - 遍历 variables Map 中的所有键值对
     *   - 将模板中的 {@code {{key}}} 替换为对应的 value（转为字符串）
     *   - 如果某个占位符在 variables 中没有对应的值，保留原始占位符不变
     *   - value 为 null 时替换为空字符串
     *
     * 【简单条件块处理】
     *   - 支持 {@code {{#var}}...{{/var}}} 形式的简单条件块
     *   - 如果变量存在且非空，保留块内内容（并继续替换内部的 {{var}}）
     *   - 如果变量不存在或为空，移除整个块（包括块内内容）
     *
     * @param template  包含占位符的模板字符串
     * @param variables 变量映射表；可为 null（此时直接返回原模板）
     * @return 替换后的字符串
     */
    private String replaceVariables(String template, Map<String, Object> variables) {
        if (template == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            // 没有变量时清理条件块标签，移除整个条件块
            return cleanConditionalBlocks(template);
        }

        String result = template;

        // 先处理条件块 {{#var}}...{{/var}}
        result = processConditionalBlocks(result, variables);

        // 再执行简单变量替换 {{variable}} -> value
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // 将 value 转为字符串（null 视为空字符串）
            String strValue = (value != null) ? value.toString() : "";
            // 替换所有 {{key}} 占位符
            result = result.replace("{{" + key + "}}", strValue);
        }

        return result;
    }

    /**
     * 处理模板中的条件块。
     *
     * 条件块格式：{@code {{#varName}}当变量存在时显示的内容{{/varName}}}
     *   - 如果 varName 在 variables 中存在且非空 -> 保留块内内容
     *   - 如果 varName 不存在或为空 -> 移除整个块
     *
     * @param template  模板字符串
     * @param variables 变量映射表
     * @return 处理后的字符串
     */
    private String processConditionalBlocks(String template, Map<String, Object> variables) {
        String result = template;
        // 使用正则匹配所有条件块
        // 模式：{{#varName}}...内容...{{/varName}}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\{\\{#(\\w+)}}(.*?)\\{\\{/\\1}}", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(result);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);     // 变量名
            String blockContent = matcher.group(2); // 块内内容
            Object value = variables.get(varName);

            if (value != null && !value.toString().isBlank()) {
                // 变量存在且非空：保留块内内容（后续的简单替换会处理里面的 {{var}}）
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(blockContent));
            } else {
                // 变量不存在或为空：移除整个块
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 清理没有变量值的条件块 —— 当 variables 为空时，移除所有条件块。
     *
     * @param template 模板字符串
     * @return 清理后的字符串
     */
    private String cleanConditionalBlocks(String template) {
        // 移除所有 {{#var}}...{{/var}} 条件块
        return template.replaceAll("\\{\\{#\\w+}}.*?\\{\\{/\\w+}}", "");
    }

    /**
     * 将 RAG 检索上下文追加到用户提示词末尾。
     *
     * 在用户提示词后添加一个"参考资料"区域，让 LLM 在生成内容时参考检索到的知识。
     * 使用清晰的分隔标记，帮助 LLM 区分用户指令和参考资料。
     *
     * @param userPrompt 已完成变量替换的用户提示词
     * @param ragContext RAG 检索到的参考资料文本
     * @return 追加了参考资料的用户提示词
     */
    private String appendRagContext(String userPrompt, String ragContext) {
        return userPrompt +
                "\n\n---\n" +
                "【参考资料】\n" +
                "以下是与本次任务相关的参考资料，请在生成内容时适当参考，但不要直接照搬：\n\n" +
                ragContext;
    }

    /**
     * 解析 Few-Shot 示例 JSON，转换为 ChatMessage 列表。
     *
     * 【JSON 格式说明】
     *
     * Few-Shot 示例以 JSON 数组存储在数据库中，支持两种格式：
     *
     * 格式一（推荐）—— 包含 input/output 字段的对象数组：
     * {@code [{"input": "用户输入示例", "output": "期望的AI输出示例"}, ...]}
     * 每组示例会被转换为一对 user + assistant 消息。
     *
     * 格式二 —— 直接的消息列表：
     * {@code [{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}, ...]}
     * 直接作为消息添加到列表中。
     *
     * @param fewShotJson 数据库中存储的 Few-Shot 示例 JSON 字符串
     * @return ChatMessage 列表（user/assistant 消息对）；解析失败时返回空列表
     */
    private List<ChatMessage> parseFewShotExamples(String fewShotJson) {
        List<ChatMessage> messages = new ArrayList<>();
        try {
            JsonNode arrayNode = JsonUtil.mapper().readTree(fewShotJson);
            if (!arrayNode.isArray()) {
                log.warn("【Few-Shot 解析】fewShotExamples 不是 JSON 数组，跳过解析");
                return messages;
            }

            for (JsonNode node : arrayNode) {
                // 格式一：{"input": "...", "output": "..."}
                if (node.has("input") && node.has("output")) {
                    String input = node.get("input").asText("");
                    String output = node.get("output").asText("");
                    if (!input.isBlank()) {
                        messages.add(ChatMessage.user(input));
                    }
                    if (!output.isBlank()) {
                        messages.add(ChatMessage.assistant(output));
                    }
                }
                // 格式二：{"role": "user/assistant", "content": "..."}
                else if (node.has("role") && node.has("content")) {
                    String role = node.get("role").asText("");
                    String content = node.get("content").asText("");
                    if (!role.isBlank() && !content.isBlank()) {
                        messages.add(new ChatMessage(role, content));
                    }
                }
            }

            log.debug("【Few-Shot 解析】成功解析 {} 条示例消息", messages.size());
        } catch (Exception e) {
            // Few-Shot 解析失败不应阻断整个渲染流程，记录警告后返回空列表
            log.warn("【Few-Shot 解析】解析失败，跳过 Few-Shot 示例。原始JSON: {}, 错误: {}",
                    fewShotJson, e.getMessage());
        }
        return messages;
    }
}
