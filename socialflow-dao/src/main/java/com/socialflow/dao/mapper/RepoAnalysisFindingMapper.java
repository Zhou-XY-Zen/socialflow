package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.RepoAnalysisFinding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码审查发现明细 Mapper —— 操作 `repo_analysis_finding` 表。
 */
@Mapper
public interface RepoAnalysisFindingMapper extends BaseMapper<RepoAnalysisFinding> {
}
