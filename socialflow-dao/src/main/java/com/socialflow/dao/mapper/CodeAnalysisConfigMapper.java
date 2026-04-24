package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.CodeAnalysisConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码分析用户级 LLM 配置 Mapper —— 操作 `code_analysis_config` 表。
 * 基础 CRUD 由 BaseMapper 提供；按 userId 查询在 Service 层用 LambdaQueryWrapper。
 */
@Mapper
public interface CodeAnalysisConfigMapper extends BaseMapper<CodeAnalysisConfig> {
}
