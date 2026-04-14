package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper —— 操作数据库表 `sys_user`
 *
 * 该接口负责对"系统用户"表进行数据库操作。
 * 系统用户是整个 SocialFlow 平台的核心实体，存储了用户的登录信息、
 * 角色权限、头像、昵称等基本资料。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条用户记录
 *     - deleteById(id)            根据主键删除用户
 *     - updateById(entity)        根据主键更新用户信息
 *     - selectById(id)            根据主键查询单个用户
 *     - selectList(wrapper)       按条件查询用户列表
 *     - selectPage(page, wrapper) 分页查询用户列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要复杂查询（例如：根据角色查用户、模糊搜索用户名），
 *   可以在此接口中添加自定义方法并配合 @Select 注解或 XML 映射文件使用。
 *
 * @author SocialFlow
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
