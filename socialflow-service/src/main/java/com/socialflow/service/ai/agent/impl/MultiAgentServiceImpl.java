package com.socialflow.service.ai.agent.impl;

import com.socialflow.common.enums.AgentRole;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.model.dto.MultiAgentGenerateDTO;
import com.socialflow.service.ai.agent.AgentContext;
import com.socialflow.service.ai.agent.MultiAgentService;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmResponse;
import com.socialflow.service.ai.llm.LlmRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多智能体协作服务实现——编排 PLANNER → WRITER → REVIEWER → OPTIMIZER 四阶段流水线。
 *
 * 【工作流程】
 *   1. 根据 DTO 构建共享上下文 AgentContext
 *   2. 依次执行 PLANNER（策划）→ WRITER（写作）→ REVIEWER（审核）→ OPTIMIZER（优化）
 *   3. 若 Reviewer 判定未通过，循环回到 OPTIMIZER 重新优化（最多 maxRounds 轮）
 *   4. 每个阶段通过 SSE 推送阶段事件和 token 增量，前端实时展示进度
 */
@Slf4j
@Service
public class MultiAgentServiceImpl implements MultiAgentService {

    /** LLM 请求路由器，根据提供者枚举分发到具体实现 */
    private final LlmRouter llmRouter;

    /** 系统级 API 密钥，用于内部 Agent 调用 LLM */
    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    /** 默认 LLM 提供者编码，如 DEEPSEEK、QWEN */
    @Value("${socialflow.ai.default-provider:DEEPSEEK}")
    private String defaultProvider;

    // ==================== 各 Agent 的系统提示词 ====================

    /** PLANNER（策划者）系统提示词——输出 JSON 格式的写作计划 */
    private static final String PLANNER_PROMPT =
            "你是内容策划专家。分析用户需求，输出JSON格式的写作计划：{angle, structure, keyPoints, wordCount}";

    /** WRITER（写作者）系统提示词模板——根据策划方案撰写文案 */
    private static final String WRITER_PROMPT_TEMPLATE =
            "你是专业文案写手。根据以下策划方案撰写文案：%s";

    /** REVIEWER（审核者）系统提示词——输出 JSON 格式的审核结论 */
    private static final String REVIEWER_PROMPT =
            "你是内容审核专家。审核以下文案，输出JSON：{passed:true/false, issues:[], suggestions:[]}";

    /** OPTIMIZER（优化者）系统提示词模板——根据审核意见优化文案 */
    private static final String OPTIMIZER_PROMPT_TEMPLATE =
            "你是文案优化专家。根据审核意见优化以下文案：%s 审核意见：%s";

    public MultiAgentServiceImpl(LlmRouter llmRouter) {
        this.llmRouter = llmRouter;
    }

