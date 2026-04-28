package com.socialflow.service.note.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.exception.NotFoundException;
import com.socialflow.common.result.PageResult;
import com.socialflow.dao.mapper.NoteCategoryMapper;
import com.socialflow.dao.mapper.NoteMapper;
import com.socialflow.dao.mapper.NoteTagMapper;
import com.socialflow.dao.mapper.NoteTagRelMapper;
import com.socialflow.dao.mapper.NoteVersionMapper;
import com.socialflow.model.dto.NoteCreateDTO;
import com.socialflow.model.dto.NoteQueryDTO;
import com.socialflow.model.dto.NoteUpdateDTO;
import com.socialflow.model.entity.Note;
import com.socialflow.model.entity.NoteCategory;
import com.socialflow.model.entity.NoteTag;
import com.socialflow.model.entity.NoteTagRel;
import com.socialflow.model.entity.NoteVersion;
import com.socialflow.model.vo.NoteVO;
import com.socialflow.service.note.NoteLinkService;
import com.socialflow.service.note.NoteService;
import com.socialflow.service.note.NoteTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    private final NoteTagMapper tagMapper;
    private final NoteTagRelMapper tagRelMapper;
    private final NoteVersionMapper versionMapper;
    private final NoteTagService tagService;
    private final NoteLinkService linkService;

    private static final int STATUS_NORMAL  = 1;
    private static final int STATUS_DRAFT   = 2;
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
        }
        // tagIds 过滤前置到 SQL：先查 note_tag_rel 拿 noteIds，再 IN 限制，
        // 这样分页/总数都准确。空交集 → 直接返回空页。
        if (!CollectionUtils.isEmpty(query.getTagIds())) {
            List<Long> noteIds = tagRelMapper.selectList(
                    new LambdaQueryWrapper<NoteTagRel>().in(NoteTagRel::getTagId, query.getTagIds())
            ).stream().map(NoteTagRel::getNoteId).distinct().toList();
            if (noteIds.isEmpty()) return PageResult.empty(pageNum, pageSize);
            w.in(Note::getId, noteIds);
        }

        // 列表不返回 contentMd（节省带宽）
        w.select(Note::getId, Note::getUserId, Note::getTitle, Note::getSummary,
                 Note::getCategoryId, Note::getWordCount, Note::getReadScore,
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

        // 标签 + 分类名批量回填，避免 N+1
        if (!records.isEmpty()) {
            attachTagsBatch(records, result.getRecords());
            attachCategoryNames(records, result.getRecords());
        }

        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public NoteVO get(Long userId, Long id) {
        Note n = loadOwnedOrThrow(userId, id);
        NoteVO vo = toVO(n, true);
        attachTagsBatch(List.of(vo), List.of(n));
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

        replaceTags(userId, n.getId(), dto.getTags());
        linkService.rebuildExplicitLinks(userId, n.getId(), n.getContentMd());
        return get(userId, n.getId());
    }

    @Override
    @Transactional
    public NoteVO update(Long userId, Long id, NoteUpdateDTO dto) {
        Note n = loadOwnedOrThrow(userId, id);
        boolean contentChanged = false;
        String oldTitle = n.getTitle();
        String oldContent = n.getContentMd();

        if (StringUtils.hasText(dto.getTitle())) n.setTitle(dto.getTitle());
        if (dto.getContentMd() != null && !dto.getContentMd().equals(oldContent)) {
            // 内容真变了 → 先快照旧版
            snapshotVersion(n.getId(), oldTitle, oldContent, "edited");
            n.setContentMd(dto.getContentMd());
            n.setWordCount(countWords(dto.getContentMd()));
            contentChanged = true;
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

        if (dto.getTags() != null) {
            replaceTags(userId, id, dto.getTags());
        }
        if (contentChanged) {
            linkService.rebuildExplicitLinks(userId, id, n.getContentMd());
        }
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
        // 清关联表
        tagRelMapper.delete(new LambdaQueryWrapper<NoteTagRel>().eq(NoteTagRel::getNoteId, id));
        linkService.deleteAllForNote(id);
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
            v.setAiOutline(n.getAiOutline());
        }
        v.setCategoryId(n.getCategoryId());
        v.setWordCount(n.getWordCount());
        v.setReadScore(n.getReadScore());
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

    /** 替换某笔记的标签关联（先全删再 upsert + 关联） */
    private void replaceTags(Long userId, Long noteId, List<String> tagNames) {
        tagRelMapper.delete(new LambdaQueryWrapper<NoteTagRel>().eq(NoteTagRel::getNoteId, noteId));
        if (CollectionUtils.isEmpty(tagNames)) return;

        List<NoteTag> tags = tagService.resolveOrCreate(userId, tagNames);
        LocalDateTime now = LocalDateTime.now();
        for (NoteTag t : tags) {
            NoteTagRel rel = new NoteTagRel();
            rel.setNoteId(noteId);
            rel.setTagId(t.getId());
            rel.setCreateTime(now);
            tagRelMapper.insert(rel);
            tagMapper.incrementUsage(t.getId(), 1);
        }
    }

    /** 给一批 VO 批量挂上 tags（避免 N+1） */
    private void attachTagsBatch(List<NoteVO> vos, List<Note> notes) {
        if (vos.isEmpty()) return;
        List<Long> noteIds = notes.stream().map(Note::getId).toList();
        List<NoteTagRel> rels = tagRelMapper.selectList(
                new LambdaQueryWrapper<NoteTagRel>().in(NoteTagRel::getNoteId, noteIds));
        if (rels.isEmpty()) {
            vos.forEach(v -> v.setTags(Collections.emptyList()));
            return;
        }
        List<Long> tagIds = rels.stream().map(NoteTagRel::getTagId).distinct().toList();
        Map<Long, String> tagIdToName = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(NoteTag::getId, NoteTag::getName));
        Map<Long, List<String>> noteIdToTags = new HashMap<>();
        for (NoteTagRel r : rels) {
            String name = tagIdToName.get(r.getTagId());
            if (name == null) continue;
            noteIdToTags.computeIfAbsent(r.getNoteId(), k -> new ArrayList<>()).add(name);
        }
        for (NoteVO v : vos) {
            v.setTags(noteIdToTags.getOrDefault(v.getId(), Collections.emptyList()));
        }
    }

    private void attachCategoryNames(List<NoteVO> vos, List<Note> notes) {
        List<Long> catIds = notes.stream()
                .map(Note::getCategoryId).filter(java.util.Objects::nonNull).distinct().toList();
        if (catIds.isEmpty()) return;
        Map<Long, String> map = categoryMapper.selectBatchIds(catIds).stream()
                .collect(Collectors.toMap(NoteCategory::getId, NoteCategory::getName));
        for (NoteVO v : vos) {
            if (v.getCategoryId() != null) v.setCategoryName(map.get(v.getCategoryId()));
        }
    }

    /** 简单字数统计：去掉 Markdown 标记后按 char 计 */
    private int countWords(String md) {
        if (!StringUtils.hasText(md)) return 0;
        String stripped = md.replaceAll("```[\\s\\S]*?```", "")    // 代码块
                            .replaceAll("`[^`]*`", "")              // 行内代码
                            .replaceAll("!\\[[^]]*]\\([^)]*\\)", "") // 图片
                            .replaceAll("\\[[^]]*]\\([^)]*\\)", "")  // 链接
                            .replaceAll("[#*_>~`\\-]+", "")          // 标记符号
                            .replaceAll("\\s+", "");
        return stripped.length();
    }

}
