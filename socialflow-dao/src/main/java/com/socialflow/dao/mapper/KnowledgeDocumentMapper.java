package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识文档 Mapper —— 操作数据库表 `knowledge_document`
 *
 * 该接口负责对"知识文档"表进行数据库操作。
 * 知识文档是用户上传到知识库中的原始文档（如 PDF、Word、TXT 等）。
 * 每条记录包含文档标题、来源、上传状态（处理中/已完成/失败）、
 * 所属知识库 ID 等信息。文档上传后会被自动切分为多个分块（Chunk），
 * 存储在 knowledge_chunk 表中用于向量检索。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条知识文档记录（上传新文档）
 *     - deleteById(id)            根据主键删除知识文档
 *     - updateById(entity)        根据主键更新文档信息（如更新处理状态）
 *     - selectById(id)            根据主键查询单个知识文档
 *     - selectList(wrapper)       按条件查询文档列表（如查某知识库下的所有文档）
 *     - selectPage(page, wrapper) 分页查询文档列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要按处理状态统计文档数量，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
