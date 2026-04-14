package com.socialflow.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库创建请求 DTO —— 用于创建新知识库的接口入参
 *
 * 【作用】封装用户创建新知识库时需要填写的参数。
 *   知识库创建后，用户可以继续向其中上传文档。
 *
 * 【对应 API 接口】
 *   POST /api/kb  —— 创建知识库
 *
 * 【使用场景】
 *   用户在"知识库管理"页面点击"新建知识库"，
 *   填写名称、描述等信息后提交创建。
 */
@Data
public class KbCreateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识库名称（必填）
     *
     * 用户为知识库起的名称，需要有辨识度。
     * 示例："护肤产品资料库"、"2024 年行业报告"
     */
    @NotBlank
    private String name;

    /**
     * 知识库描述（选填）
     *
     * 对知识库内容的简要说明。
     * 示例："包含所有护肤产品的成分说明和使用方法"
     */
    private String description;

    /**
     * 知识库分类（选填）
     *
     * 帮助用户对知识库进行分类管理。
     * 示例："产品资料"、"行业报告"、"品牌素材"
     */
    private String category;

    /**
     * 向量化模型名称（选填）
     *
     * 用于将文档切片转换为向量的 Embedding 模型。
     * 示例："text-embedding-ada-002"、"bge-large-zh"
     * 为空时使用系统默认的 Embedding 模型。
     * 注意：知识库创建后不建议更改此参数，否则需要重新向量化所有文档。
     */
    private String embeddingModel;

    /**
     * 向量维度（选填）
     *
     * 与 embeddingModel 配套的向量维度。
     * 示例：1536（OpenAI ada-002）、1024（bge-large-zh）
     * 为空时根据 embeddingModel 自动确定。
     */
    private Integer embeddingDim;
}
