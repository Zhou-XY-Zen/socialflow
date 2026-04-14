package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.PublishTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发布任务 Mapper —— 操作数据库表 `publish_task`
 *
 * 该接口负责对"发布任务"表进行数据库操作。
 * 发布任务代表将某篇内容发布到指定社交媒体平台的一次操作。
 * 每条记录包含关联的内容 ID、目标平台账号 ID、计划发布时间、
 * 实际发布时间、任务状态（待发布/发布中/已完成/失败）等信息。
 * 支持定时发布和即时发布两种模式。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条发布任务（创建新的发布计划）
 *     - deleteById(id)            根据主键删除发布任务
 *     - updateById(entity)        根据主键更新任务状态（如标记为已完成）
 *     - selectById(id)            根据主键查询单个发布任务
 *     - selectList(wrapper)       按条件查询任务列表（如查所有待发布的任务）
 *     - selectPage(page, wrapper) 分页查询任务列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要统计各状态任务数量或查询即将到期的定时任务，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface PublishTaskMapper extends BaseMapper<PublishTask> {
}
