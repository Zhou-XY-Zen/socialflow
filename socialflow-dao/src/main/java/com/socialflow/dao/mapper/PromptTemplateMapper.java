package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板 Mapper —— 操作数据库表 `prompt_template`
 *
 * 该接口负责对"提示词模板"表进行数据库操作。
 * 提示词模板（Prompt Template）是 AI 内容生成的核心配置，
 * 定义了发送给大语言模型（LLM）的指令格式，包含模板名称、
 * 模板内容（支持变量占位符）、适用场景等字段。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条提示词模板
 *     - deleteById(id)            根据主键删除模板
 *     - updateById(entity)        根据主键更新模板内容
 *     - selectById(id)            根据主键查询单个模板
 *     - selectList(wrapper)       按条件查询模板列表（如按场景筛选）
 *     - selectPage(page, wrapper) 分页查询模板列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要按模板类型或关键词搜索模板，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
}
