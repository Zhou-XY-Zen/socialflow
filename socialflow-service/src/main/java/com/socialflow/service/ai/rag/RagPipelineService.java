package com.socialflow.service.ai.rag;

import com.socialflow.model.vo.ChunkSearchVO;

import java.util.List;

/**
 * RAG（检索增强生成）端到端检索管道服务接口。
 *
 * 【什么是 RAG（Retrieval-Augmented Generation，检索增强生成）？】
 *
 * RAG 是一种结合"信息检索"和"文本生成"的 AI 技术模式。
 * 其核心思想是：在让 LLM 回答问题之前，先从知识库中检索与问题最相关的文档片段，
 * 将这些片段作为"参考资料"一起发送给 LLM。这样做的好处是：
 *     - 减少幻觉：LLM 生成内容时有据可依，不会"凭空编造"
 *     - 知识可更新：不需要重新训练模型，只需更新知识库即可
 *     - 可追溯：生成内容可以注明出处，便于验证
 *
 * 【RAG 管道的完整处理流程】
 *     - HyDE 查询重写（可选）：用 LLM 生成一个"假设性回答"，
 *       用它代替原始问题去检索，提高召回率
 *     - 向量嵌入：将查询文本转换为向量
 *     - 向量搜索：在 {@code kb_chunks} 向量集合中按语义相似度检索，
 *       通过 kb_id 过滤确保只搜索目标知识库
 *     - BM25 关键词搜索：通过 MySQL 全文索引进行传统关键词匹配搜索
 *     - RRF 融合去重：将向量搜索和关键词搜索的结果合并排序
 *     - 重排序（Rerank）：使用 BGE-Reranker 模型对候选片段精排，
 *       从粗排 top-k 缩减到精排 top-n
 *     - 补全元数据：从 MySQL 中加载完整的片段信息（文档标题、来源等）
 *
 * 【在系统中的位置】
 *
 * 当用户开启 RAG 模式生成内容时，业务层先调用本服务获取相关知识片段，
 * 再将片段文本通过 {@link com.socialflow.service.ai.prompt.PromptService}
 * 注入到提示词中，最后发送给 LLM 生成最终内容。
 */
public interface RagPipelineService {

    /**
     * 从知识库中检索并排序知识片段。
     *
     * 执行完整的 RAG 检索管道（从查询重写到重排序），
     * 返回按相关度排序的知识片段列表。
     *
     * @param userId 调用者用户 ID，用于审计和速率限制
     * @param kbId   要检索的知识库 ID
     * @param query  用户的原始查询文本
     * @param topK   重排序后最终返回的片段数量
     * @return 按相关度排序的知识片段 VO 列表
     */
    List<ChunkSearchVO> retrieve(Long userId, Long kbId, String query, int topK);

    /**
     * 检索知识片段并格式化为可直接注入提示词的上下文字符串。
     *
     * 功能与 {@link #retrieve} 相同，但额外将结果拼接为单个字符串，
     * 每个片段以 {@code [参考1] [参考2] ...} 的格式编号，方便直接嵌入 Prompt。
     *
     * @param userId 调用者用户 ID
     * @param kbId   知识库 ID
     * @param query  用户查询文本
     * @param topK   返回片段数量
     * @return 格式化后的参考资料上下文字符串
     */
    String retrieveAsContext(Long userId, Long kbId, String query, int topK);
}
