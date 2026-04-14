package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.EvalResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测结果 Mapper —— 操作数据库表 `eval_result`
 *
 * 该接口负责对"评测结果"表进行数据库操作。
 * 评测结果（Eval Result）是某个评测任务运行后产生的具体评分数据。
 * 每条记录对应一次评测打分，包含评测任务 ID、被评测的内容、
 * 各维度的评分（如流畅度、准确性、相关性等）、评测模型的输出等信息。
 * 通过分析评测结果，可以量化 AI 生成内容的质量。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条评测结果
 *     - deleteById(id)            根据主键删除评测结果
 *     - updateById(entity)        根据主键更新评测结果
 *     - selectById(id)            根据主键查询单条评测结果
 *     - selectList(wrapper)       按条件查询评测结果列表（如查某个任务的所有结果）
 *     - selectPage(page, wrapper) 分页查询评测结果列表
 *
 * 【自定义方法】
 *   当前没有自定义方法，所有数据库操作都由 BaseMapper 提供。
 *   如果以后需要按评测任务 ID 统计平均分或汇总评测报告，可在此添加自定义方法。
 *
 * @author SocialFlow
 */
@Mapper
public interface EvalResultMapper extends BaseMapper<EvalResult> {
}
