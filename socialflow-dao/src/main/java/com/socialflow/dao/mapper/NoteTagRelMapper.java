package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.NoteTagRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NoteTagRelMapper extends BaseMapper<NoteTagRel> {

    @Select("SELECT tag_id FROM note_tag_rel WHERE note_id = #{noteId}")
    List<Long> findTagIdsByNote(@Param("noteId") Long noteId);
}
