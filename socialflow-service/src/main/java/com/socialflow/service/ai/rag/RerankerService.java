package com.socialflow.service.ai.rag;

import java.util.List;

/**
 * 重排序（Reranker）服务接口——对候选文本片段进行精细化相关度排序。
 *
 * 【什么是重排序（Reranking）？】
 *
 * 在 RAG 管道中，向量检索和关键词检索能快速召回大量候选片段（粗排），
 * 但这些片段的排序可能不够精确。Reranker 就是一个"精排"模型，
 * 它接收用户查询和每个候选片段，逐一计算"查询-片段"的相关度得分，
 * 然后按得分重新排序，只保留最相关的 top-K 个片段。
 *
 * 【Cross-Encoder 与 Bi-Encoder 的区别】
 *
 * 本服务默认使用 BGE-Reranker-v2-m3，这是一个 Cross-Encoder 模型：
 *     - Bi-Encoder（向量检索使用的）：将查询和文本分别编码为向量，
 *       速度快但精度一般，适合粗排
 *     - Cross-Encoder（Reranker 使用的）：将查询和文本拼接在一起
 *       送入模型联合编码，精度高但速度较慢，适合对少量候选进行精排
 *
 * 【在系统中的位置】
 *
 * 位于 RAG 管道的倒数第二步。前面的融合阶段产出 10-20 个候选片段，
 * Reranker 从中挑选出最终的 top-N（通常 3-5 个）传给 LLM 作为参考资料。
 */
public interface RerankerService {

    /**
     * 对候选文本列表进行重排序。
     *
     * @param query 用户的查询文本
     * @param texts 候选文本列表（通常是融合阶段的 10-20 个片段）
     * @param topK  最终保留的片段数量
     * @return 按相关度从高到低排序的索引-得分列表，
     *         长度 = min(topK, texts.size())。
     *         索引值对应 {@code texts} 列表中的位置
     */
    List<ScoredIndex> rerank(String query, List<String> texts, int topK);

    /**
     * 带得分的索引记录。
     *
     * @param index 在原始候选列表中的索引位置
     * @param score 相关度得分（越高越相关）
     */
    record ScoredIndex(int index, double score) {}
}
