package com.socialflow.service.note;

import com.socialflow.common.result.PageResult;
import com.socialflow.model.dto.NoteCreateDTO;
import com.socialflow.model.dto.NoteQueryDTO;
import com.socialflow.model.dto.NoteUpdateDTO;
import com.socialflow.model.vo.NoteVO;

/**
 * 笔记服务 —— 知识中枢的核心 CRUD。
 *
 * 列表与详情的差异：列表不返回 contentMd 节省带宽；详情才返回。
 * 软删与硬删：trash() 进回收站（status=3）；remove() 真正物理删除（仅回收站内可调）。
 */
public interface NoteService {

    PageResult<NoteVO> list(Long userId, NoteQueryDTO query);

    NoteVO get(Long userId, Long id);

    NoteVO create(Long userId, NoteCreateDTO dto);

    NoteVO update(Long userId, Long id, NoteUpdateDTO dto);

    /** 软删：移入回收站 */
    void trash(Long userId, Long id);

    /** 从回收站恢复 */
    void restore(Long userId, Long id);

    /** 硬删：仅在回收站内允许 */
    void remove(Long userId, Long id);

    /** 切换置顶 */
    void togglePin(Long userId, Long id);

    /** 切换公开（公开博客） */
    void togglePublic(Long userId, Long id);
}
