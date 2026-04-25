package com.socialflow.service.knowledge.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.dao.mapper.KnowledgeBaseMapper;
import com.socialflow.dao.mapper.KnowledgeChunkMapper;
import com.socialflow.dao.mapper.KnowledgeDocumentMapper;
import com.socialflow.model.dto.KbCreateDTO;
import com.socialflow.model.dto.KbSearchDTO;
import com.socialflow.model.entity.KnowledgeBase;
import com.socialflow.model.entity.KnowledgeChunk;
import com.socialflow.model.entity.KnowledgeDocument;
import com.socialflow.model.vo.ChunkSearchVO;
import com.socialflow.model.vo.KbDocVO;
import com.socialflow.model.vo.KbVO;
import com.socialflow.service.ai.embedding.EmbeddingService;
import com.socialflow.service.ai.embedding.VectorStoreService;
import com.socialflow.service.ai.rag.RagPipelineService;
import com.socialflow.service.knowledge.KnowledgeIngestService;
import com.socialflow.service.knowledge.KnowledgeService;
import com.socialflow.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库服务实现 —— 管理知识库的创建、文档上传、语义检索和删除等操作。
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    private final KnowledgeBaseMapper baseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeIngestService ingestService;
    private final RagPipelineService ragPipelineService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final StorageService storageService;

    public KnowledgeServiceImpl(KnowledgeBaseMapper baseMapper,
                                 KnowledgeDocumentMapper documentMapper,
                                 KnowledgeChunkMapper chunkMapper,
                                 KnowledgeIngestService ingestService,
                                 RagPipelineService ragPipelineService,
                                 VectorStoreService vectorStoreService,
                                 EmbeddingService embeddingService,
                                 StorageService storageService) {
        this.baseMapper = baseMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.ingestService = ingestService;
        this.ragPipelineService = ragPipelineService;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
        this.storageService = storageService;
    }

    // ==================== 知识库 CRUD ====================

    /**
     * 创建一个新的知识库。
     *
     * @param userId 当前登录用户的 ID
     * @param dto    创建参数
     * @return 新创建的知识库视图对象
     */
    @Override
    public KbVO create(Long userId, KbCreateDTO dto) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setCategory(dto.getCategory());
        kb.setDocCount(0);
        kb.setChunkCount(0);
        // 设置嵌入模型，如果未指定则使用系统默认
        kb.setEmbeddingModel(dto.getEmbeddingModel() != null ? dto.getEmbeddingModel() : "text-embedding-v3");
        kb.setEmbeddingDim(dto.getEmbeddingDim() != null ? dto.getEmbeddingDim() : embeddingService.dimension());
        kb.setStatus(1);
        baseMapper.insert(kb);
        return toKbVO(kb);
    }

    /**
     * 获取当前用户的所有知识库列表。
     *
     * @param userId 当前登录用户的 ID
     * @return 知识库列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<KbVO> list(Long userId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getUserId, userId)
               .orderByDesc(KnowledgeBase::getCreateTime);
        List<KnowledgeBase> kbList = baseMapper.selectList(wrapper);
        List<KbVO> voList = new ArrayList<>();
        for (KnowledgeBase kb : kbList) {
            voList.add(toKbVO(kb));
        }
        return voList;
    }

    /**
     * 获取单个知识库的详细信息。
     *
     * @param userId 当前登录用户的 ID
     * @param kbId   知识库 ID
     * @return 知识库视图对象
     */
    @Override
    @Transactional(readOnly = true)
    public KbVO get(Long userId, Long kbId) {
        KnowledgeBase kb = baseMapper.selectById(kbId);
        checkOwnership(kb, userId);
        return toKbVO(kb);
    }

    /**
     * 删除一个知识库及其所有关联数据（文档、分块、向量、存储文件）。
     *
     * @param userId 当前登录用户的 ID
     * @param kbId   知识库 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long kbId) {
        KnowledgeBase kb = baseMapper.selectById(kbId);
        checkOwnership(kb, userId);

        // 1. 查询文档列表（在删除记录之前，用于后续清理对象存储文件）
        List<KnowledgeDocument> docs = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getKbId, kbId));

        // 2. 删除 MySQL 中的分块记录
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKbId, kbId));

        // 3. 删除 MySQL 中的文档记录
        documentMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKbId, kbId));

        // 4. 删除知识库本身
        baseMapper.deleteById(kbId);

        // 5. 删除 pgvector 中的向量数据
        try {
            vectorStoreService.deleteByFilter("kb_chunks", Map.of("kb_id", kbId));
        } catch (Exception e) {
            log.warn("删除知识库向量数据时出错，kbId={}：{}", kbId, e.getMessage());
        }

        // 6. 清理对象存储中的文件（根据文档记录中保存的 objectKey 逐个删除）
        try {
            for (KnowledgeDocument doc : docs) {
                if (doc.getFileUrl() != null) {
                    storageService.delete(doc.getFileUrl());
                }
            }
        } catch (Exception e) {
            log.warn("清理知识库存储文件时出错，kbId={}：{}", kbId, e.getMessage());
        }

        log.info("知识库已删除，kbId={}", kbId);
    }

    // ==================== 文档管理 ====================

    /**
     * 向知识库上传一个文档。
     * 文件先上传到对象存储，然后创建文档记录（PENDING 状态），最后异步触发摄入流程。
     *
     * @param userId 当前登录用户的 ID
     * @param kbId   目标知识库 ID
     * @param file   上传的文件
     * @return 新创建的文档记录 ID
     */
    @Override
    public Long uploadDocument(Long userId, Long kbId, MultipartFile file) {
        // 校验知识库所有权
        KnowledgeBase kb = baseMapper.selectById(kbId);
        checkOwnership(kb, userId);

        try {
            // 1. 生成对象键：kb/{kbId}/{UUID}_{原始文件名}
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            String objectKey = "kb/" + kbId + "/" + UUID.randomUUID() + "_" + originalFilename;

            // 2. 上传文件到对象存储
            storageService.upload(objectKey, file.getInputStream(), file.getSize(), file.getContentType());

            // 3. 创建文档记录（PENDING 状态）
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setKbId(kbId);
            doc.setFileName(originalFilename);
            doc.setFileUrl(objectKey);
            doc.setFileType(extractFileType(originalFilename));
            doc.setFileSize(file.getSize());
            doc.setParseStatus("PENDING");
            documentMapper.insert(doc);

            // 4. 异步触发文档摄入流程
            ingestService.ingest(doc.getId());

            log.info("文档上传成功，docId={}，kbId={}，文件名={}", doc.getId(), kbId, originalFilename);
            return doc.getId();

        } catch (Exception e) {
            throw new RuntimeException("文档上传失败：" + e.getMessage(), e);
        }
    }

    /**
     * 知识库语义检索，委托给 RAG 管道服务。
     *
     * @param userId 当前登录用户的 ID
     * @param kbId   要检索的知识库 ID
     * @param dto    检索参数
     * @return 匹配的文档片段列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<ChunkSearchVO> search(Long userId, Long kbId, KbSearchDTO dto) {
        // 校验知识库所有权
        KnowledgeBase kb = baseMapper.selectById(kbId);
        checkOwnership(kb, userId);
        return ragPipelineService.retrieve(userId, kbId, dto.getQuery(), dto.getTopK());
    }

    /**
     * 删除知识库中的某个文档及其所有关联数据。
     *
     * @param userId 当前登录用户的 ID
     * @param kbId   知识库 ID
     * @param docId  文档 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long userId, Long kbId, Long docId) {
        // 校验知识库所有权
        KnowledgeBase kb = baseMapper.selectById(kbId);
        checkOwnership(kb, userId);

        // 加载文档并校验归属
        KnowledgeDocument doc = documentMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new RuntimeException("文档不存在或不属于该知识库");
        }

        // 统计该文档的分块数（用于更新知识库统计）
        Long docChunkCount = chunkMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getDocId, docId));

        // 1. 删除 MySQL 中的分块记录
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocId, docId));

        // 2. 删除 pgvector 中的向量数据
        try {
            vectorStoreService.deleteByFilter("kb_chunks", Map.of("doc_id", docId));
        } catch (Exception e) {
            log.warn("删除文档向量数据时出错，docId={}：{}", docId, e.getMessage());
        }

        // 3. 删除 MySQL 中的文档记录
        documentMapper.deleteById(docId);

        // 4. 删除对象存储中的文件
        try {
            storageService.delete(doc.getFileUrl());
        } catch (Exception e) {
            log.warn("删除存储文件时出错，fileUrl={}：{}", doc.getFileUrl(), e.getMessage());
        }

        // 5. 更新知识库统计（递减文档数和分块数）
        kb.setDocCount(Math.max(0, (kb.getDocCount() == null ? 0 : kb.getDocCount()) - 1));
        kb.setChunkCount(Math.max(0, (kb.getChunkCount() == null ? 0 : kb.getChunkCount()) - docChunkCount.intValue()));
        baseMapper.updateById(kb);

        log.info("文档已删除，docId={}，kbId={}", docId, kbId);
    }

    /**
     * 获取知识库中的文档列表。
     *
     * @param userId 当前登录用户的 ID
     * @param kbId   知识库 ID
     * @return 文档列表，按创建时间倒序
     */
    @Override
    @Transactional(readOnly = true)
    public List<KbDocVO> listDocuments(Long userId, Long kbId) {
        // 校验知识库所有权
        KnowledgeBase kb = baseMapper.selectById(kbId);
        checkOwnership(kb, userId);

        // 查询该知识库下的所有文档，按创建时间倒序
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocument::getKbId, kbId)
               .orderByDesc(KnowledgeDocument::getCreateTime);
        List<KnowledgeDocument> docs = documentMapper.selectList(wrapper);

        // 转换为 VO
        List<KbDocVO> voList = new ArrayList<>();
        for (KnowledgeDocument doc : docs) {
            voList.add(toKbDocVO(doc));
        }
        return voList;
    }

    // ==================== 辅助方法 ====================

    /**
     * 校验知识库所有权——确保知识库存在且属于当前用户。
     */
    private void checkOwnership(KnowledgeBase kb, Long userId) {
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此知识库");
        }
    }

    /**
     * 从文件名中提取文件类型（后缀名）。
     */
    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 将 KnowledgeBase 实体转换为 KbVO 视图对象。
     */
    private KbVO toKbVO(KnowledgeBase kb) {
        KbVO vo = new KbVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setCategory(kb.getCategory());
        vo.setDocCount(kb.getDocCount());
        vo.setChunkCount(kb.getChunkCount());
        vo.setEmbeddingModel(kb.getEmbeddingModel());
        vo.setEmbeddingDim(kb.getEmbeddingDim());
        vo.setStatus(kb.getStatus());
        vo.setCreateTime(kb.getCreateTime());
        return vo;
    }

    /**
     * 将 KnowledgeDocument 实体转换为 KbDocVO 视图对象。
     */
    private KbDocVO toKbDocVO(KnowledgeDocument doc) {
        KbDocVO vo = new KbDocVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setFileName(doc.getFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setCharCount(doc.getCharCount());
        vo.setChunkCount(doc.getChunkCount());
        vo.setParseStatus(doc.getParseStatus());
        vo.setParseError(doc.getParseError());
        vo.setCreateTime(doc.getCreateTime());
        return vo;
    }
}
