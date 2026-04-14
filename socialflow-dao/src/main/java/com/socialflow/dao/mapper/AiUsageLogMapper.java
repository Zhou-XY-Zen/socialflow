package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.AiUsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * AI 使用日志 Mapper —— 操作数据库表 `ai_usage_log`
 *
 * 该接口负责对"AI 使用日志"表进行数据库操作。
 * 每次系统调用大语言模型（如 OpenAI、Claude）时，都会记录一条使用日志，
 * 包含调用的模型名称、输入 Token 数、输出 Token 数、总 Token 数（total_tokens）、
 * 调用耗时、所属用户等信息。用于用量统计、费用核算和配额管控。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条 AI 使用日志
 *     - deleteById(id)            根据主键删除日志
 *     - updateById(entity)        根据主键更新日志信息
 *     - selectById(id)            根据主键查询单条日志
 *     - selectList(wrapper)       按条件查询日志列表（如查某用户的所有调用记录）
 *     - selectPage(page, wrapper) 分页查询日志列表
 *
 * 【自定义方法】
 *   - sumTokensSince(): 统计某个用户从指定时间点至今的 Token 总用量，
 *     用于实现用量配额检查（例如：每月限额 100 万 Token）。
 *
 * @author SocialFlow
 */
@Mapper
public interface AiUsageLogMapper extends BaseMapper<AiUsageLog> {

    /**
     * 统计用户从指定时间到现在的 Token 总消耗量
     *
     * 典型使用场景：用户每月有 Token 配额限制，在每次调用 AI 前，
     * 先调用此方法查询本月已用量，判断是否超出限额。
     *
     * 【SQL 逻辑详解】
     *   1. SELECT COALESCE(SUM(total_tokens), 0)
     *      —— 对 total_tokens 字段求和，得到所有匹配记录的 Token 总量
     *         COALESCE(..., 0) 的作用：如果查询结果为 NULL（即没有匹配的记录），
     *         则返回 0 而不是 NULL。这样调用方不需要处理 NULL 的情况，
     *         直接拿到一个数字就能用。
     *
     *   2. FROM ai_usage_log
     *      —— 从 AI 使用日志表中查询
     *
     *   3. WHERE user_id = #{userId}
     *      —— 只统计指定用户的数据，不同用户的用量互不影响
     *
     *   4. AND create_time >= #{since}
     *      —— 只统计从 since 时间点之后的记录
     *         例如：传入本月 1 号 00:00:00，就能统计本月的总用量
     *         例如：传入今天 00:00:00，就能统计今日的总用量
     *
     *   5. AND is_deleted = 0
     *      —— 排除已被逻辑删除的记录（软删除机制，0 表示未删除）
     *
     * 【参数说明】
     * @param userId 用户 ID —— 指定要统计哪个用户的用量
     * @param since  起始时间 —— 从什么时间开始统计（包含该时间点）
     * @return 该用户从 since 到现在的 Token 总消耗量，如果没有记录则返回 0
     */
    @Select("""
            SELECT COALESCE(SUM(total_tokens), 0) FROM ai_usage_log
            WHERE user_id = #{userId} AND create_time >= #{since}
              AND is_deleted = 0
            """)
    long sumTokensSince(@Param("userId") Long userId,
                        @Param("since") LocalDateTime since);
}
