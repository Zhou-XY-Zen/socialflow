package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.RepoAnalysis;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码分析主记录 Mapper —— 操作 `repo_analysis` 表
 *
 * 所有基础 CRUD 由 BaseMapper 提供。按用户查最近分析、按 Git URL + 类型
 * 查历史，均在 Service 层用 LambdaQueryWrapper 拼装。
 */
@Mapper
public interface RepoAnalysisMapper extends BaseMapper<RepoAnalysis> {
}
