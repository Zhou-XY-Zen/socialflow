package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.ContentMediaRel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容-媒体关联 Mapper —— 操作数据库表 `content_media_rel`
 *
 * 该接口负责对"内容与媒体资源的关联关系"表进行数据库操作。
 * 这是一张典型的多对多关联表（中间表）：一篇内容可以包含多个媒体文件，
 * 一个媒体文件也可以被多篇内容引用。每条记录存储了 content_id 和
 * media_asset_id 的对应关系，以及排序序号等辅助字段。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条关联记录（给内容绑定媒体）
 *     - deleteById(id)            根据主键删除关联记录（解绑媒体）
 *     - updateById(entity)        根据主键更新关联信息（如修改排序序号）
 *     - selectById(id)            根据主键查询单条关联记录
 *     - selectList(wrapper)       按条件查询关联列表（如查某篇内容的所有媒体）
 *     - selectPage(page, wrapper) 分页查询关联列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要批量绑定/解绑媒体资源，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface ContentMediaRelMapper extends BaseMapper<ContentMediaRel> {
}
