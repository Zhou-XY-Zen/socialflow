package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库信息视图对象（VO）—— 用于向前端返回知识库的详细信息
 *
 * 【作用】封装知识库的完整展示信息，包括名称、描述、统计数据和配置参数。
 *   是前端展示知识库列表和详情页时使用的数据结构。
 *
 * 【与 KnowledgeBase 实体的区别】
 *   KnowledgeBase 包含 userId 等内部字段；
 *   KbVO 是面向前端的视图对象，只包含需要展示的字段。
 *
 * 【对应 API 接口（作为返回值）】
 *   - GET /api/kb          —— 获取知识库列表，返回 List<KbVO>
 *   - GET /api/kb/{id}     —— 获取单个知识库详情，返回 KbVO
 *   - POST /api/kb         —— 创建知识库后返回 KbVO
 *
 * 【使用场景】
 *   - 知识库管理页面展示所有知识库的列表
 *   - 知识库详情页展示单个知识库的完整信息
 *   - 文案生成时选择知识库的下拉列表
 */
@Data
public class KbVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识库 ID
     *
     * 知识库的唯一标识。
     */
    private Long id;

    /**
     * 知识库名称
     *
     * 用户为知识库起的名称。
     * 示例："护肤产品资料库"
     */
    private String name;

    /**
     * 知识库描述
     *
     * 对知识库内容的简要说明。
     */
    private String description;

    /**
     * 知识库分类
     *
     * 知识库的业务分类。
     * 示例："产品资料"、"行业报告"
     */
    private String category;

    /**
     * 文档数量
     *
     * 该知识库中包含的文档总数。
     */
    private Integer docCount;

    /**
     * 切片数量
     *
     * 该知识库中所有文档切分后的文本切片总数。
     */
    private Integer chunkCount;

    /**
     * 向量化模型名称
     *
     * 该知识库使用的 Embedding 模型。
     * 示例："text-embedding-ada-002"、"bge-large-zh"
     */
    private String embeddingModel;

    /**
     * 向量维度
     *
     * Embedding 模型输出的向量维度。
     * 示例：1536、1024
     */
    private Integer embeddingDim;

    /**
     * 知识库状态
     *
     * 0 = 已禁用，1 = 正常。
     */
    private Integer status;

    /**
     * 创建时间
     *
     * 知识库的创建时间。
     */
    private LocalDateTime createTime;
}
