package com.socialflow.service.ai.rag;

import java.util.List;

/**
 * 文档分块器（Document Chunker）接口——将长文本切分为适合检索的小段。
 *
 * 【什么是分块（Chunking）？】
 *
 * LLM 和向量嵌入模型对输入文本长度有限制，而且过长的文本会导致语义信息被稀释。
 * 因此，在将文档存入知识库之前，需要把长文本切分成较短的"块"（Chunk），
 * 每个块通常包含 200-1000 个 token。分块的质量直接影响 RAG 的检索效果。
 *
 * 【递归字符分块策略】
 *
 * 本接口采用"递归字符分块"方法，按照分隔符优先级进行切分：
 * {@code \n\n}（段落）&gt; {@code \n}（换行）&gt; {@code 。}（句号）
 * &gt; {@code .}（英文句号）&gt; {@code ;}（分号）&gt; {@code ,}（逗号）。
 * 即优先在段落边界处切分，保证每个块尽量是一个完整的语义单元。
 * 如果切出的块太小（低于 minChunkTokens），则合并到前一个块中。
 *
 * 【配置参数】
 *     - {@code socialflow.rag.chunk-size}：每个块的目标 token 数量
 *     - {@code socialflow.rag.chunk-overlap}：相邻块之间的重叠 token 数量，
 *       重叠是为了避免在切分边界处丢失上下文信息
 *
 * 【在系统中的位置】
 *
 * 用户上传文档到知识库时，系统先提取文本，再通过本服务切分为块，
 * 然后对每个块生成向量嵌入并存入向量数据库。
 */
public interface DocumentChunker {

    /**
     * 将原始文本切分为多个块。
     *
     * @param text 原始长文本
     * @return 切分后的块列表，按在原文中的出现顺序排列
     */
    List<Chunk> chunk(String text);

    /**
     * 文本块记录。
     *
     * @param index      块在原文中的序号（从 0 开始）
     * @param text       块的文本内容
     * @param tokenCount 块包含的 token 数量（用于控制块大小）
     */
    record Chunk(int index, String text, int tokenCount) {}
}
