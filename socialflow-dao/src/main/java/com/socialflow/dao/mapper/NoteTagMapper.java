package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.NoteTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NoteTagMapper extends BaseMapper<NoteTag> {

    /** 维护 usage_count，避免每次 group by 全表 */
    @Update("UPDATE note_tag SET usage_count = usage_count + #{delta}, " +
            "update_time = NOW() WHERE id = #{id}")
    int incrementUsage(@Param("id") Long id, @Param("delta") int delta);
}
