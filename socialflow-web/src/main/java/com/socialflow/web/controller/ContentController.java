package com.socialflow.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.PageResult;
import com.socialflow.common.result.R;
import com.socialflow.model.dto.ContentBatchGenerateDTO;
import com.socialflow.model.dto.ContentGenerateDTO;
import com.socialflow.model.dto.ContentRewriteDTO;
import com.socialflow.model.dto.MultiAgentGenerateDTO;
import com.socialflow.model.entity.ContentVersion;
import com.socialflow.model.vo.ContentVO;
import com.socialflow.service.content.ContentService;
import com.socialflow.web.aspect.ApiLog;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 内容控制器 —— 本系统最核心的控制器，负责 AI 内容的生成与管理。
 *
 * 本控制器处理的基础 URL 路径为 {@code /api/v1/content}，包含以下功能：
 *     - 单平台内容生成（同步 / SSE 流式）
 *     - 多平台批量生成
 *     - 基于 RAG（检索增强生成）的内容生成
 *     - Multi-Agent（多智能体协作）生成
 *     - 内容改写、标题生成、话题标签推荐、语义相似搜索
 *     - 内容的增删改查（CRUD）操作
 *
 * 使用的 HTTP 方法：
 *     - POST —— 生成、改写、搜索等写操作
 *     - GET  —— 查询列表、查看详情
 *     - PUT  —— 更新已有内容
 *     - DELETE —— 删除内容
 *
 * 所有接口都需要用户登录后才能访问，通过 Sa-Token 的 {@code StpUtil.getLoginIdAsLong()}
 * 获取当前登录用户的 ID，确保用户只能操作自己的数据。
 *
 * @see ContentService 内容业务逻辑的具体实现
 */
/*
 * @Tag           —— Swagger/Knife4j 文档分组标签，在 API 文档中显示为 "content" 分组
 * @RestController —— 标记这是一个 REST 控制器，方法返回值会自动序列化为 JSON
 * @RequestMapping —— 设置本控制器所有接口的公共前缀路径：/api/v1/content
 * @RequiredArgsConstructor —— Lombok 注解，自动为所有 final 字段生成构造函数（实现依赖注入）
 */
@Tag(name = "content", description = "content generation and management")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/content")
@RequiredArgsConstructor
public class ContentController {

    /** 内容服务（Controller 不直接依赖 DAO，媒体绑定/版本历史等通过 Service 暴露） */
    private final ContentService contentService;

    /**
     * 单平台内容生成（同步方式）。
     *
     * 接口路径：POST /api/v1/content/generate
     *
     * 功能：根据用户提供的提示词、平台类型等参数，调用 AI 生成一篇适合指定平台的内容。
     * 该接口是同步的，会等待 AI 生成完毕后一次性返回完整结果。
     *
     * @param dto 内容生成请求参数（包含提示词、目标平台、语气风格等），使用 @Valid 进行参数校验
     * @return 统一响应体 R，包含生成的内容详情 ContentVO（标题、正文、标签等）
     */
    @Operation(summary = "single platform generation")
    @PostMapping("/generate")
    @RateLimiter(name = "ai-generate")
    @ApiLog("[内容生成-单平台]")
    public R<ContentVO> generate(@Valid @RequestBody ContentGenerateDTO dto) {
        return R.ok(contentService.generate(StpUtil.getLoginIdAsLong(), dto));
    }

    // ========== Wave 4.3: 内容库 UX 端点 ==========

    /**
     * Autosave 草稿（Wave 4.3）—— 前端定时调用，不写版本快照。
     * 请求体：{title, body, tags}，三者均可选。
     */
    @Operation(summary = "autosave draft (no version snapshot)")
    @PutMapping("/{id}/draft")
    public R<ContentVO> saveDraft(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return R.ok(contentService.saveDraft(StpUtil.getLoginIdAsLong(), id,
                body.get("title"), body.get("body"), body.get("tags")));
    }

    /** 批量软删（Wave 4.3）。请求体：{ids: [1,2,3]}，返回实际删除条数。 */
    @Operation(summary = "bulk soft-delete contents")
    @PostMapping("/bulk/delete")
    @ApiLog("[批量删除]")
    public R<Map<String, Object>> bulkDelete(@RequestBody Map<String, List<Long>> body) {
        int affected = contentService.bulkDelete(StpUtil.getLoginIdAsLong(), body.get("ids"));
        return R.ok(Map.of("affected", affected));
    }