    /**
     * 启动多智能体链并流式输出中间阶段和最终结果。
     *
     * 使用 Flux.create() 手动控制事件推送，每个阶段先发送阶段通知事件，
     * 然后调用 LLM 获取完整结果并逐段推送 token 事件。
     *
     * @param userId 发起请求的用户 ID
     * @param dto    多智能体生成请求参数
     * @return 流式 JSON 字符串，包含 stage 事件和 token 事件
     */
    @Override
    public Flux<String> runStream(Long userId, MultiAgentGenerateDTO dto) {
        return Flux.create(sink -> {
            try {
                // ========== 1. 构建共享上下文 ==========
                String keywordsStr = dto.getKeywords() != null
                        ? String.join(",", dto.getKeywords()) : "";
                AgentContext ctx = AgentContext.builder()
                        .userId(userId)
                        .topic(dto.getTopic())
                        .platform(dto.getPlatform())
                        .keywords(keywordsStr)
                        .round(1)
                        .build();

                // 构建 LLM 调用配置
                LlmConfig config = buildLlmConfig(userId, dto);
                int maxRounds = dto.getMaxRounds() != null ? dto.getMaxRounds() : 3;

                // ========== 2. 阶段一：PLANNER（策划） ==========
                emitStageEvent(sink, AgentRole.PLANNER, "策划中...");
                String planUserMsg = buildPlannerUserMessage(ctx);
                String plan = callLlmSync(PLANNER_PROMPT, planUserMsg, config);
                ctx.setPlan(plan);
                emitTokenEvent(sink, plan);
                log.info("【多Agent】PLANNER 阶段完成, userId={}, topic={}", userId, ctx.getTopic());

                // ========== 3. 阶段二：WRITER（写作） ==========
                emitStageEvent(sink, AgentRole.WRITER, "写作中...");
                String writerSystemPrompt = String.format(WRITER_PROMPT_TEMPLATE, ctx.getPlan());
                String writerUserMsg = buildWriterUserMessage(ctx);
                String draft = callLlmSync(writerSystemPrompt, writerUserMsg, config);
                ctx.setDraft(draft);
                emitTokenEvent(sink, draft);
                log.info("【多Agent】WRITER 阶段完成, userId={}", userId);

                // ========== 4. 阶段三：REVIEWER（审核）+ 循环优化 ==========
                for (int round = 1; round <= maxRounds; round++) {
                    ctx.setRound(round);

                    // 审核阶段
                    emitStageEvent(sink, AgentRole.REVIEWER, "审核中（第" + round + "轮）...");
                    String reviewUserMsg = "请审核以下文案：\n" + ctx.getDraft();
                    String reviewResult = callLlmSync(REVIEWER_PROMPT, reviewUserMsg, config);
                    ctx.setReviewResult(reviewResult);
                    emitTokenEvent(sink, reviewResult);
                    log.info("【多Agent】REVIEWER 第{}轮完成, userId={}", round, userId);

                    // 判断审核是否通过
                    if (isReviewPassed(reviewResult)) {
                        log.info("【多Agent】审核通过，跳过优化, round={}", round);
                        break;
                    }

                    // 审核未通过，进入 OPTIMIZER（优化）阶段
                    emitStageEvent(sink, AgentRole.OPTIMIZER, "优化中（第" + round + "轮）...");
                    String optimizerSystemPrompt = String.format(
                            OPTIMIZER_PROMPT_TEMPLATE, ctx.getDraft(), ctx.getReviewResult());
                    String optimizerUserMsg = "请根据上述审核意见优化文案。";
                    String optimizedDraft = callLlmSync(optimizerSystemPrompt, optimizerUserMsg, config);
                    ctx.setDraft(optimizedDraft);
                    emitTokenEvent(sink, optimizedDraft);
                    log.info("【多Agent】OPTIMIZER 第{}轮完成, userId={}", round, userId);
                }

                // ========== 5. 完成事件 ==========
                Map<String, Object> doneEvent = new HashMap<>();
                doneEvent.put("stage", "DONE");
                doneEvent.put("message", "生成完成");
                doneEvent.put("content", ctx.getDraft());
                sink.next(JsonUtil.toJson(doneEvent));

                log.info("【多Agent】全部阶段完成, userId={}, topic={}", userId, ctx.getTopic());
                sink.complete();

            } catch (Exception e) {
                log.error("【多Agent】执行异常, userId={}", userId, e);
                // 推送错误事件
                Map<String, Object> errorEvent = new HashMap<>();
                errorEvent.put("stage", "ERROR");
                errorEvent.put("message", "生成失败：" + e.getMessage());
                sink.next(JsonUtil.toJson(errorEvent));
                sink.error(e);
            }
        });
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建 LLM 调用配置。
     * 优先使用 DTO 中指定的模型，否则使用系统默认配置。
     */
    private LlmConfig buildLlmConfig(Long userId, MultiAgentGenerateDTO dto) {
        return LlmConfig.builder()
                .model(dto.getModel() != null ? dto.getModel() : null)
                .temperature(dto.getTemperature() != null ? dto.getTemperature() : 0.7)
                .maxTokens(2048)
                .apiKey(systemApiKey)
                .userId(userId)
                .build();
    }

    /**
     * 同步调用 LLM 获取完整响应文本。
     *
     * @param systemPrompt 系统提示词（定义 Agent 角色）
     * @param userMessage  用户消息（本阶段的输入内容）
     * @param config       LLM 调用配置
     * @return LLM 生成的完整文本
     */
    private String callLlmSync(String systemPrompt, String userMessage, LlmConfig config) {
        List<ChatMessage> messages = List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(userMessage)
        );
        LlmResponse response = llmRouter.get(LlmProvider.valueOf(defaultProvider.toUpperCase()))
                .chat(messages, config);
        return response.getContent();
    }

    /**
     * 构建 PLANNER 阶段的用户消息——包含主题、平台、关键词等上下文信息。
     */
    private String buildPlannerUserMessage(AgentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为以下内容制定写作计划：\n");
        sb.append("主题：").append(ctx.getTopic()).append("\n");
        sb.append("目标平台：").append(ctx.getPlatform()).append("\n");
        if (ctx.getKeywords() != null && !ctx.getKeywords().isEmpty()) {
            sb.append("关键词：").append(ctx.getKeywords()).append("\n");
        }
        if (ctx.getRagContext() != null && !ctx.getRagContext().isEmpty()) {
            sb.append("参考资料：").append(ctx.getRagContext()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建 WRITER 阶段的用户消息——将策划方案作为输入传给写作者。
     */
    private String buildWriterUserMessage(AgentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据策划方案撰写文案。\n");
        sb.append("目标平台：").append(ctx.getPlatform()).append("\n");
        if (ctx.getKeywords() != null && !ctx.getKeywords().isEmpty()) {
            sb.append("需要融入的关键词：").append(ctx.getKeywords()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 向 sink 推送阶段变更事件。
     * 格式：{"stage":"PLANNER","message":"策划中..."}
     */
    private void emitStageEvent(reactor.core.publisher.FluxSink<String> sink,
                                AgentRole role, String message) {
        Map<String, Object> event = new HashMap<>();
        event.put("stage", role.name());
        event.put("message", message);
        sink.next(JsonUtil.toJson(event));
    }

    /**
     * 向 sink 推送 token 文本事件。
     * 格式：{"token":"一段文字"}
     */
    private void emitTokenEvent(reactor.core.publisher.FluxSink<String> sink, String content) {
        Map<String, Object> event = new HashMap<>();
        event.put("token", content);
        sink.next(JsonUtil.toJson(event));
    }

    /**
     * 判断审核结果是否通过。
     * 尝试从审核结果 JSON 中解析 passed 字段，解析失败则视为未通过。
     */
    private boolean isReviewPassed(String reviewResult) {
        try {
            // 尝试解析 JSON 格式的审核结论
            Map<String, Object> result = JsonUtil.fromJson(reviewResult,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Object passed = result.get("passed");
            if (passed instanceof Boolean) {
                return (Boolean) passed;
            }
            // 兼容字符串形式的 "true"/"false"
            return "true".equalsIgnoreCase(String.valueOf(passed));
        } catch (Exception e) {
            // JSON 解析失败，尝试简单文本匹配
            log.warn("【多Agent】审核结果 JSON 解析失败，使用文本匹配判断", e);
            return reviewResult.contains("\"passed\":true")
                    || reviewResult.contains("\"passed\": true");
        }
    }
}
