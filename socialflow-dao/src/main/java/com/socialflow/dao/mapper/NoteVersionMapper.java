package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.NoteVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NoteVersionMapper extends BaseMapper<NoteVersion> {

    @Select("SELECT COALESCE(MAX(version), 0) FROM note_version WHERE note_id = #{noteId}")
    Integer findMaxVersion(@Param("noteId") Long noteId);
}