    /** 批量改状态（Wave 4.3）。请求体：{ids: [...], status: "DRAFT"} */
    @Operation(summary = "bulk update status")
    @PostMapping("/bulk/status")
    public R<Map<String, Object>> bulkStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<?>) body.get("ids")).stream()
                .map(o -> Long.valueOf(o.toString())).toList();
        String status = (String) body.get("status");
        int affected = contentService.bulkUpdateStatus(StpUtil.getLoginIdAsLong(), ids, status);
        return R.ok(Map.of("affected", affected));
    }

    /** 克隆（Wave 4.3）—— 复制头表数据为新草稿，不复制版本/发布任务/媒体关联。 */
    @Operation(summary = "clone content as a new draft")
    @PostMapping("/{id}/clone")
    public R<ContentVO> clone(@PathVariable Long id) {
        return R.ok(contentService.clone(StpUtil.getLoginIdAsLong(), id));
    }

    /**
     * 单平台内容生成（SSE 流式方式）。
     *
     * 接口路径：POST /api/v1/content/generate-stream
     *
     * 功能：与 /generate 相同，但使用 SSE（Server-Sent Events，服务器推送事件）流式返回。
     * AI 每生成一小段文字就立即推送给前端，用户可以看到内容"逐字出现"的效果，体验更好。
     *
     * 返回类型为 {@code Flux<String>}，这是 Spring WebFlux 的响应式类型，
     * 配合 {@code produces = TEXT_EVENT_STREAM_VALUE} 实现 SSE 流式输出。
     *
     * @param dto 内容生成请求参数（与同步接口相同）
     * @return SSE 事件流，前端通过 EventSource 或 fetch 逐步接收生成的文本片段
     */
    @Operation(summary = "single platform generation (SSE)")
    @PostMapping(value = "/generate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiLog("[内容生成-SSE]")
    public Flux<String> generateStream(@Valid @RequestBody ContentGenerateDTO dto) {
        return contentService.generateStream(StpUtil.getLoginIdAsLong(), dto);
    }

    /**
     * 多平台批量内容生成。
     *
     * 接口路径：POST /api/v1/content/generate-batch
     *
     * 功能：一次请求同时为多个社交媒体平台生成内容。例如同时为微博、小红书、抖音
     * 生成风格各异的内容。返回一个 Map，key 是平台名称，value 是对应的生成内容。
     *
     * @param dto 批量生成请求参数（包含多个目标平台及各自的配置）
     * @return 统一响应体 R，包含 Map<平台名称, 生成内容>
     */
    @Operation(summary = "multi-platform batch generation")
    @PostMapping("/generate-batch")
    @RateLimiter(name = "ai-generate")
    @ApiLog("[内容生成-批量]")
    public R<Map<String, ContentVO>> generateBatch(@Valid @RequestBody ContentBatchGenerateDTO dto) {
        return R.ok(contentService.generateBatch(StpUtil.getLoginIdAsLong(), dto));
    }

    /**
     * 基于 RAG（检索增强生成）的内容生成（SSE 流式方式）。
     *
     * 接口路径：POST /api/v1/content/generate-with-rag
     *
     * 功能：在生成内容时，先从用户的知识库中检索相关文档片段，
     * 然后将这些片段作为上下文提供给 AI，让生成的内容更加准确和专业。
     * 这就是 RAG（Retrieval-Augmented Generation，检索增强生成）技术。
     *
     * 同样使用 SSE 流式返回，让用户实时看到生成过程。
     *
     * @param dto 内容生成请求参数（需包含关联的知识库 ID）
     * @return SSE 事件流
     */
    @Operation(summary = "RAG-enabled single platform generation (SSE)")
    @PostMapping(value = "/generate-with-rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateWithRag(@Valid @RequestBody ContentGenerateDTO dto) {
        return contentService.generateStream(StpUtil.getLoginIdAsLong(), dto);
    }

    /**
     * Multi-Agent（多智能体协作）内容生成（SSE 流式方式）。
     *
     * 接口路径：POST /api/v1/content/generate-multi-agent
     *
     * 功能：使用多个 AI 智能体协作完成内容生成。例如一个智能体负责选题策划，
     *
     * 一个负责内容撰写，一个负责审核优化，模拟真实的内容创作团队协作流程。
     *
     * 同样使用 SSE 流式返回，前端可以展示各智能体的工作进度。
     *
     * @param dto 多智能体生成请求参数（包含各智能体的角色配置等）
     * @return SSE 事件流
     */
    @Operation(summary = "Multi-Agent generation (SSE)")
    @PostMapping(value = "/generate-multi-agent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiLog("[Multi-Agent 生成]")
    public Flux<String> generateMultiAgent(@Valid @RequestBody MultiAgentGenerateDTO dto) {
        return contentService.generateMultiAgent(StpUtil.getLoginIdAsLong(), dto);
    }

    /**
     * 改写已有内容。
     *
     * 接口路径：POST /api/v1/content/rewrite
     *
     * 功能：对一条已有的内容进行 AI 改写。可以调整语气风格、适配不同平台，
     * 或者简单地换一种表达方式。改写后生成一条新的内容记录。
     *
     * @param dto 改写请求参数（包含原内容 ID、改写指令等）
     * @return 统一响应体 R，包含改写后的新内容详情 ContentVO
     */
    @Operation(summary = "rewrite an existing content row")
    @PostMapping("/rewrite")
    @RateLimiter(name = "ai-generate")
    @ApiLog("[内容改写]")
    public R<ContentVO> rewrite(@Valid @RequestBody ContentRewriteDTO dto) {
        return R.ok(contentService.rewrite(StpUtil.getLoginIdAsLong(), dto));
    }

    /**
     * 生成标题候选列表。
     *
     * 接口路径：POST /api/v1/content/generate-title
     *
     * 功能：根据内容正文和目标平台，AI 自动生成多个备选标题供用户选择。
     * 例如输入一段产品介绍文案，AI 会生成多个吸引眼球的标题。
     *
     * @param body     内容正文文本（通过 URL 查询参数传入）
     * @param platform 目标平台名称（如 "weibo"、"xiaohongshu"）
     * @param count    需要生成的标题数量，默认为 3 个
     * @return 统一响应体 R，包含标题字符串列表
     */
    @Operation(summary = "generate title candidates")
    @PostMapping("/generate-title")
    public R<List<String>> generateTitle(@RequestParam String body,
                                          @RequestParam String platform,
                                          @RequestParam(defaultValue = "3") int count) {
        return R.ok(contentService.generateTitles(StpUtil.getLoginIdAsLong(), body, platform, count));
    }

    /**
     * 话题标签（Hashtag）推荐。
     *
     * 接口路径：POST /api/v1/content/suggest-hashtags
     *
     * 功能：根据内容正文和目标平台，AI 自动推荐合适的话题标签。
     * 好的话题标签可以增加内容的曝光量和发现率。
     *
     * @param body     内容正文文本
     * @param platform 目标平台名称
     * @param count    需要推荐的标签数量，默认为 10 个
     * @return 统一响应体 R，包含推荐的话题标签字符串列表
     */
    @Operation(summary = "hashtag suggestions")
    @PostMapping("/suggest-hashtags")
    public R<List<String>> suggestHashtags(@RequestParam String body,
                                            @RequestParam String platform,
                                            @RequestParam(defaultValue = "10") int count) {
        return R.ok(contentService.suggestHashtags(StpUtil.getLoginIdAsLong(), body, platform, count));
    }

    /**
     * 语义相似内容搜索。
     *
     * 接口路径：POST /api/v1/content/similar
     *
     * 功能：通过向量相似度搜索，找到与输入文本语义上最相似的历史内容。
     * 这有助于用户发现相关内容、避免重复创作，也可以作为灵感参考。
     *
     * 底层使用向量数据库进行语义检索，而不是简单的关键词匹配。
     *
     * @param text 搜索文本（用户输入的查询内容）
     * @param topK 返回最相似的前 K 条结果，默认为 5
     * @return 统一响应体 R，包含相似内容列表
     */
    @Operation(summary = "similar content semantic search")
    @PostMapping("/similar")
    public R<List<ContentVO>> similar(@RequestParam String text,
                                       @RequestParam(defaultValue = "5") int topK) {
        return R.ok(contentService.similar(StpUtil.getLoginIdAsLong(), text, topK));
    }

    // ------------- CRUD（增删改查）操作 -------------

    /**
     * 分页查询内容列表。
     *
     * 接口路径：GET /api/v1/content/list
     *
     * 功能：获取当前用户的内容列表，支持分页和多种筛选条件。
     * 前端的内容管理页面会调用此接口展示内容列表。
     *
     * @param pageNum  页码，从 1 开始，默认第 1 页
     * @param pageSize 每页显示的条数，默认 20 条
     * @param platform 可选筛选条件：按平台过滤（如 "weibo"）
     * @param status   可选筛选条件：按状态过滤（如 "draft" 草稿、"published" 已发布）
     * @param keyword  可选筛选条件：按关键词搜索标题或正文
     * @param tags     可选筛选条件：按标签过滤
     * @return 统一响应体 R，包含分页结果 PageResult（总条数、当前页数据列表等）
     */
    @Operation(summary = "paged content list")
    @GetMapping("/list")
    public R<PageResult<ContentVO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "20") Integer pageSize,
                                          @RequestParam(required = false) String platform,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String tags) {
        return R.ok(contentService.list(StpUtil.getLoginIdAsLong(),
                pageNum, pageSize, platform, status, keyword, tags));
    }

    /**
     * 获取单条内容详情。
     *
     * 接口路径：GET /api/v1/content/{id}
     *
     * 功能：根据内容 ID 获取完整的内容详情，包含标题、正文、标签、生成参数等。
     *
     * @param id 内容的唯一标识（从 URL 路径中获取）
     * @return 统一响应体 R，包含内容详情 ContentVO
     */
    @Operation(summary = "content detail")
    @GetMapping("/{id}")
    public R<ContentVO> get(@PathVariable Long id) {
        return R.ok(contentService.get(StpUtil.getLoginIdAsLong(), id));
    }

    /**
     * 更新已有内容。
     *
     * 接口路径：PUT /api/v1/content/{id}
     *
     * 功能：修改一条已有内容的标题、正文和标签。用户在前端编辑内容后保存时调用此接口。
     *
     * @param id    要更新的内容 ID（从 URL 路径中获取）
     * @param title 新的标题
     * @param body  新的正文内容
     * @param tags  新的标签（可选）
     * @return 统一响应体 R，包含更新后的内容详情 ContentVO
     */
    @Operation(summary = "update content")
    @PutMapping("/{id}")
    public R<ContentVO> update(@PathVariable Long id,
                                @RequestParam String title,
                                @RequestParam String body,
                                @RequestParam(required = false) String tags) {
        return R.ok(contentService.update(StpUtil.getLoginIdAsLong(), id, title, body, tags));
    }

    /**
     * 删除内容。
     *
     * 接口路径：DELETE /api/v1/content/{id}
     *
     * 功能：根据内容 ID 删除一条内容。只有内容的所有者才能删除。
     *
     * @param id 要删除的内容 ID（从 URL 路径中获取）
     * @return 统一响应体 R，无数据体（仅包含操作成功/失败状态）
     */
    @Operation(summary = "delete content")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        contentService.delete(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    /**
     * 绑定配图素材到文案。
     * 前端生成文案后，用户勾选推荐配图，点保存调用此接口。
     */
    @Operation(summary = "bind media to content")
    @PostMapping("/{id}/bindMedia")
    public R<Void> bindMedia(@PathVariable Long id, @RequestBody java.util.List<Long> mediaIds) {
        contentService.bindMedia(StpUtil.getLoginIdAsLong(), id, mediaIds);
        return R.ok();
    }

    /**
     * 获取文案关联的配图素材列表。
     */
    @Operation(summary = "get content bound media")
    @GetMapping("/{id}/media")
    public R<java.util.List<com.socialflow.model.entity.MediaAsset>> getMedia(@PathVariable Long id) {
        return R.ok(contentService.listBoundMedia(StpUtil.getLoginIdAsLong(), id));
    }

    /**
     * 获取内容版本历史列表。
     *
     * 接口路径：GET /api/v1/content/{id}/versions
     *
     * 功能：返回指定内容的所有历史版本，按版本号倒序排列（最新版本在前）。
     *
     * @param id 内容的唯一标识
     * @return 统一响应体 R，包含版本历史列表
     */
    @Operation(summary = "get content version history")
    @GetMapping("/{id}/versions")
    public R<List<ContentVersion>> getVersions(@PathVariable Long id) {
        return R.ok(contentService.listVersions(StpUtil.getLoginIdAsLong(), id));
    }

    /**
     * 对比两个版本之间的字段级差异（V22）。
     *
     * 接口路径：GET /api/v1/content/{id}/versions/diff?from=&lt;n&gt;&amp;to=&lt;m&gt;
     *
     * <p>返回 {@link com.socialflow.model.vo.ContentVersionDiffVO}，前端可据此渲染
     * before/after 对比视图。</p>
     */
    @Operation(summary = "diff two content versions")
    @GetMapping("/{id}/versions/diff")
    public R<com.socialflow.model.vo.ContentVersionDiffVO> diffVersions(
            @PathVariable Long id,
            @RequestParam("from") Integer fromVersion,
            @RequestParam("to") Integer toVersion) {
        return R.ok(contentService.diffVersions(
                StpUtil.getLoginIdAsLong(), id, fromVersion, toVersion));
    }

    /**
     * 导出内容为纯文本文件。
     *
     * 接口路径：GET /api/v1/content/{id}/export
     *
     * 功能：将指定内容的标题和正文导出为 UTF-8 编码的纯文本文件，
     * 浏览器会触发文件下载。
     *
     * @param id 内容的唯一标识
     * @return 纯文本文件的字节数据
     */
    @Operation(summary = "export content as text")
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportContent(@PathVariable Long id) {
        ContentVO content = contentService.get(StpUtil.getLoginIdAsLong(), id);
        String text = (content.getTitle() != null ? content.getTitle() + "\n\n" : "") + content.getBody();
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=content_" + id + ".txt")
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(bytes);
    }

}
