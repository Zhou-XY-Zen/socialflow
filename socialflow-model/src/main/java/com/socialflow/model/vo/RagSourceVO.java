package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * RAG 引用来源视图对象（VO）—— 用于向前端展示文案生成时引用的知识库来源
 *
 * 【作用】当文案通过 RAG（检索增强生成）模式生成时，AI 会参考知识库中的内容。
 *   本 VO 封装了每个被引用的知识切片的来源信息，
 *   让用户可以追溯"AI 的文案依据是什么"。
 *
 * 【对应 API 接口（作为返回值的一部分）】
 *   作为 ContentVO.ragSources 的列表元素返回，嵌套在以下接口中：
 *   - GET  /api/content/{id}     —— 获取文案详情时包含 RAG 来源
 *   - POST /api/content/generate —— 生成文案后返回 RAG 来源
 *
 * 【使用场景】
 *   前端在展示 AI 生成的文案时，如果使用了 RAG，会在文案下方展示
 *   "引用来源"列表，让用户了解 AI 引用了哪些文档的哪部分内容。
 */
@Data
public class RagSourceVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识切片 ID
     *
     * 被引用的知识切片的唯一标识，对应 knowledge_chunk.id。
     */
    private Long chunkId;

    /**
     * 来源文档 ID
     *
     * 该切片所属文档的 ID，对应 knowledge_document.id。
     */
    private Long docId;

    /**
     * 来源文档名称
     *
     * 文档的原始文件名，方便用户识别引用来源。
     * 示例："产品成分说明书.pdf"
     */
    private String docName;

    /**
     * 所在页码
     *
     * 该切片在原文档中的页码（如果文档有页码概念的话）。
     * 帮助用户快速定位到原文的位置。可为空。
     */
    private Integer page;

    /**
     * 引用片段摘要
     *
     * 被引用的切片文本的简短摘要或前几行内容，用于在前端快速预览。
     * 通常截取前 200 字左右。
     */
    private String snippet;

    /**
     * 相似度分数
     *
     * 该切片与用户查询之间的相似度得分，范围通常为 0.0 ~ 1.0。
     * 分数越高表示相关性越强。用于排序和展示匹配度。
     */
    private Double score;
}
