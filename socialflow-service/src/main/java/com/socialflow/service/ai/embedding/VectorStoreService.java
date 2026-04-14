package com.socialflow.service.ai.embedding;

import java.util.List;
import java.util.Map;

/**
 * 向量数据库（Vector Store / Vector DB）操作抽象接口。
 *
 * 【什么是向量数据库？】
 *
 * 向量数据库是专门用来存储和检索高维向量的数据库。传统数据库（如 MySQL）
 * 擅长精确匹配查询（如 WHERE id = 1），而向量数据库擅长"相似度查询"——
 * 给定一个向量，快速找到数据库中与它最接近（最相似）的 N 个向量。
 *
 * 【什么是相似度搜索（Similarity Search）？】
 *
 * 将用户的问题转成向量后，在向量数据库中找到距离最近的文本向量，
 * 距离越近说明语义越相关。常见的距离度量有余弦相似度（Cosine Similarity）
 * 和欧氏距离（L2 Distance）。
 *
 * 【什么是元数据过滤（Metadata Filtering）？】
 *
 * 每个向量在存储时可以附带一组键值对（metadata），如 user_id、kb_id 等。
 * 查询时可以通过 filter 参数限定只在某个用户或某个知识库的范围内搜索，
 * 这是实现数据隔离的关键机制——确保用户 A 查不到用户 B 的数据。
 *
 * 【在系统中的位置】
 *
 * 本接口有两种实现：pgvector（基于 PostgreSQL 扩展）和 Milvus（专用向量数据库）。
 * 通过配置文件决定加载哪个实现。RAG 管道的向量检索步骤依赖本服务。
 *
 * 安全约束：每次查询必须携带 metadata filter（user_id / kb_id），
 * 以保证数据隔离安全性，防止跨用户数据泄漏。
 */
public interface VectorStoreService {

    /**
     * 插入或更新单条向量（Upsert = Update + Insert）。
     *
     * 如果该向量 ID 已存在则更新，否则新增。
     *
     * @param collection 集合名称（类似数据库中的"表"）
     * @param vector     浮点数向量
     * @param metadata   附带的元数据键值对，如 {@code {user_id: 1, kb_id: 2, chunk_id: 100}}
     * @return 该向量的唯一 ID 字符串
     */
    String upsert(String collection, float[] vector, Map<String, Object> metadata);

    /**
     * 批量插入 / 更新向量。
     *
     * @param collection   集合名称
     * @param vectors      向量列表
     * @param metadataList 元数据列表，顺序与 vectors 一一对应
     * @return ID 列表，顺序与输入一一对应
     */
    List<String> upsertBatch(String collection,
                             List<float[]> vectors,
                             List<Map<String, Object>> metadataList);

    /**
     * 根据 ID 列表删除向量。
     *
     * @param collection 集合名称
     * @param ids        要删除的向量 ID 列表
     */
    void deleteByIds(String collection, List<String> ids);

    /**
     * 根据元数据条件批量删除向量。
     *
     * 例如删除某个知识库的所有向量：{@code filter = {kb_id: 2}}。
     *
     * @param collection 集合名称
     * @param filter     元数据过滤条件
     */
    void deleteByFilter(String collection, Map<String, Object> filter);

    /**
     * 相似度搜索——核心检索方法。
     *
     * 给定一个查询向量，在指定集合中找到与之最相似的 topK 条记录。
     * 结果按相似度得分从高到低排列。
     *
     * @param collection 集合名称
     * @param query      查询向量（通常由用户问题经过 Embedding 转换得到）
     * @param filter     必填的元数据过滤条件（如 {@code {user_id=1, kb_id=2}}），
     *                   确保数据隔离
     * @param topK       返回结果数量
     * @return 搜索命中列表，包含向量 ID、相似度得分和元数据
     */
    List<SearchHit> search(String collection,
                           float[] query,
                           Map<String, Object> filter,
                           int topK);

    /**
     * 搜索命中结果记录。
     *
     * @param id       命中向量的唯一 ID
     * @param score    相似度得分（越高越相关）
     * @param metadata 该向量附带的元数据
     */
    record SearchHit(String id, double score, Map<String, Object> metadata) {}
}
