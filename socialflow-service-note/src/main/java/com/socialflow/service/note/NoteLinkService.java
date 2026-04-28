package com.socialflow.service.note;

import com.socialflow.model.entity.NoteLink;
import com.socialflow.model.vo.NoteLinkVO;

import java.util.List;

/**
 * 笔记图谱边维护
 *
 * 当前实现：解析 markdown 中的 [[标题]] 显式链接 → 写入 note_link (explicit)
 * P3 阶段补：embedding 余弦相似度 → 写入 note_link (semantic)
 */
public interface NoteLinkService {

    /** 笔记保存后回调：从 contentMd 抽 [[name]] → upsert explicit links */
    void rebuildExplicitLinks(Long userId, Long noteId, String contentMd);

    /** 反向链接：哪些笔记引用了 noteId（带标题） */
    List<NoteLinkVO> findBacklinks(Long userId, Long noteId);

    /** 正向链接：noteId 引用了哪些笔记（带标题） */
    List<NoteLinkVO> findForwardLinks(Long userId, Long noteId);

    /** 整张图：当前用户的所有 link，用于知识图谱视图 */
    List<NoteLinkVO> findAllForUser(Long userId);

    /** 删除某笔记相关的所有 link（硬删时调用） */
    void deleteAllForNote(Long noteId);
}
