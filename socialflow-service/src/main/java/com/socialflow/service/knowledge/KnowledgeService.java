package com.socialflow.service.knowledge;

import com.socialflow.model.dto.KbCreateDTO;
import com.socialflow.model.dto.KbSearchDTO;
import com.socialflow.model.vo.ChunkSearchVO;
import com.socialflow.model.vo.KbDocVO;
import com.socialflow.model.vo.KbVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库服务接口 —— 管理用户的私有知识库。
 *
 * 负责的业务领域：知识库的创建、文档上传与解析、语义检索、以及知识库的基础管理操作。
 *
 * 知识库是 RAG（检索增强生成）的数据来源。用户可以上传自己的产品文档、营销资料等，
 * 系统会将这些文档切分成小块（chunk）并生成向量嵌入（embedding），
 * 在生成内容时可以从知识库中检索相关信息，让 AI 生成更准确、更有依据的文案。
 *
 * 对应的 Controller：{@code KnowledgeController}，路由前缀为 {@code /api/v1/knowledge/*}。
 *
 * @see KnowledgeIngestService 异步文档解析管道（上传后的后台处理）
 */
public interface KnowledgeService {

    /**
     * 创建一个新的知识库。
     *
     * 知识库是文档的容器，用户可以创建多个知识库来分类管理不同领域的资料。
     * 例如：一个知识库存放护肤品资料，另一个存放食品资料。
     *
     * @param userId 当前登录用户的 ID
     * @param dto    创建参数，包含知识库名称、描述等基本信息
     * @return 新创建的知识库视图对象
     */
    KbVO create(Long userId, KbCreateDTO dto);

    /**
     * 获取当前用户的所有知识库列表。
     *
     * @param userId 当前登录用户的 ID（只返回该用户自己的知识库）
     * @return 知识库列表
     */
    List<KbVO> list(Long userId);

    /**
     * 获取单个知识库的详细信息。
     *
     * @param userId 当前登录用户的 ID（用于权限校验）
     * @param kbId   知识库的主键 ID
     * @return 知识库视图对象，包含名称、文档数量、状态等信息
     */
    KbVO get(Long userId, Long kbId);

    /**
     * 删除一个知识库及其所有关联数据（文档、向量等）。
     *
     * @param userId 当前登录用户的 ID（用于权限校验）
     * @param kbId   要删除的知识库 ID
     */
    void delete(Long userId, Long kbId);

    /**
     * 向知识库上传一个文档。
     *
     * 上传后文档会立即以 {@code PENDING}（待处理）状态保存到数据库。
     * 实际的解析、分块（chunking）和向量嵌入（embedding）操作由
     * {@link KnowledgeIngestService} 在后台异步完成。
     *
     * 支持的文件格式通常包括：PDF、Word、TXT、Markdown 等。
     *
     * @param userId 当前登录用户的 ID
     * @param kbId   目标知识库的 ID
     * @param file   上传的文件（Spring 的 MultipartFile 对象）
     * @return 新创建的文档记录 ID，前端可用此 ID 轮询解析进度
     */
    Long uploadDocument(Long userId, Long kbId, MultipartFile file);

    /**
     * 知识库语义检索（检索游乐场）。
     *
     * 用户输入一段查询文本，系统会将其向量化后在知识库中检索最相关的文档片段。
     * 返回的每个 chunk 都带有相似度得分（score），得分越高表示越相关。
     *
     * 这个方法也是 RAG 流程中检索步骤的底层实现。
     *
     * @param userId 当前登录用户的 ID
     * @param kbId   要检索的知识库 ID
     * @param dto    检索参数，包含查询文本、返回数量（topK）等
     * @return 匹配的文档片段列表，按相似度得分降序排列
     */
    List<ChunkSearchVO> search(Long userId, Long kbId, KbSearchDTO dto);

    /**
     * 删除知识库中的某个文档，并清理其关联的向量数据。
     *
     * 同时会从 MySQL 中删除文档记录和所有 chunk 记录，
     * 以及从向量数据库中删除对应的向量嵌入。
     *
     * @param userId 当前登录用户的 ID（用于权限校验）
     * @param kbId   知识库 ID
     * @param docId  要删除的文档 ID
     */
    void deleteDocument(Long userId, Long kbId, Long docId);

    /**
     * 获取知识库中的文档列表。
     *
     * @param userId 当前登录用户的 ID（用于权限校验）
     * @param kbId   知识库 ID
     * @return 文档列表，按创建时间倒序排列
     */
    List<KbDocVO> listDocuments(Long userId, Long kbId);
}
