package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.RepoBookmark;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仓库书签 Mapper —— 操作 `repo_bookmark` 表。
 */
@Mapper
public interface RepoBookmarkMapper extends BaseMapper<RepoBookmark> {
}
