package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.UserPreference;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户偏好设置 Mapper —— 操作数据库表 `user_preference`
 *
 * 该接口负责对"用户偏好设置"表进行数据库操作。
 * 用户偏好记录了每个用户的个性化配置，例如默认使用的 AI 模型、
 * 偏好的内容风格（正式/轻松/幽默）、默认语言、界面主题等。
 * 系统在生成内容时会读取用户偏好来调整 AI 的行为参数。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条用户偏好记录
 *     - deleteById(id)            根据主键删除偏好记录
 *     - updateById(entity)        根据主键更新偏好设置（如切换默认模型）
 *     - selectById(id)            根据主键查询单条偏好记录
 *     - selectList(wrapper)       按条件查询偏好列表（如查某用户的所有偏好）
 *     - selectPage(page, wrapper) 分页查询偏好列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要按偏好类型批量查询或重置默认偏好，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {
}
