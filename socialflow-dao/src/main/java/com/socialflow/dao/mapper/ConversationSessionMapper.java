package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.ConversationSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper —— 操作数据库表 `conversation_session`
 *
 * 该接口负责对"对话会话"表进行数据库操作。
 * 对话会话记录了用户与 AI 助手之间的交互会话。每个会话有唯一的 session ID，
 * 包含会话标题、创建时间、最后活跃时间、所属用户等信息。
 * 一个会话下包含多轮对话消息（用户提问 + AI 回答），
 * 用于实现多轮对话的上下文保持和历史记录回溯。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条对话会话记录（开启新会话）
 *     - deleteById(id)            根据主键删除会话
 *     - updateById(entity)        根据主键更新会话信息（如更新最后活跃时间）
 *     - selectById(id)            根据主键查询单个会话
 *     - selectList(wrapper)       按条件查询会话列表（如查某用户的所有会话）
 *     - selectPage(page, wrapper) 分页查询会话列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要查询用户最近的活跃会话或清理过期会话，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface ConversationSessionMapper extends BaseMapper<ConversationSession> {
}
