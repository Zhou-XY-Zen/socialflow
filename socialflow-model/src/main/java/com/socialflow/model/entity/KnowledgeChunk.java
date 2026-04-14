package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文本切片实体类 —— 对应数据库表 `knowledge_chunk`
 *
 * 【作用】存储知识库文档被切分后产生的每一个文本片段（Chunk）。
 *   这是 RAG（检索增强生成）的核心数据单元。当用户使用知识库生成文案时，
 *   系统会先在向量数据库中检索最相关的切片，然后将切片内容作为上下文传给大模型。
 *
 * 【为什么需要它】
 *   大模型的上下文窗口有限，无法一次性读取整篇长文档。
 *   通过将文档切分为较小的片段，可以只检索最相关的部分提供给 AI，
 *   既节省 Token 又提高了信息的针对性。
 *
 * 【关联关系】
 *   - knowledge_chunk.doc_id → knowledge_document.id （来源文档）
 *   - knowledge_chunk.kb_id → knowledge_base.id （所属知识库，冗余字段便于查询）
 *
 * 【使用场景】
 *   - 文档上传并解析后，系统自动将内容切分为多个 chunk 并存入本表
 *   - RAG 检索时，根据用户查询在向量数据库中搜索最相似的 chunk
 *   - 检索结果展示时，返回 chunk 的文本内容和来源信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_chunk")
public class KnowledgeChunk extends BaseEntity {

    /**
     * 来源文档 ID
     *
     * 关联 knowledge_document.id，标识该切片来自哪个文档。
     */
    private Long docId;

    /**
     * 所属知识库 ID
     *
     * 关联 knowledge_base.id。这是一个冗余字段，
     * 虽然可以通过 docId 间接查到 kbId，但直接存储便于按知识库维度查询切片。
     */
    private Long kbId;

    /**
     * 切片序号
     *
     * 该切片在所属文档中的顺序编号，从 0 开始递增。
     * 用于还原切片在原文档中的位置顺序。
     */
    private Integer chunkIndex;

    /**
     * 切片文本内容
     *
     * 切片的完整纯文本内容，即从文档中截取出的一段文字。
     * 这是 RAG 检索时返回给大模型作为参考上下文的核心数据。
     */
    private String contentText;

    /**
     * Token 数量
     *
     * 该切片文本的 Token 数（按 Embedding 模型的分词器计算）。
     * 用于控制切片大小和估算 AI 调用的上下文消耗。
     */
    private Integer tokenCount;

    /**
     * 向量数据库中的向量 ID
     *
     * 该切片的文本经过 Embedding 模型转换为向量后，存入向量数据库的 ID。
     * RAG 检索时先在向量数据库中按相似度搜索，再根据此 ID 回查本表获取原文。
     */
    private String vectorId;

    /**
     * 切片元数据（JSON 格式）
     *
     * 记录该切片在原文档中的位置等附加信息。
     * JSON 格式示例：{"page": 3, "section": "产品成分", "source": "产品手册.pdf"}
     * - page: 所在页码
     * - section: 所在章节标题
     * - source: 来源文件名
     */
    private String metadata;

    /**
     * Embedding 向量的字节序列（备份存储）
     *
     * 主要向量存储在 pgvector 中，此字段作为 MySQL 侧的备份。
     * float[] 通过 ByteBuffer 序列化为 byte[]（1024维 * 4字节 = 4KB）。
     */
    private byte[] embedding;
}
