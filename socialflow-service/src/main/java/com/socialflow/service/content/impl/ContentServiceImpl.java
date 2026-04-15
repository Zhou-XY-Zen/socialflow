package com.socialflow.service.content.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.socialflow.common.exception.NotFoundException;
import com.socialflow.common.result.PageResult;
import com.socialflow.dao.mapper.ContentMapper;
import com.socialflow.model.dto.ContentBatchGenerateDTO;
import com.socialflow.model.dto.ContentGenerateDTO;
import com.socialflow.model.dto.ContentRewriteDTO;
import com.socialflow.model.dto.MultiAgentGenerateDTO;
import com.socialflow.model.entity.Content;
import com.socialflow.model.vo.ContentVO;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.service.ai.agent.MultiAgentService;
import com.socialflow.service.ai.embedding.EmbeddingService;
import com.socialflow.service.ai.embedding.VectorStoreService;
import com.socialflow.service.ai.guardrails.GuardrailService;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmProviderService;
import com.socialflow.service.ai.llm.LlmResponse;
import com.socialflow.service.ai.llm.LlmRouter;
import com.socialflow.service.ai.prompt.PromptService;
import com.socialflow.service.ai.rag.RagPipelineService;
import com.socialflow.service.content.ContentService;
import com.socialflow.service.content.support.ContentPersister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内容服务的完整实现类 —— 整个系统最核心的业务实现。
 *
 * 本类实现了 {@link ContentService} 接口，负责内容生成的完整业务流程，包括：
 *     - 单平台同步/流式生成（接入 LLM 调用）
 *     - 多平台批量生成（并行处理）
 *     - 多 Agent 协作生成
 *     - 内容改写、标题生成、标签推荐
 *     - 内容的增删改查（CRUD）
 *     - 内容版本管理
 *
 * 核心生成流程（5 步流水线）：
 *     安全检查 -> RAG 检索 -> 渲染提示词 -> 调用 LLM -> 输出检查 + 持久化
 *
 * @see ContentService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentServiceImpl implements ContentService {

    /** 内容表数据库映射器 */
    private final ContentMapper contentMapper;

    /** 内容持久化 Helper —— 将 Content + ContentVersion 多表写操作收敛到独立事务，避免 LLM 长调用占用 DB 连接 */
    private final ContentPersister contentPersister;

    /** 提示词模板服务，将用户输入 + 模板 + RAG 上下文组装成最终的 AI 提示词 */
    private final PromptService promptService;

    /** RAG 检索增强生成管道，从知识库检索相关文档片段作为上下文 */
    private final RagPipelineService ragPipelineService;

    /** 安全护栏服务，在生成前后检查内容合规性 */
    private final GuardrailService guardrailService;

    /** 多 Agent 协作服务，协调多个 AI Agent 分工协作生成 */
    private final MultiAgentService multiAgentService;

    /** LLM 请求路由器，根据提供者名称分发到对应的 LLM 实现 */
    private final LlmRouter llmRouter;

    /** 文本向量嵌入服务，用于生成内容向量 */
    private final EmbeddingService embeddingService;

    /** 向量数据库操作服务，用于存储和检索内容向量 */
    private final VectorStoreService vectorStoreService;

    /** 默认 LLM 提供者名称，从配置文件读取（如 "glm"、"deepseek"） */
    @Value("${socialflow.ai.default-provider:glm}")
    private String defaultProvider;

    /** 默认模型名称，从对应 provider 配置读取 */
    @Value("${socialflow.ai.providers.deepseek.default-model:deepseek-reasoner}")
    private String defaultModel;

    /** 系统级 API 密钥，当用户未配置自有密钥时使用 */
    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    // ==================== 文案生成相关方法 ====================

    /**
     * 单平台同步生成文案 —— 核心生成流程（5 步流水线）。
     *
     * 完整流程：
     *   1. 安全护栏：检查用户输入是否合规
     *   2. RAG 检索：如果指定了知识库，检索相关上下文
     *   3. 渲染提示词：将参数变量填入 Prompt 模板
     *   4. 调用 LLM：通过 LlmRouter 选择 Provider 并发送请求
     *   5. 输出检查 + 持久化：检查 AI 输出安全性，保存到数据库
     *
     * @param userId 当前登录用户 ID
     * @param dto    生成请求参数
     * @return 生成的内容视图对象
     */
    @Override
    public ContentVO generate(Long userId, ContentGenerateDTO dto) {
        // 第一步：安全护栏 —— 检查用户输入的主题是否包含敏感或违规内容
        guardrailService.checkInput(userId, dto.getTopic());

        // 第二步：RAG 上下文检索 —— 如果指定了知识库，检索相关文档片段
        String ragContext = null;
        if (dto.getKbId() != null) {
            ragContext = ragPipelineService.retrieveAsContext(userId, dto.getKbId(), dto.getTopic(), 5);
        }

        // 第三步：渲染提示词模板 —— 将用户参数填入模板
        Map<String, Object> vars = buildVariablesMap(dto);
        List<ChatMessage> messages = promptService.render(dto.getTemplateId(), dto.getPlatform(), vars, ragContext);

        // 第四步：构建 LLM 调用配置并发起请求
        LlmConfig config = buildLlmConfig(userId, dto);
        LlmProviderService provider = llmRouter.get(defaultProvider);
        LlmResponse response = provider.chat(messages, config);
        log.info("LLM 同步生成完成, userId={}, model={}, totalTokens={}",
                userId, response.getModel(), response.getTotalTokens());

        // 第五步：输出安全检查 —— 不阻塞主流程，仅收集警告信息
        List<String> guardrailWarnings = checkOutputSafely(userId, response.getContent(),
                dto.getPlatform(), ragContext);

        // 第六步：持久化到数据库（保存内容记录和初始版本）
        Content entity = saveContent(userId, dto, response.getContent(),
                response.getModel(), response.getTotalTokens());

        // 第七步：转换为视图对象并附加警告信息 + Wave 3.4 fallback 透传
        ContentVO vo = toVo(entity);
        vo.setGuardrailWarnings(guardrailWarnings);
        vo.setProviderUsed(response.getProviderUsed() != null
                ? response.getProviderUsed() : defaultProvider);
        vo.setFallback(response.isFallback());
        return vo;
    }

    /**
     * 单平台流式生成文案（SSE 推送）。
     *
     * 与同步生成相同的准备流程（安全检查、RAG、Prompt 渲染），
     * 但调用 LLM 的 chatStream 方法，逐 token 返回 JSON SSE 事件。
     * 流结束后将累积的完整内容保存到数据库。
     *
     * @param userId 当前登录用户 ID
     * @param dto    生成请求参数
     * @return Flux 字符串流，每个元素是一个 JSON SSE 事件
     */
    @Override
    public Flux<String> generateStream(Long userId, ContentGenerateDTO dto) {
        // 准备阶段：安全检查 + RAG + 渲染提示词
        guardrailService.checkInput(userId, dto.getTopic());

        String ragContext = null;
        if (dto.getKbId() != null) {
            ragContext = ragPipelineService.retrieveAsContext(userId, dto.getKbId(), dto.getTopic(), 5);
        }

        Map<String, Object> vars = buildVariablesMap(dto);
        List<ChatMessage> messages = promptService.render(dto.getTemplateId(), dto.getPlatform(), vars, ragContext);

        // 构建 LLM 配置并发起流式调用
        LlmConfig config = buildLlmConfig(userId, dto);
        LlmProviderService provider = llmRouter.get(defaultProvider);
        Flux<String> tokenStream = provider.chatStream(messages, config);

        // 使用 StringBuilder 累积所有 token，在流结束时保存完整内容
        StringBuilder accumulated = new StringBuilder();
        final String finalRagContext = ragContext;

        return tokenStream
                // 累积每个 token 并转换为 JSON SSE 事件格式
                .map(token -> {
                    accumulated.append(token);
                    // 转义 JSON 特殊字符，封装为 SSE 事件
                    String escapedToken = token.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                            .replace("\t", "\\t");
                    return "{\"token\": \"" + escapedToken + "\"}";
                })
                // 流完成后执行：保存内容到数据库
                .doOnComplete(() -> {
                    try {
                        String fullContent = accumulated.toString();
                        if (fullContent.isBlank()) {
                            log.warn("流式生成完成但内容为空, userId={}, model={}", userId, config.getModel());
                            return;
                        }
                        // 异步执行输出安全检查（不阻塞流）
                        checkOutputSafely(userId, fullContent, dto.getPlatform(), finalRagContext);
                        // 用 TokenCountUtil 估算生成内容的 token 数（流式接口无法获取精确值）
                        int estimatedTokens = com.socialflow.common.util.TokenCountUtil.estimate(fullContent);
                        // 持久化
                        saveContent(userId, dto, fullContent, config.getModel(), estimatedTokens);
                        log.info("流式生成完成并已保存, userId={}, contentLength={}", userId, fullContent.length());
                    } catch (Exception e) {
                        log.error("流式生成保存失败, userId={}", userId, e);
                    }
                })
                // 流出错时：发送错误事件给前端，而不是静默关闭
                .onErrorResume(e -> {
                    log.error("流式生成出错, userId={}, error={}", userId, e.getMessage(), e);
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "生成失败";
                    String escapedMsg = errorMsg.replace("\\", "\\\\").replace("\"", "\\\"")
                            .replace("\n", " ").replace("\r", "");
                    // 发送一个错误事件给前端
                    return Flux.just("event:error\ndata:{\"code\":500,\"message\":\"" + escapedMsg + "\"}\n\n");
                });
    }

    /**
     * 多平台批量生成文案（并行处理）。
     *
     * 对 dto.getPlatforms() 中的每个平台，创建独立的生成请求并行调用 generate 方法。
     * 使用 CompletableFuture 实现并行化，显著提升多平台生成效率。
     *
     * @param userId 当前登录用户 ID
     * @param dto    批量生成参数，包含多个目标平台
     * @return Map，key 为平台名称，value 为该平台的生成结果
     */
    @Override
    public Map<String, ContentVO> generateBatch(Long userId, ContentBatchGenerateDTO dto) {
        List<String> platforms = dto.getPlatforms();
        if (platforms == null || platforms.isEmpty()) {
            return Collections.emptyMap();
        }

        // 为每个平台创建独立的 CompletableFuture 任务并行执行
        Map<String, ContentVO> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = platforms.stream()
                .map(platform -> CompletableFuture.runAsync(() -> {
                    try {
                        // 为每个平台构建独立的 ContentGenerateDTO
                        ContentGenerateDTO singleDto = new ContentGenerateDTO();
                        BeanUtils.copyProperties(dto, singleDto);
                        singleDto.setPlatform(platform);

                        // 调用单平台生成方法
                        ContentVO vo = generate(userId, singleDto);
                        results.put(platform, vo);
                        log.info("批量生成 - 平台 {} 完成, userId={}", platform, userId);
                    } catch (Exception e) {
                        log.error("批量生成 - 平台 {} 失败, userId={}", platform, userId, e);
                    }
                }))
                .collect(Collectors.toList());

        // 等待所有平台生成完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("批量生成全部完成, userId={}, 平台数={}, 成功数={}",
                userId, platforms.size(), results.size());
        return results;
    }

    /**
     * 多 Agent 协作生成文案（最高级的生成模式）。
     *
     * 委托给 MultiAgentService 的流式执行方法，由多个 AI Agent 分阶段协作完成。
     *
     * @param userId 当前登录用户 ID
     * @param dto    多 Agent 生成参数
     * @return Flux 字符串流，包含各阶段进度更新和最终结果
     */
    @Override
    public Flux<String> generateMultiAgent(Long userId, MultiAgentGenerateDTO dto) {
        return multiAgentService.runStream(userId, dto);
    }

    /**
     * 改写已有内容 —— 基于原文案生成新风格的版本。
     *
     * 处理流程：
     *   1. 从数据库加载原内容
     *   2. 构建改写提示词（系统角色=文案改写专家，用户角色=改写指令+原文）
     *   3. 调用 LLM 生成改写结果
     *   4. 保存为新的内容记录
     *
     * @param userId 当前登录用户 ID
     * @param dto    改写参数，包含原内容 ID、目标语气、目标平台等
     * @return 改写后的新内容视图对象
     */
    @Override
    public ContentVO rewrite(Long userId, ContentRewriteDTO dto) {
        // 第一步：加载原内容并校验权限
        Content original = contentMapper.selectById(dto.getContentId());
        if (original == null || !userId.equals(original.getUserId())) {
            throw new NotFoundException("原文案不存在: " + dto.getContentId());
        }

        // 第二步：确定目标语气和目标平台
        String targetTone = StringUtils.hasText(dto.getTargetTone()) ? dto.getTargetTone() : "professional";
        String targetPlatform = StringUtils.hasText(dto.getTargetPlatform()) ? dto.getTargetPlatform() : original.getPlatform();

        // 第三步：构建改写提示词
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("你是文案改写专家，擅长将文案改写为不同风格和语气，同时保持核心信息不变。"));
        messages.add(ChatMessage.user(
                "请将以下文案改写为" + targetTone + "风格，适配" + targetPlatform + "平台：\n\n" + original.getBody()
        ));

        // 第四步：构建 LLM 配置并发起调用
        String modelName = StringUtils.hasText(dto.getModel()) ? dto.getModel() : null;
        LlmConfig config = LlmConfig.builder()
                .model(modelName)
                .temperature(0.8)
                .apiKey(systemApiKey)
                .userId(userId)
                .build();
        LlmProviderService provider = llmRouter.get(defaultProvider);
        LlmResponse response = provider.chat(messages, config);
        log.info("文案改写完成, userId={}, originalId={}, model={}", userId, dto.getContentId(), response.getModel());

        // 第五步：组装改写后的新内容实体
        Content newContent = new Content();
        newContent.setUserId(userId);
        newContent.setTitle(original.getTitle());
        newContent.setBody(response.getContent());
        newContent.setPlatform(targetPlatform);
        newContent.setStatus("DRAFT");
        newContent.setAiModel(response.getModel());
        newContent.setTokenUsage(response.getTotalTokens());
        newContent.setTemplateId(original.getTemplateId());
        newContent.setKbId(original.getKbId());
        newContent.setTags(original.getTags());

        // 第六步：走独立事务持久化（Content + 版本快照原子提交）
        contentPersister.insertWithVersion(newContent, "AI_REWRITE - 改写为" + targetTone + "风格");

        return toVo(newContent);
    }

    /**
     * 生成多个候选标题。
     *
     * 根据文案正文内容，调用 LLM 生成指定数量的标题候选供用户选择。
     *
     * @param userId   当前登录用户 ID
     * @param body     文案正文
     * @param platform 目标平台
     * @param count    需要生成的标题数量
     * @return 候选标题列表
     */
    @Override
    public List<String> generateTitles(Long userId, String body, String platform, int count) {
        // 构建标题生成提示词
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("你是社交媒体标题撰写专家，擅长为不同平台创作吸引眼球的标题。"));
        messages.add(ChatMessage.user(
                "根据以下文案内容，生成" + count + "个适合" + platform + "平台的标题候选。\n"
                        + "要求：每个标题独占一行，直接输出标题文本，不要加序号和其他标点符号。\n\n"
                        + body
        ));

        // 调用 LLM 生成
        LlmConfig config = LlmConfig.builder()
                .temperature(0.9)  // 较高温度以获得更多样的标题
                .apiKey(systemApiKey)
                .userId(userId)
                .build();
        LlmProviderService provider = llmRouter.get(defaultProvider);
        LlmResponse response = provider.chat(messages, config);

        // 解析响应 —— 按换行符分割，过滤空行和序号前缀
        return parseListResponse(response.getContent(), count);
    }

    /**
     * 智能推荐话题标签（Hashtag）。
     *
     * 根据文案内容和目标平台，调用 LLM 推荐合适的话题标签。
     *
     * @param userId   当前登录用户 ID
     * @param body     文案正文
     * @param platform 目标平台
     * @param count    推荐标签数量
     * @return 推荐的标签列表
     */
    @Override
    public List<String> suggestHashtags(Long userId, String body, String platform, int count) {
        // 构建标签推荐提示词
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("你是社交媒体运营专家，擅长为文案推荐高流量的话题标签。"));
        messages.add(ChatMessage.user(
                "根据以下文案内容，推荐" + count + "个适合" + platform + "平台的话题标签（Hashtag）。\n"
                        + "要求：每个标签独占一行，以 # 开头，不要加序号和其他内容。\n\n"
                        + body
        ));

        // 调用 LLM 生成
        LlmConfig config = LlmConfig.builder()
                .temperature(0.7)
                .apiKey(systemApiKey)
                .userId(userId)
                .build();
        LlmProviderService provider = llmRouter.get(defaultProvider);
        LlmResponse response = provider.chat(messages, config);

        // 解析响应 —— 按换行符分割，提取标签
        return parseListResponse(response.getContent(), count);
    }

    /**
     * 相似内容检索（基于向量搜索）。
     *
     * 将查询文本转换为向量，在 content_vectors 集合中搜索语义相似的历史内容，
     * 然后从 MySQL 加载完整内容并转换为 VO 返回。
     *
     * @param userId 当前登录用户 ID
     * @param text   检索文本
     * @param topK   返回条数
     * @return 按相似度排序的内容列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<ContentVO> similar(Long userId, String text, int topK) {
        try {
            // 第一步：将查询文本转换为向量
            float[] vector = embeddingService.embed(text);

            // 第二步：在 content_vectors 集合中搜索相似向量（按 user_id 隔离数据）
            Map<String, Object> filter = new HashMap<>();
            filter.put("user_id", userId);
            List<VectorStoreService.SearchHit> hits = vectorStoreService.search(
                    CommonConstants.VC_CONTENT_VECTORS, vector, filter, topK);

            if (hits.isEmpty()) {
                return Collections.emptyList();
            }

            // 第三步：从搜索结果的元数据中提取 content_id，加载完整内容
            List<ContentVO> results = new ArrayList<>();
            for (VectorStoreService.SearchHit hit : hits) {
                Object contentIdObj = hit.metadata() == null ? null : hit.metadata().get("content_id");
                if (contentIdObj == null) continue;
                long contentId = ((Number) contentIdObj).longValue();

                Content content = contentMapper.selectById(contentId);
                if (content != null && userId.equals(content.getUserId())) {
                    results.add(toVo(content));
                }
            }

            log.info("相似内容检索完成, userId={}, topK={}, 返回数={}", userId, topK, results.size());
            return results;
        } catch (Exception e) {
            log.error("相似内容检索失败, userId={}, topK={}", userId, topK, e);
            return Collections.emptyList();
        }
    }

    // ==================== 基础 CRUD 操作 ====================

    /**
     * 根据 ID 获取单条内容详情。
     *
     * 查询数据库并校验内容是否属于当前用户（数据隔离）。
     *
     * @param userId 当前登录用户 ID
     * @param id     内容记录 ID
     * @return 内容视图对象
     * @throws NotFoundException 内容不存在或不属于当前用户
     */
    @Override
    @Transactional(readOnly = true)
    public ContentVO get(Long userId, Long id) {
        Content entity = contentMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new NotFoundException("content not found: " + id);
        }
        return toVo(entity);
    }

    /**
     * 更新内容的标题、正文和标签。
     *
     * 更新前校验权限，更新后自动保存版本快照。
     *
     * @param userId 当前登录用户 ID
     * @param id     要更新的内容 ID
     * @param title  新标题
     * @param body   新正文
     * @param tags   新标签
     * @return 更新后的内容视图对象
     * @throws NotFoundException 内容不存在或不属于当前用户
     */
    @Override
    public ContentVO update(Long userId, Long id, String title, String body, String tags) {
        // 查询并校验权限
        Content entity = contentMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new NotFoundException("content not found: " + id);
        }

        // 更新字段
        entity.setTitle(title);
        entity.setBody(body);
        entity.setTags(tags);

        // 走独立事务：Content 更新 + 版本快照原子提交
        contentPersister.updateWithVersion(entity, "MANUAL_EDIT - 手动编辑");

        return get(userId, id);
    }

    /**
     * 删除一条内容记录。
     *
     * 删除前校验权限，防止用户删除他人的内容。
     * 注意：如果配置了逻辑删除，实际执行的是 UPDATE is_deleted = 1。
     *
     * @param userId 当前登录用户 ID
     * @param id     要删除的内容 ID
     * @throws NotFoundException 内容不存在或不属于当前用户
     */
    @Override
    public void delete(Long userId, Long id) {
        Content entity = contentMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new NotFoundException("content not found: " + id);
        }
        contentPersister.softDelete(id);
        log.info("内容已删除, userId={}, contentId={}", userId, id);
    }

    /**
     * 分页查询内容列表（支持多条件筛选）。
     *
     * 使用 MyBatis-Plus 的 Page + LambdaQueryWrapper 实现动态条件分页查询。
     * 支持按平台、状态、关键词（模糊匹配标题和正文）、标签筛选。
     *
     * @param userId   当前登录用户 ID（数据隔离，只能查自己的内容）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @param platform 平台筛选（可选）
     * @param status   状态筛选（可选）
     * @param keyword  关键词搜索（可选，模糊匹配标题和正文）
     * @param tags     标签筛选（可选）
     * @return 分页结果
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ContentVO> list(Long userId, Integer pageNum, Integer pageSize,
                                      String platform, String status, String keyword, String tags) {
        // 设置默认分页参数
        int currentPage = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 20 : pageSize;

        // 构建动态查询条件
        LambdaQueryWrapper<Content> wrapper = new LambdaQueryWrapper<>();
        // 必须条件：只查当前用户的内容（数据隔离）
        wrapper.eq(Content::getUserId, userId);
        // 可选条件：按平台筛选
        wrapper.eq(StringUtils.hasText(platform), Content::getPlatform, platform);
        // 可选条件：按状态筛选
        wrapper.eq(StringUtils.hasText(status), Content::getStatus, status);
        // 可选条件：关键词模糊搜索（匹配标题或正文）
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Content::getTitle, keyword)
                    .or()
                    .like(Content::getBody, keyword)
            );
        }
        // 可选条件：标签模糊匹配
        wrapper.like(StringUtils.hasText(tags), Content::getTags, tags);
        // 按创建时间倒序排列（最新的排在前面）
        wrapper.orderByDesc(Content::getCreateTime);

        // 执行分页查询
        Page<Content> page = new Page<>(currentPage, size);
        contentMapper.selectPage(page, wrapper);

        // 将实体列表转换为视图对象列表
        List<ContentVO> voList = page.getRecords().stream()
                .map(this::toVo)
                .collect(Collectors.toList());

        return PageResult.of(voList, page.getTotal(), currentPage, size);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 构建 LLM 调用配置。
     *
     * 根据用户的 DTO 参数和系统默认配置，构建完整的 LlmConfig 对象。
     * 优先使用 DTO 中指定的参数，未指定则使用系统默认值。
     *
     * @param userId 当前用户 ID
     * @param dto    生成请求 DTO
     * @return LLM 调用配置
     */
    private LlmConfig buildLlmConfig(Long userId, ContentGenerateDTO dto) {
        return LlmConfig.builder()
                .model(StringUtils.hasText(dto.getModel()) ? dto.getModel() : defaultModel)
                .temperature(dto.getTemperature() != null ? dto.getTemperature() : 0.7)
                .apiKey(systemApiKey)
                .userId(userId)
                .build();
    }

    /**
     * 从 DTO 构建 Prompt 模板变量 Map。
     *
     * 将 DTO 中的各项参数提取到一个 Map 中，供 PromptService 模板渲染使用。
     *
     * @param dto 生成请求 DTO
     * @return 变量映射表
     */
    private Map<String, Object> buildVariablesMap(ContentGenerateDTO dto) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("topic", dto.getTopic());
        vars.put("keywords", dto.getKeywords());
        vars.put("productInfo", dto.getProductInfo());
        vars.put("wordCount", dto.getWordCount());
        vars.put("tone", dto.getTone());
        vars.put("platform", dto.getPlatform());
        return vars;
    }

    /**
     * 安全地执行输出端护栏检查（不阻塞主流程）。
     *
     * 对 LLM 生成的输出执行安全检查。如果检查抛出异常，
     * 仅记录警告日志和收集警告信息，不中断生成流程。
     *
     * @param userId        用户 ID
     * @param generatedText LLM 生成的文本
     * @param platform      目标平台
     * @param ragContext    RAG 上下文
     * @return 警告信息列表（无警告时返回空列表）
     */
    private List<String> checkOutputSafely(Long userId, String generatedText,
                                           String platform, String ragContext) {
        List<String> warnings = new ArrayList<>();
        try {
            guardrailService.checkOutput(userId, generatedText, platform, ragContext);
        } catch (Exception e) {
            // 输出护栏检查失败时不阻断流程，仅收集警告信息
            log.warn("输出安全检查触发警告, userId={}, message={}", userId, e.getMessage());
            warnings.add(e.getMessage());
        }
        return warnings;
    }

    /**
     * 保存生成的内容到数据库，并创建初始版本记录。
     *
     * @param userId        用户 ID
     * @param dto           生成请求 DTO（用于提取平台、模板、知识库等元数据）
     * @param generatedText LLM 生成的文案正文
     * @param model         实际使用的模型名称
     * @param tokenUsage    Token 消耗量（流式生成时可能为 null）
     * @return 保存后的 Content 实体（包含数据库生成的 ID）
     */
    private Content saveContent(Long userId, ContentGenerateDTO dto,
                                String generatedText, String model, Integer tokenUsage) {
        // 清理 AI 常附加的字数统计（如"（全文约860字）""字数：500"等）
        String cleanText = generatedText
                .replaceAll("[（(]\\s*全文[约共]?\\d+[字词][）)]\\s*$", "")
                .replaceAll("[（(]\\s*[字词数][数量]?[：:]?\\s*\\d+\\s*[）)]\\s*$", "")
                .replaceAll("\\n*\\s*[字词][数量]?[：:]\\s*\\d+\\s*[字词]?\\s*$", "")
                .trim();

        Content entity = new Content();
        entity.setUserId(userId);
        entity.setBody(cleanText);
        entity.setPlatform(dto.getPlatform());
        entity.setStatus("DRAFT");  // 新生成的内容默认为草稿状态
        entity.setAiModel(model);
        entity.setTokenUsage(tokenUsage);
        entity.setTemplateId(dto.getTemplateId());
        entity.setKbId(dto.getKbId());

        // 如果有关键词，将其作为初始标签（逗号分隔）
        if (dto.getKeywords() != null && !dto.getKeywords().isEmpty()) {
            entity.setTags(String.join(",", dto.getKeywords()));
        }

        // 保存生成参数快照（JSON 格式，便于复现和追溯）
        entity.setGenerationParams(buildParamsSnapshot(dto));

        // 走独立事务持久化 Content + 初始版本（事务边界仅覆盖 DB 操作，不占用 LLM 调用期间的连接）
        contentPersister.insertWithVersion(entity, "AI_GENERATE - AI 首次生成");
        log.info("内容已保存, userId={}, contentId={}", userId, entity.getId());

        // 异步嵌入并存储内容向量到 content_vectors（事务外执行，失败不影响主流程）
        try {
            float[] contentVector = embeddingService.embed(cleanText);
            Map<String, Object> vectorMeta = new HashMap<>();
            vectorMeta.put("user_id", userId);
            vectorMeta.put("content_id", entity.getId());
            vectorMeta.put("platform", entity.getPlatform());
            vectorMeta.put("tags", entity.getTags());
            vectorStoreService.upsert(CommonConstants.VC_CONTENT_VECTORS, contentVector, vectorMeta);
            log.debug("内容向量已存储, contentId={}", entity.getId());
        } catch (Exception e) {
            log.warn("内容向量存储失败（不影响主流程）, contentId={}", entity.getId(), e);
        }

        return entity;
    }

    /**
     * 将 Content 实体转换为 ContentVO 视图对象。
     *
     * 使用 BeanUtils 复制同名属性，并处理字段名差异（aiModel -> model）。
     *
     * @param entity 数据库内容实体
     * @return 内容视图对象
     */
    private ContentVO toVo(Content entity) {
        ContentVO vo = new ContentVO();
        BeanUtils.copyProperties(entity, vo);
        // 特殊映射：数据库字段 aiModel -> VO 字段 model
        vo.setModel(entity.getAiModel());
        return vo;
    }

    /**
     * 解析 LLM 返回的列表格式文本。
     *
     * LLM 可能返回带序号、带前缀或纯文本的列表，本方法统一解析：
     * 按换行分割 -> 去除序号前缀（如 "1."、"1)"） -> 去除空行 -> 截取指定数量。
     *
     * @param responseText LLM 返回的文本
     * @param maxCount     最多返回的条目数
     * @return 解析后的字符串列表
     */
    private List<String> parseListResponse(String responseText, int maxCount) {
        if (!StringUtils.hasText(responseText)) {
            return Collections.emptyList();
        }
        return Arrays.stream(responseText.split("\n"))
                .map(String::trim)
                // 去除常见的序号前缀：1. 2. 3. 或 1) 2) 3) 或 1、2、3、
                .map(line -> line.replaceAll("^\\d+[.、)\\]\\s]+", ""))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(maxCount)
                .collect(Collectors.toList());
    }

    /**
     * 构建生成参数的 JSON 快照字符串。
     *
     * 将 DTO 中的关键参数序列化为简单 JSON 格式，保存到数据库中，
     * 便于后续复现和追溯生成过程。
     *
     * @param dto 生成请求 DTO
     * @return JSON 格式的参数快照
     */
    private String buildParamsSnapshot(ContentGenerateDTO dto) {
        // 手动构建简单 JSON，避免引入额外 JSON 库依赖
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"topic\":\"").append(escapeJson(dto.getTopic())).append("\"");
        if (dto.getTone() != null) {
            sb.append(",\"tone\":\"").append(escapeJson(dto.getTone())).append("\"");
        }
        if (dto.getWordCount() != null) {
            sb.append(",\"wordCount\":").append(dto.getWordCount());
        }
        if (dto.getTemperature() != null) {
            sb.append(",\"temperature\":").append(dto.getTemperature());
        }
        if (dto.getModel() != null) {
            sb.append(",\"model\":\"").append(escapeJson(dto.getModel())).append("\"");
        }
        if (dto.getKeywords() != null) {
            sb.append(",\"keywords\":[");
            sb.append(dto.getKeywords().stream()
                    .map(k -> "\"" + escapeJson(k) + "\"")
                    .collect(Collectors.joining(",")));
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 转义 JSON 字符串中的特殊字符。
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
