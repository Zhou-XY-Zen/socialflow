package com.socialflow.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库检索请求 DTO —— 用于在知识库中搜索相关内容的接口入参
 *
 * 【作用】封装用户或系统在知识库中进行语义检索时需要的参数。
 *   系统会根据查询文本在向量数据库中搜索最相关的知识切片（Chunk），
 *   返回匹配度最高的结果。
 *
 * 【对应 API 接口】
 *   POST /api/kb/{kbId}/search  —— 知识库语义检索
 *
 * 【使用场景】
 *   1. RAG 生成文案时，系统内部自动调用知识库检索，获取相关上下文
 *   2. 用户在知识库详情页手动搜索，查看某个问题能检索到哪些内容
 */
@Data
public class KbSearchDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 检索查询文本（必填）
     *
     * 要搜索的问题或关键信息。系统会将该文本转为向量，
     * 在知识库中搜索语义最相似的切片。
     * 示例："春季护肤有哪些注意事项？"
     */
    @NotBlank
    private String query;

    /**
     * 返回结果数量（选填）
     *
     * 指定返回前 K 条最相似的结果。默认值 5。
     * 值越大返回的结果越多，但可能包含相关性较低的内容。
     */
    private Integer topK = 5;

    /**
     * 是否启用重排序（Rerank）（选填）
     *
     * 默认 true。启用后，系统会用 Rerank 模型对初步检索结果进行二次排序，
     * 提高结果的精准度。关闭可以加快检索速度但精度会下降。
     */
    private Boolean enableRerank = true;

    /**
     * 是否启用混合检索（Hybrid Search）（选填）
     *
     * 默认 true。启用后，同时使用向量相似度检索和关键词检索，
     * 取两者结果的并集，从而兼顾语义匹配和精确匹配。
     * 关闭时仅使用向量相似度检索。
     */
    private Boolean enableHybrid = true;
}
