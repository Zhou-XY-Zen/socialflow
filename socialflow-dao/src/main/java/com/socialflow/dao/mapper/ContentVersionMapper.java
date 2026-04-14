package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.ContentVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容版本 Mapper —— 操作数据库表 `content_version`
 *
 * 该接口负责对"内容版本"表进行数据库操作。
 * 每次对内容进行修改时，系统会保存一个新的版本快照到此表中，
 * 从而实现内容的版本历史管理。用户可以查看历史版本、回滚到之前的版本。
 * 每条版本记录关联一个 content_id，并存储该版本的完整正文内容。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条版本记录（保存新版本）
 *     - deleteById(id)            根据主键删除某个版本
 *     - updateById(entity)        根据主键更新版本信息
 *     - selectById(id)            根据主键查询单个版本
 *     - selectList(wrapper)       按条件查询版本列表（如查某篇内容的所有版本）
 *     - selectPage(page, wrapper) 分页查询版本列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要查询某篇内容的最新版本号，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface ContentVersionMapper extends BaseMapper<ContentVersion> {
}
