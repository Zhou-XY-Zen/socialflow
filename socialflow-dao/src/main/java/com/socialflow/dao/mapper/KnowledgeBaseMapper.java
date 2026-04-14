package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 Mapper —— 操作数据库表 `knowledge_base`
 *
 * 该接口负责对"知识库"表进行数据库操作。
 * 知识库（Knowledge Base）是 RAG（检索增强生成）功能的顶层容器。
 * 用户可以创建多个知识库，每个知识库有自己的名称、描述、向量模型配置等。
 * 知识库下面包含多个知识文档（KnowledgeDocument），
 * 文档再被切分为多个知识分块（KnowledgeChunk）用于检索。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条知识库记录（创建新知识库）
 *     - deleteById(id)            根据主键删除知识库
 *     - updateById(entity)        根据主键更新知识库信息（如修改名称或配置）
 *     - selectById(id)            根据主键查询单个知识库
 *     - selectList(wrapper)       按条件查询知识库列表
 *     - selectPage(page, wrapper) 分页查询知识库列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要统计每个知识库下的文档数或分块数，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
