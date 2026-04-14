package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.UserApiKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 API 密钥 Mapper —— 操作数据库表 `user_api_key`
 *
 * 该接口负责对"用户 API 密钥"表进行数据库操作。
 * 每个用户可以拥有多个 API Key（例如 OpenAI Key、Claude Key 等），
 * 系统会把这些密钥加密后存储在此表中，在调用 AI 服务时取出使用。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条 API 密钥记录
 *     - deleteById(id)            根据主键删除密钥
 *     - updateById(entity)        根据主键更新密钥信息（如更换 key 值）
 *     - selectById(id)            根据主键查询单条密钥
 *     - selectList(wrapper)       按条件查询密钥列表（如查某个用户的所有 key）
 *     - selectPage(page, wrapper) 分页查询密钥列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要按平台类型批量查询密钥，可以在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface UserApiKeyMapper extends BaseMapper<UserApiKey> {
}
