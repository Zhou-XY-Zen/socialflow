package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.EvalTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测任务 Mapper —— 操作数据库表 `eval_task`
 *
 * 该接口负责对"评测任务"表进行数据库操作。
 * 评测任务（Eval Task）是对 AI 生成内容进行质量评估的任务。
 * 每个评测任务定义了评测的名称、评测标准、使用的评测模型、
 * 任务状态（待运行/运行中/已完成）等信息。
 * 评测任务执行后会产生多条评测结果（EvalResult）。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条评测任务（创建新评测）
 *     - deleteById(id)            根据主键删除评测任务
 *     - updateById(entity)        根据主键更新任务信息（如更新状态为已完成）
 *     - selectById(id)            根据主键查询单个评测任务
 *     - selectList(wrapper)       按条件查询评测任务列表
 *     - selectPage(page, wrapper) 分页查询评测任务列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要查询最近的评测任务或按评测类型筛选，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface EvalTaskMapper extends BaseMapper<EvalTask> {
}
