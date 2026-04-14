package com.socialflow.service.knowledge.impl;

import com.socialflow.dao.mapper.KnowledgeBaseMapper;
import com.socialflow.dao.mapper.KnowledgeChunkMapper;
import com.socialflow.dao.mapper.KnowledgeDocumentMapper;
import com.socialflow.model.entity.KnowledgeBase;
import com.socialflow.model.entity.KnowledgeChunk;
import com.socialflow.model.entity.KnowledgeDocument;
import com.socialflow.service.ai.embedding.EmbeddingService;
import com.socialflow.service.ai.embedding.VectorStoreService;
import com.socialflow.service.ai.rag.DocumentChunker;
import com.socialflow.service.knowledge.KnowledgeIngestService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库文档摄入服务实现 —— 异步执行文档解析、分块、向量嵌入和持久化。
 *
 * 处理流程：下载文件 → Tika 解析 → 文本分块 → 批量嵌入 → 存储到 MySQL 和 pgvector。
 */
@Service
public class KnowledgeIngestServiceImpl implements KnowledgeIngestService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestServiceImpl.class);

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeBaseMapper baseMapper;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentChunker documentChunker;

    /** MinIO 对象存储端点地址 */
    @Value("${socialflow.storage.endpoint}")
    private String minioEndpoint;

    /** MinIO 访问密钥 */
    @Value("${socialflow.storage.access-key}")
    private String minioAccessKey;

    /** MinIO 秘密密钥 */
    @Value("${socialflow.storage.secret-key}")
    private String minioSecretKey;

    /** MinIO 存储桶名称 */
    @Value("${socialflow.storage.bucket}")
    private String minioBucket;

    public KnowledgeIngestServiceImpl(KnowledgeDocumentMapper documentMapper,
                                      KnowledgeChunkMapper chunkMapper,
                                      KnowledgeBaseMapper baseMapper,
                                      EmbeddingService embeddingService,
                                      VectorStoreService vectorStoreService,
                                      DocumentChunker documentChunker) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.baseMapper = baseMapper;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.documentChunker = documentChunker;
    }

    /**
     * 异步执行文档摄入处理（解析 → 分块 → 嵌入 → 存储）。
     *
     * 整个方法被 try-catch 包裹，任何异常都会将文档状态设为 FAILED 并记录错误信息，
     * 不会向上抛出异常，避免异步任务的异常影响主流程。
     *
     * @param docId 要处理的文档记录 ID
     */
    @Async
    @Override
    public void ingest(Long docId) {
        try {
            // 1. 加载文档记录
            KnowledgeDocument doc = documentMapper.selectById(docId);
            if (doc == null) {
                log.warn("摄入任务找不到文档记录，docId={}", docId);
                return;
            }

            // 2. 更新文档状态为 PARSING（解析中）
            doc.setParseStatus("PARSING");
            documentMapper.updateById(doc);

            // 3. 从 MinIO 下载文件
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(minioEndpoint)
                    .credentials(minioAccessKey, minioSecretKey)
                    .build();

            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(doc.getFileUrl())
                            .build()
            );

            // 4. 使用 Apache Tika 解析文档内容为纯文本
            String rawText = new Tika().parseToString(inputStream);
            inputStream.close();

            // 5. 更新文档字符数
            doc.setCharCount(rawText.length());

            // 6. 文本分块
            List<DocumentChunker.Chunk> chunks = documentChunker.chunk(rawText);

            // 7. 收集所有分块文本，批量生成嵌入向量
            List<String> chunkTexts = new ArrayList<>();
            for (DocumentChunker.Chunk chunk : chunks) {
                chunkTexts.add(chunk.text());
            }
            List<float[]> embeddings = embeddingService.embedBatch(chunkTexts);

            // 8. 逐个处理分块：保存到 MySQL 和 pgvector
            int chunkCount = 0;
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunker.Chunk chunk = chunks.get(i);
                float[] embedding = embeddings.get(i);

                // 构建元数据 JSON
                Map<String, Object> metadataMap = new HashMap<>();
                metadataMap.put("kb_id", doc.getKbId());
                metadataMap.put("doc_id", doc.getId());
                metadataMap.put("chunk_index", chunk.index());
                metadataMap.put("source", doc.getFileName());

                // 创建 KnowledgeChunk 实体
                KnowledgeChunk chunkEntity = new KnowledgeChunk();
                chunkEntity.setDocId(doc.getId());
                chunkEntity.setKbId(doc.getKbId());
                chunkEntity.setChunkIndex(chunk.index());
                chunkEntity.setContentText(chunk.text());
                chunkEntity.setTokenCount(chunk.tokenCount());
                chunkEntity.setVectorId(UUID.randomUUID().toString());
                chunkEntity.setMetadata(toJson(metadataMap));

                // 将 float[] 嵌入向量序列化为 byte[] 备份存储
                chunkEntity.setEmbedding(floatArrayToBytes(embedding));

                // 9. 插入分块记录到 MySQL
                chunkMapper.insert(chunkEntity);

                // 10. 存储向量到 pgvector，获取返回的向量 ID
                String vectorId = vectorStoreService.upsert("kb_chunks", embedding, metadataMap);

                // 11. 更新分块的 vectorId 为 pgvector 返回的实际 ID
                chunkEntity.setVectorId(vectorId);
                chunkMapper.updateById(chunkEntity);

                chunkCount++;
            }

            // 12. 更新文档：分块数量、解析状态为 COMPLETED
            doc.setChunkCount(chunkCount);
            doc.setParseStatus("COMPLETED");
            doc.setParseError(null);
            documentMapper.updateById(doc);

            // 13. 更新知识库统计：递增文档数和分块数
            KnowledgeBase kb = baseMapper.selectById(doc.getKbId());
            if (kb != null) {
                kb.setDocCount((kb.getDocCount() == null ? 0 : kb.getDocCount()) + 1);
                kb.setChunkCount((kb.getChunkCount() == null ? 0 : kb.getChunkCount()) + chunkCount);
                baseMapper.updateById(kb);
            }

            log.info("文档摄入完成，docId={}，生成 {} 个分块", docId, chunkCount);

        } catch (Exception e) {
            // 异常处理：更新文档状态为 FAILED，记录错误信息
            log.error("文档摄入失败，docId={}", docId, e);
            try {
                KnowledgeDocument doc = documentMapper.selectById(docId);
                if (doc != null) {
                    doc.setParseStatus("FAILED");
                    doc.setParseError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    documentMapper.updateById(doc);
                }
            } catch (Exception ex) {
                log.error("更新文档失败状态时出错，docId={}", docId, ex);
            }
        }
    }

    /**
     * 将 Map 转换为 JSON 字符串（简单实现，用于元数据序列化）。
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof String) {
                sb.append("\"").append(((String) val).replace("\"", "\\\"")).append("\"");
            } else {
                sb.append(val);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 将 float 数组序列化为 byte 数组（用于 MySQL 侧的向量备份存储）。
     */
    private byte[] floatArrayToBytes(float[] array) {
        ByteBuffer buffer = ByteBuffer.allocate(array.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : array) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }
}
