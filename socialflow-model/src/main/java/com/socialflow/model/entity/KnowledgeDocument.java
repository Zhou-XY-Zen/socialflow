package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档实体类 —— 对应数据库表 `knowledge_document`
 *
 * 【作用】记录用户上传到知识库中的每一个文档文件的元信息和解析状态。
 *   文档上传后会经历"解析 → 切片 → 向量化"的处理流水线，本表记录了整个流程的状态。
 *
 * 【为什么需要它】
 *   知识库的内容来源于用户上传的文档。系统需要跟踪每个文档的解析进度和结果，
 *   在解析失败时给用户提供错误信息，解析成功后记录产生的切片数量等统计信息。
 *
 * 【关联关系】
 *   - knowledge_document.kb_id → knowledge_base.id （所属知识库）
 *   - knowledge_chunk.doc_id → knowledge_document.id （文档的切片）
 *
 * 【使用场景】
 *   - 用户在知识库详情页查看已上传的文档列表及其解析状态
 *   - 上传文档后后台异步解析，更新 parseStatus
 *   - 解析失败时 parseError 记录失败原因，用户可查看后重新上传
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_document")
public class KnowledgeDocument extends BaseEntity {

    /**
     * 所属知识库 ID
     *
     * 关联 knowledge_base.id，标识该文档属于哪个知识库。
     */
    private Long kbId;

    /**
     * 文件名称
     *
     * 上传时的原始文件名。
     * 示例："产品使用手册.pdf"
     */
    private String fileName;

    /**
     * 文件存储 URL
     *
     * 文件在对象存储服务中的访问地址。
     */
    private String fileUrl;

    /**
     * 文件类型
     *
     * 文档的后缀名/格式类型。
     * 可选值："pdf"、"docx"、"txt"、"md"、"html" 等。
     */
    private String fileType;

    /**
     * 文件大小（字节）
     *
     * 文档文件的大小，单位为字节。
     */
    private Long fileSize;

    /**
     * 文档字符数
     *
     * 文档解析后提取出的纯文本字符总数。
     * 用于评估文档的内容量。
     */
    private Integer charCount;

    /**
     * 切片数量
     *
     * 该文档被切分后产生的文本切片数量。
     * 切片策略通常按固定 Token 数或按段落切分。
     */
    private Integer chunkCount;

    /**
     * 解析状态
     *
     * 标识文档的处理进度。
     * 可选值："PENDING"（等待解析）、"PARSING"（解析中）、
     *         "SUCCESS"（解析成功）、"FAILED"（解析失败）
     */
    private String parseStatus;

    /**
     * 解析错误信息
     *
     * 当 parseStatus 为 FAILED 时，记录失败的错误原因。
     * 示例："不支持的文件格式"、"文件内容为空"、"PDF 解析异常"
     * 解析成功时该字段为空。
     */
    private String parseError;
}
