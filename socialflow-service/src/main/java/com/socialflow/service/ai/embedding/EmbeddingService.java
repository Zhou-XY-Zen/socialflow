package com.socialflow.service.ai.embedding;

import java.util.List;

/**
 * 文本向量嵌入（Embedding）服务接口。
 *
 * 【什么是 Embedding（向量嵌入）？】
 *
 * Embedding 是一种将文本转换为数学向量（一组浮点数）的技术。
 * 转换后的向量能够"捕捉"文本的语义信息——意思相近的文本，其向量在高维空间中
 * 的距离也更近。例如，"苹果手机"和"iPhone"的向量会非常接近，
 * 而"苹果手机"和"西红柿炒蛋"的向量则相距很远。
 *
 * 【什么是 BGE-M3？】
 *
 * BGE-M3 是由智源研究院（BAAI）开源的一款多语言、多粒度嵌入模型。
 * "M3" 代表 Multi-Lingual（多语言）、Multi-Functionality（多功能）、
 * Multi-Granularity（多粒度）。它的向量维度为 1024，同时支持中文和英文，
 * 非常适合本系统的社交媒体内容处理场景。
 *
 * 【Embedding 在系统中的作用】
 *
 * Embedding 是实现 RAG（检索增强生成）的基础：
 *     - 知识库文档被切分成小段后，每段文本通过本服务转换为向量并存入向量数据库
 *     - 用户提问时，问题也被转换为向量
 *     - 通过向量相似度搜索，找到与问题最相关的知识片段
 * 实现类可以调用本地部署的模型服务或云端 API，并对相同输入进行缓存
 * （因为同一段文本的嵌入结果是确定性的，不会变化）。
 */
public interface EmbeddingService {

    /**
     * 返回底层嵌入模型的向量维度。
     *
     * 对于 BGE-M3 模型，维度为 1024。向量数据库建表/建索引时需要此信息。
     *
     * @return 向量维度（整数）
     */
    int dimension();

    /**
     * 对单条文本生成向量嵌入。
     *
     * @param text 要嵌入的文本
     * @return 浮点数向量，长度等于 {@link #dimension()}
     */
    float[] embed(String text);

    /**
     * 批量文本向量嵌入。
     *
     * 一次性处理多条文本，减少网络开销。实现类应遵守配置项
     * {@code socialflow.embedding.batch-size} 的限制，避免单次请求过大导致 API 超限。
     *
     * @param texts 要嵌入的文本列表
     * @return 向量列表，顺序与输入文本一一对应
     */
    List<float[]> embedBatch(List<String> texts);
}
