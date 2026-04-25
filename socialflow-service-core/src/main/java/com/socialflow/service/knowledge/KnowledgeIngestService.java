package com.socialflow.service.knowledge;

/**
 * 知识库文档摄入服务（异步文档解析管道）。
 *
 * 这是知识库系统的后台处理组件，负责将用户上传的文档转换为可检索的知识。
 * 整个处理流程（也叫"摄入管道"）包含以下步骤：
 *     - 解析文档 —— 提取文档中的纯文本内容（支持 PDF、Word、TXT 等格式）
 *     - 文本分块（Chunking）—— 将长文本切分成适合检索的小段落
 *     - 向量嵌入（Embedding）—— 调用 AI 模型将每个文本块转换为向量
 *     - 持久化存储 —— 将文本块保存到 MySQL，将向量保存到向量数据库
 *
 * 调用时机：当 {@link KnowledgeService#uploadDocument} 将文档以 {@code PENDING}（待处理）
 * 状态保存到数据库后，会异步调用本服务的 {@link #ingest} 方法来执行上述流程。
 *
 * 状态流转：
 *     - 处理成功：文档状态更新为 {@code COMPLETED}
 *     - 处理失败：文档状态更新为 {@code FAILED}，错误信息记录在 {@code parse_error} 字段中
 *
 * 对应的 Controller：无直接对应的 Controller，由 {@link KnowledgeService} 内部触发调用。
 *
 * @see KnowledgeService#uploadDocument 文档上传入口（触发本服务）
 */
public interface KnowledgeIngestService {

    /**
     * 执行文档摄入处理（解析 -> 分块 -> 嵌入 -> 存储）。
     *
     * 这是一个容错方法 —— 即使处理过程中发生异常也不会向上抛出，
     * 而是将错误信息记录到文档的 {@code parse_error} 字段中，并将状态标记为 {@code FAILED}。
     * 这样做是为了防止异步任务的异常影响到主流程。
     *
     * @param docId 要处理的文档记录 ID（由 uploadDocument 方法创建并返回）
     */
    void ingest(Long docId);
}
