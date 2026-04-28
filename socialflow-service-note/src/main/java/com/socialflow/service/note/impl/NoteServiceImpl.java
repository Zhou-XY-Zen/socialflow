package com.socialflow.service.note.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.exception.NotFoundException;
import com.socialflow.common.result.PageResult;
import com.socialflow.dao.mapper.NoteCategoryMapper;
import com.socialflow.dao.mapper.NoteMapper;
import com.socialflow.dao.mapper.NoteVersionMapper;
import com.socialflow.model.dto.NoteCreateDTO;
import com.socialflow.model.dto.NoteQueryDTO;
import com.socialflow.model.dto.NoteUpdateDTO;
import com.socialflow.model.entity.Note;
import com.socialflow.model.entity.NoteCategory;
import com.socialflow.model.entity.NoteVersion;
import com.socialflow.model.vo.NoteVO;
import com.socialflow.service.note.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 笔记服务实现
 *
 * 关于权限：所有方法都强制按 user_id 过滤，且修改/删除前先 load + 校验，
 * 避免越权篡改他人笔记。
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteMapper noteMapper;
    private final NoteCategoryMapper categoryMapper;
    private final NoteVersionMapper versionMapper;

    private static final int STATUS_NORMAL  = 1;
    private static final int STATUS_TRASHED = 3;

    @Override
    public PageResult<NoteVO> list(Long userId, NoteQueryDTO query) {
        long pageNum = query.getPageNum() == null ? 1L : query.getPageNum();
        long pageSize = query.getPageSize() == null ? 20L : query.getPageSize();
        int statusFilter = query.getStatus() == null ? STATUS_NORMAL : query.getStatus();

        LambdaQueryWrapper<Note> w = new LambdaQueryWrapper<>();
        w.eq(Note::getUserId, userId)
         .eq(Note::getStatus, statusFilter);

        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            w.and(x -> x.like(Note::getTitle, kw)
                       .or().like(Note::getContentMd, kw)
                       .or().like(Note::getSummary, kw));
        }
        if (query.getCategoryId() != null) {
            w.eq(Note::getCategoryId, query.getCategoryId());
        } else if (Boolean.TRUE.equals(query.getUncategorizedOnly())) {
            w.isNull(Note::getCategoryId);
        }

        // 列表不返回 contentMd（节省带宽）
        w.select(Note::getId, Note::getUserId, Note::getTitle, Note::getSummary,
                 Note::getCategoryId, Note::getWordCount,
                 Note::getIsPinned, Note::getIsPublic, Note::getSlug, Note::getStatus,
                 Note::getSourceType, Note::getPublishedAt,
                 Note::getCreateTime, Note::getUpdateTime);

        // 默认置顶优先 + 更新时间倒序
        String sortBy = query.getSortBy() == null ? "pinned-first" : query.getSortBy();
        switch (sortBy) {
            case "created" -> w.orderByDesc(Note::getCreateTime);
            case "updated" -> w.orderByDesc(Note::getUpdateTime);
            default -> w.orderByDesc(Note::getIsPinned).orderByDesc(Note::getUpdateTime);
        }

        Page<Note> page = new Page<>(pageNum, pageSize);
        Page<Note> result = noteMapper.selectPage(page, w);

        List<NoteVO> records = result.getRecords().stream()
                .map(n -> toVO(n, false))
                .toList();

        if (!records.isEmpty()) {
            attachCategoryNames(records, result.getRecords());
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public NoteVO get(Long userId, Long id) {
        Note n = loadOwnedOrThrow(userId, id);
        NoteVO vo = toVO(n, true);
        attachCategoryNames(List.of(vo), List.of(n));
        return vo;
    }

    @Override
    @Transactional
    public NoteVO create(Long userId, NoteCreateDTO dto) {
        Note n = new Note();
        n.setUserId(userId);
        n.setTitle(dto.getTitle());
        n.setContentMd(dto.getContentMd());
        n.setSummary(dto.getSummary());
        n.setCategoryId(dto.getCategoryId());
        n.setIsPinned(dto.getIsPinned() == null ? 0 : dto.getIsPinned());
        n.setIsPublic(dto.getIsPublic() == null ? 0 : dto.getIsPublic());
        n.setStatus(dto.getStatus() == null ? STATUS_NORMAL : dto.getStatus());
        n.setWordCount(countWords(dto.getContentMd()));
        n.setSourceType("manual");
        noteMapper.insert(n);
        return get(userId, n.getId());
    }

    @Override
    @Transactional
    public NoteVO update(Long userId, Long id, NoteUpdateDTO dto) {
        Note n = loadOwnedOrThrow(userId, id);
        String oldTitle = n.getTitle();
        String oldContent = n.getContentMd();

        if (StringUtils.hasText(dto.getTitle())) n.setTitle(dto.getTitle());
        if (dto.getContentMd() != null && !dto.getContentMd().equals(oldContent)) {
            // 内容真变了 → 先快照旧版
            snapshotVersion(n.getId(), oldTitle, oldContent, "edited");
            n.setContentMd(dto.getContentMd());
            n.setWordCount(countWords(dto.getContentMd()));
        }
        if (dto.getSummary() != null)   n.setSummary(dto.getSummary());
        if (dto.getCategoryId() != null) n.setCategoryId(dto.getCategoryId());
        if (dto.getIsPinned() != null)  n.setIsPinned(dto.getIsPinned());
        if (dto.getIsPublic() != null) {
            // 首次公开记一个 published_at
            if (dto.getIsPublic() == 1 && (n.getIsPublic() == null || n.getIsPublic() == 0)) {
                n.setPublishedAt(LocalDateTime.now());
            }
            n.setIsPublic(dto.getIsPublic());
        }
        if (dto.getStatus() != null)    n.setStatus(dto.getStatus());
        noteMapper.updateById(n);
        return get(userId, id);
    }

    @Override
    public void trash(Long userId, Long id) {
        Note n = loadOwnedOrThrow(userId, id);
        n.setStatus(STATUS_TRASHED);
        noteMapper.updateById(n);
    }

    @Override
    public void restore(Long userId, Long id) {
        Note n = loadOwnedOrThrow(userId, id);
        if (n.getStatus() != STATUS_TRASHED) {
            throw new BusinessException("仅回收站内的笔记可恢复");
        }
        n.setStatus(STATUS_NORMAL);
        noteMapper.updateById(n);
    }

    @Override
    @Transactional
    public void remove(Long userId, Long id) {
        Note n = loadOwnedOrThrow(userId, id);
        if (n.getStatus() != STATUS_TRASHED) {
            throw new BusinessException("仅回收站内的笔记可彻底删除");
        }
        noteMapper.deleteById(id);
    }

    /** 把指定版本的内容存入 note_version 表 */
    private void snapshotVersion(Long noteId, String title, String content, String reason) {
        if (content == null) return;
        Integer max = versionMapper.findMaxVersion(noteId);
        NoteVersion v = new NoteVersion();
        v.setNoteId(noteId);
        v.setVersion((max == null ? 0 : max) + 1);
        v.setTitle(title);
        v.setContentMd(content);
        v.setChangeSummary(reason);
        v.setCreateTime(LocalDateTime.now());
        versionMapper.insert(v);
    }

    @Override
    public void togglePin(Long userId, Long id) {
        Note n = loadOwnedOrThrow(userId, id);
        n.setIsPinned(n.getIsPinned() == 1 ? 0 : 1);
        noteMapper.updateById(n);
    }

    @Override
    public void togglePublic(Long userId, Long id) {
        Note n = loadOwnedOrThrow(userId, id);
        int next = n.getIsPublic() == 1 ? 0 : 1;
        n.setIsPublic(next);
        if (next == 1 && n.getPublishedAt() == null) {
            n.setPublishedAt(LocalDateTime.now());
        }
        noteMapper.updateById(n);
    }

    // ============== helpers ==============

    private Note loadOwnedOrThrow(Long userId, Long id) {
        Note n = noteMapper.selectById(id);
        if (n == null) {
            throw new NotFoundException("笔记不存在");
        }
        if (!n.getUserId().equals(userId)) {
            throw new BusinessException("无权访问他人笔记");
        }
        return n;
    }

    private NoteVO toVO(Note n, boolean withContent) {
        NoteVO v = new NoteVO();
        v.setId(n.getId());
        v.setTitle(n.getTitle());
        v.setSummary(n.getSummary());
        if (withContent) {
            v.setContentMd(n.getContentMd());
        }
        v.setCategoryId(n.getCategoryId());
        v.setWordCount(n.getWordCount());
        v.setIsPinned(n.getIsPinned());
        v.setIsPublic(n.getIsPublic());
        v.setSlug(n.getSlug());
        v.setStatus(n.getStatus());
        v.setSourceType(n.getSourceType());
        v.setPublishedAt(n.getPublishedAt());
        v.setCreateTime(n.getCreateTime());
        v.setUpdateTime(n.getUpdateTime());
        return v;
    }

    private void attachCategoryNames(List<NoteVO> vos, List<Note> notes) {
        List<Long> catIds = notes.stream()
                .map(Note::getCategoryId).filter(java.util.Objects::nonNull).distinct().toList();
        if (catIds.isEmpty()) return;
        Map<Long, String> map = new HashMap<>(categoryMapper.selectBatchIds(catIds).stream()
                .collect(Collectors.toMap(NoteCategory::getId, NoteCategory::getName)));
        for (NoteVO v : vos) {
            if (v.getCategoryId() != null) v.setCategoryName(map.get(v.getCategoryId()));
        }
    }

    /** 简单字数统计：去掉 Markdown 标记后按 char 计 */
    private int countWords(String md) {
        if (!StringUtils.hasText(md)) return 0;
        String stripped = md.replaceAll("```[\\s\\S]*?```", "")
                            .replaceAll("`[^`]*`", "")
                            .replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                            .replaceAll("\\[[^]]*]\\([^)]*\\)", "")
                            .replaceAll("[#*_>~`\\-]+", "")
                            .replaceAll("\\s+", "");
        return stripped.length();
    }
}
