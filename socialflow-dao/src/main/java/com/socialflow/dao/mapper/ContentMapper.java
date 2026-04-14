package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.Content;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容 Mapper —— 操作数据库表 `content`
 *
 * 该接口负责对"内容"表进行数据库操作。
 * 内容（Content）是 SocialFlow 平台的核心业务实体，代表用户创作的
 * 一篇文章、一条动态、一段视频脚本等。每条内容记录包含标题、正文、
 * 状态（草稿/待审核/已发布）、所属用户等信息。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条内容记录
 *     - deleteById(id)            根据主键删除内容
 *     - updateById(entity)        根据主键更新内容（如修改标题、正文）
 *     - selectById(id)            根据主键查询单条内容
 *     - selectList(wrapper)       按条件查询内容列表（如按状态筛选）
 *     - selectPage(page, wrapper) 分页查询内容列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要全文搜索内容或者统计内容数量，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface ContentMapper extends BaseMapper<Content> {
}
