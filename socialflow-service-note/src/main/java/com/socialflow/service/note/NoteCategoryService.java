package com.socialflow.service.note;

import com.socialflow.model.dto.NoteCategoryUpsertDTO;
import com.socialflow.model.vo.NoteCategoryVO;

import java.util.List;

public interface NoteCategoryService {

    /** 返回树形结构（顶级 + children） */
    List<NoteCategoryVO> tree(Long userId);

    NoteCategoryVO create(Long userId, NoteCategoryUpsertDTO dto);

    NoteCategoryVO update(Long userId, Long id, NoteCategoryUpsertDTO dto);

    /** 删除分类：若仍关联笔记则把笔记的 category_id 置 NULL */
    void delete(Long userId, Long id);
}
