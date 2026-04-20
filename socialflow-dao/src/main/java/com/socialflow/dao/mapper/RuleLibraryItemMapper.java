package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.RuleLibraryItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 规约库 Mapper。基础 CRUD 走 MyBatis-Plus，无需写 XML。
 */
@Mapper
public interface RuleLibraryItemMapper extends BaseMapper<RuleLibraryItem> {
}
