package com.socialflow.service.note;

import com.socialflow.model.entity.NoteTag;
import com.socialflow.model.vo.NoteTagVO;

import java.util.Collection;
import java.util.List;

public interface NoteTagService {

    List<NoteTagVO> listAll(Long userId);

    /**
     * 按名字数组 upsert：已存在直接复用，新名字插入
     *
     * @return 顺序与入参一致的 NoteTag 列表（用于建关联表）
     */
    List<NoteTag> resolveOrCreate(Long userId, Collection<String> names);

    void rename(Long userId, Long id, String newName);

    void delete(Long userId, Long id);
}
