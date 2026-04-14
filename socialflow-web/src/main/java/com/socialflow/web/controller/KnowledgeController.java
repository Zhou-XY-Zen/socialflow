package com.socialflow.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.R;
import com.socialflow.model.dto.KbCreateDTO;
import com.socialflow.model.dto.KbSearchDTO;
import com.socialflow.model.vo.ChunkSearchVO;
import com.socialflow.model.vo.KbDocVO;
import com.socialflow.model.vo.KbVO;
import com.socialflow.service.knowledge.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库控制器 —— 管理用户的 RAG 知识库，支持文档上传和语义检索。
 *
 * 本控制器处理的基础 URL 路径为 {@code /api/v1/kb}，提供以下功能：
 *     - 知识库的创建、查看、删除（CRUD）
 *     - 向知识库上传文档（异步解析和向量化）
 *     - 知识库语义检索测试（返回最相关的文档片段）
 *     - 删除知识库中的文档及其向量数据
 *
 * 知识库是 RAG（检索增强生成）功能的基础。用户上传文档后，系统会自动将文档
 * 切分成小片段（chunk），并通过向量化存储到向量数据库中。在内容生成时，
 * 系统可以检索相关片段作为上下文，让 AI 生成更准确的内容。
 *
 * 使用的 HTTP 方法：
 *     - POST   —— 创建知识库、上传文档、执行搜索
 *     - GET    —— 获取知识库列表和详情
 *     - DELETE —— 删除知识库或文档
 *
 * @see KnowledgeService 知识库业务逻辑的具体实现
 */
/*
 * @Tag           —— Swagger 文档分组标签，显示为 "knowledge-base"
 * @RestController —— REST 控制器，返回值自动转为 JSON
 * @RequestMapping —— 公共路径前缀：/api/v1/kb
 * @RequiredArgsConstructor —— Lombok 自动生成构造函数，注入 final 依赖
 */
@Tag(name = "knowledge-base")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/kb")
@RequiredArgsConstructor
public class KnowledgeController {

    /** 知识库服务，封装知识库相关的所有业务逻辑 */
    private final KnowledgeService knowledgeService;

    /**
     * 创建知识库。
     *
     * 接口路径：POST /api/v1/kb
     *
     * 功能：为当前用户创建一个新的知识库。知识库用于存储文档，
     * 为后续的 RAG 内容生成提供知识基础。
     *
     * @param dto 创建请求参数（包含知识库名称、描述等）
     * @return 统一响应体 R，包含新创建的知识库信息 KbVO
     */
    @Operation(summary = "create knowledge base")
    @PostMapping
    public R<KbVO> create(@Valid @RequestBody KbCreateDTO dto) {
        return R.ok(knowledgeService.create(StpUtil.getLoginIdAsLong(), dto));
    }

    /**
     * 获取当前用户的知识库列表。
     *
     * 接口路径：GET /api/v1/kb/list
     *
     * 功能：查询当前登录用户创建的所有知识库。前端的知识库管理页面调用此接口。
     *
     * @return 统一响应体 R，包含知识库列表
     */
    @Operation(summary = "list user's knowledge bases")
    @GetMapping("/list")
    public R<List<KbVO>> list() {
        return R.ok(knowledgeService.list(StpUtil.getLoginIdAsLong()));
    }

    /**
     * 获取知识库详情。
     *
     * 接口路径：GET /api/v1/kb/{id}
     *
     * 功能：根据知识库 ID 获取详细信息，包括名称、描述、文档数量等。
     *
     * @param id 知识库 ID（从 URL 路径中获取）
     * @return 统一响应体 R，包含知识库详情 KbVO
     */
    @Operation(summary = "knowledge base detail")
    @GetMapping("/{id}")
    public R<KbVO> get(@PathVariable Long id) {
        return R.ok(knowledgeService.get(StpUtil.getLoginIdAsLong(), id));
    }

