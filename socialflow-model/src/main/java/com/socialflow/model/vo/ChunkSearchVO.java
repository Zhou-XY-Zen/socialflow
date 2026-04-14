package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库切片搜索结果视图对象（VO）—— 用于向前端返回知识库检索的结果
 *
 * 【作用】封装在知识库中进行语义检索时返回的单条切片结果。
 *   包含切片的完整文本内容、来源信息和相似度分数，
 *   方便用户查看检索到了哪些相关内容。
 *
 * 【与 RagSourceVO 的区别】
 *   - RagSourceVO 是嵌套在 ContentVO 中的引用摘要，只包含简短的 snippet
 *   - ChunkSearchVO 是独立的检索结果，包含完整的切片文本（contentText）
 *
 * 【对应 API 接口（作为返回值）】
 *   POST /api/kb/{kbId}/search  —— 知识库语义检索，返回 List<ChunkSearchVO>
 *
 * 【使用场景】
 *   用户在知识库详情页手动搜索时，展示检索结果列表。
 *   每条结果展示切片文本、来源文档、页码和匹配分数。
 */
@Data
public class ChunkSearchVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识切片 ID
     *
     * 切片的唯一标识，对应 knowledge_chunk.id。
     */
    private Long chunkId;

    /**
     * 来源文档 ID
     *
     * 该切片所属文档的 ID，对应 knowledge_document.id。
     */
    private Long docId;

    /**
     * 所属知识库 ID
     *
     * 该切片所属知识库的 ID，对应 knowledge_base.id。
     */
    private Long kbId;

    /**
     * 切片在文档中的序号
     *
     * 该切片在所属文档内的顺序编号，从 0 开始。
     * 帮助用户了解该段内容在文档中的位置。
     */
    private Integer chunkIndex;

    /**
     * 切片的完整文本内容
     *
     * 切片的原始纯文本，即从文档中截取出的一段完整文字。
     * 用户可以阅读此内容来判断检索结果是否有用。
     */
    private String contentText;

    /**
     * 来源文档名称
     *
     * 文档的原始文件名，方便用户识别来源。
     * 示例："产品使用手册.pdf"
     */
    private String docName;

    /**
     * 所在页码
     *
     * 该切片在原文档中的页码。可为空（如 txt 文件没有页码概念）。
     */
    private Integer page;

    /**
     * 相似度分数
     *
     * 该切片与搜索查询之间的语义相似度得分，范围通常为 0.0 ~ 1.0。
     * 分数越高表示越相关，结果按分数从高到低排列。
     */
    private Double score;
}
