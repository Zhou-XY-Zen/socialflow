package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库实体类 —— 对应数据库表 `knowledge_base`
 *
 * 【作用】存储用户创建的知识库基本信息。
 *   知识库是 RAG（检索增强生成）功能的基础。用户可以上传文档（PDF、Word 等）到知识库，
 *   系统会将文档切片、向量化后存入向量数据库。生成文案时，AI 可以从知识库中检索相关内容，
 *   从而生成更准确、更贴合实际的文案。
 *
 * 【为什么需要它】
 *   大模型的知识有截止日期，且不了解用户的产品信息。通过知识库，用户可以将自己的
 *   产品资料、品牌故事、行业报告等文档导入系统，让 AI 在生成文案时参考这些专属知识，
 *   大幅提升文案的准确性和专业性。
 *
 * 【关联关系】
 *   - knowledge_base.user_id → sys_user.id （所属用户）
 *   - knowledge_document.kb_id → knowledge_base.id （知识库下的文档）
 *   - knowledge_chunk.kb_id → knowledge_base.id （知识库下的文本切片）
 *   - content.kb_id → knowledge_base.id （使用该知识库生成的文案）
 *
 * 【使用场景】
 *   - 用户在"知识库管理"页面创建/查看/删除知识库
 *   - 向知识库中上传文档
 *   - 生成文案时选择一个知识库进行 RAG 增强
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_base")
public class KnowledgeBase extends BaseEntity {

    /**
     * 所属用户 ID
     *
     * 关联 sys_user.id，标识该知识库属于哪个用户。
     */
    private Long userId;

    /**
     * 知识库名称
     *
     * 用户为知识库起的名称，便于识别和管理。
     * 示例："护肤产品资料库"、"品牌故事素材"
     */
    private String name;

    /**
     * 知识库描述
     *
     * 对该知识库的简要说明，描述其包含的内容类型和用途。
     * 示例："包含所有护肤产品的成分说明、使用方法和用户评价"
     */
    private String description;

    /**
     * 知识库分类
     *
     * 按业务场景对知识库分类，帮助用户快速筛选。
     * 示例："产品资料"、"行业报告"、"品牌素材"
     */
    private String category;

    /**
     * 文档数量
     *
     * 该知识库中包含的文档总数。每上传一个文档加 1，删除时减 1。
     * 用于在知识库列表中展示统计信息。
     */
    private Integer docCount;

    /**
     * 切片数量
     *
     * 该知识库中所有文档被切分后产生的文本切片总数。
     * 切片数量决定了向量数据库中的存储量和检索范围。
     */
    private Integer chunkCount;

    /**
     * 向量化模型名称
     *
     * 用于将文本切片转换为向量的 Embedding 模型。
     * 示例："text-embedding-ada-002"、"bge-large-zh"
     * 知识库创建后该字段通常不应更改，否则需要重新向量化所有切片。
     */
    private String embeddingModel;

    /**
     * 向量维度
     *
     * Embedding 模型输出的向量维度。不同模型维度不同。
     * 示例：1536（OpenAI ada-002）、1024（bge-large-zh）
     * 必须与向量数据库中创建的集合维度一致。
     */
    private Integer embeddingDim;

    /**
     * 知识库状态
     *
     * 可选值：0 = 已禁用（暂时不可用于检索），1 = 正常（可正常使用）。
     */
    private Integer status;
}