    /**
     * 删除知识库。
     *
     * 接口路径：DELETE /api/v1/kb/{id}
     *
     * 功能：删除指定的知识库及其所有关联数据（文档、向量索引等）。
     * 此操作不可逆，请谨慎使用。
     *
     * @param id 要删除的知识库 ID
     * @return 统一响应体 R，无数据体
     */
    @Operation(summary = "delete knowledge base")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    /**
     * 向知识库上传文档（异步解析）。
     *
     * 接口路径：POST /api/v1/kb/{kbId}/upload
     *
     * 功能：上传一个文档文件到指定知识库。上传后系统会在后台异步执行以下操作：
     *     - 解析文档内容（支持 PDF、Word、TXT 等格式）
     *     - 将文档切分成小片段（chunk）
     *     - 对每个片段进行向量化（embedding）
     *     - 将向量存储到向量数据库中
     *
     * 注意：使用 @RequestPart 接收文件，前端需要以 multipart/form-data 格式提交。
     *
     * @param kbId 目标知识库的 ID（从 URL 路径中获取）
     * @param file 上传的文档文件（通过 multipart 表单上传）
     * @return 统一响应体 R，包含新创建的文档记录 ID
     */
    @Operation(summary = "upload a document to a knowledge base (async parse)")
    @PostMapping("/{kbId}/upload")
    public R<Long> upload(@PathVariable Long kbId,
                           @RequestPart("file") MultipartFile file) {
        return R.ok(knowledgeService.uploadDocument(StpUtil.getLoginIdAsLong(), kbId, file));
    }

    /**
     * 知识库检索测试（Top-K 片段检索）。
     *
     * 接口路径：POST /api/v1/kb/{kbId}/search
     *
     * 功能：在指定知识库中进行语义检索，返回与查询内容最相关的前 K 个文档片段。
     * 可用于测试知识库的检索效果，验证文档是否被正确解析和索引。
     *
     * 底层使用向量相似度搜索，而非传统的关键词匹配，能理解语义上的相似性。
     *
     * @param kbId 知识库 ID
     * @param dto  搜索请求参数（包含查询文本、返回数量 topK 等）
     * @return 统一响应体 R，包含匹配的文档片段列表 ChunkSearchVO（含片段内容和相似度分数）
     */
    @Operation(summary = "retrieval test (top-K chunks)")
    @PostMapping("/{kbId}/search")
    public R<List<ChunkSearchVO>> search(@PathVariable Long kbId,
                                          @Valid @RequestBody KbSearchDTO dto) {
        return R.ok(knowledgeService.search(StpUtil.getLoginIdAsLong(), kbId, dto));
    }

    /**
     * 获取知识库中的文档列表。
     *
     * 接口路径：GET /api/v1/kb/{kbId}/docs
     *
     * 功能：查询指定知识库下的所有文档，包含解析状态等信息。
     * 前端可通过轮询此接口来跟踪文档的解析进度。
     *
     * @param kbId 知识库 ID
     * @return 统一响应体 R，包含文档列表 KbDocVO
     */
    @Operation(summary = "list documents in a knowledge base")
    @GetMapping("/{kbId}/docs")
    public R<List<KbDocVO>> listDocs(@PathVariable Long kbId) {
        return R.ok(knowledgeService.listDocuments(StpUtil.getLoginIdAsLong(), kbId));
    }

    /**
     * 删除文档并清理向量数据。
     *
     * 接口路径：DELETE /api/v1/kb/{kbId}/doc/{docId}
     *
     * 功能：从指定知识库中删除一个文档，同时清理该文档在向量数据库中的所有片段向量。
     * 确保删除操作的完整性，不留下孤立的向量数据。
     *
     * @param kbId  知识库 ID
     * @param docId 要删除的文档 ID
     * @return 统一响应体 R，无数据体
     */
    @Operation(summary = "delete document and clean up vectors")
    @DeleteMapping("/{kbId}/doc/{docId}")
    public R<Void> deleteDoc(@PathVariable Long kbId, @PathVariable Long docId) {
        knowledgeService.deleteDocument(StpUtil.getLoginIdAsLong(), kbId, docId);
        return R.ok();
    }
}
