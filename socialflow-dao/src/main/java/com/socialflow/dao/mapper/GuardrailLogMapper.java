package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.GuardrailLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 护栏日志 Mapper —— 操作数据库表 `guardrail_log`
 *
 * 该接口负责对"护栏日志"表进行数据库操作。
 * 护栏（Guardrail）是 AI 安全机制的一部分，用于在内容生成前后
 * 进行安全检查（如敏感词过滤、合规性校验、内容质量检测等）。
 * 每次护栏检查的结果（通过/拦截/警告）都会记录在此表中，
 * 包括检查类型、触发规则、原始内容摘要等信息，便于审计和追溯。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条护栏日志（记录一次安全检查结果）
 *     - deleteById(id)            根据主键删除日志
 *     - updateById(entity)        根据主键更新日志信息
 *     - selectById(id)            根据主键查询单条日志
 *     - selectList(wrapper)       按条件查询日志列表（如查所有被拦截的记录）
 *     - selectPage(page, wrapper) 分页查询日志列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要按拦截类型统计数量或查询拦截率，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface GuardrailLogMapper extends BaseMapper<GuardrailLog> {
}
