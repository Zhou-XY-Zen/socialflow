package com.socialflow.service.note.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.dao.mapper.NoteLinkMapper;
import com.socialflow.dao.mapper.NoteMapper;
import com.socialflow.model.entity.Note;
import com.socialflow.model.entity.NoteLink;
import com.socialflow.model.vo.NoteLinkVO;
import com.socialflow.service.note.NoteLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteLinkServiceImpl implements NoteLinkService {

    /** Obsidian 风格 [[Title]] 或 [[Title|Alias]] */
    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\]\\|]+)(?:\\|[^\\]]+)?]]");

    private final NoteLinkMapper linkMapper;
    private final NoteMapper noteMapper;

    @Override
    @Transactional
    public void rebuildExplicitLinks(Long userId, Long noteId, String contentMd) {
        // 删除该 note 现有的 explicit 边
        linkMapper.delete(new LambdaQueryWrapper<NoteLink>()
                .eq(NoteLink::getSrcNoteId, noteId)
                .eq(NoteLink::getLinkType, "explicit"));

        if (contentMd == null || contentMd.isEmpty()) return;
        Matcher m = WIKI_LINK.matcher(contentMd);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            String t = m.group(1).trim();
            if (!t.isEmpty()) seen.add(t);
        }
        if (seen.isEmpty()) return;

        // 按标题查同用户笔记
        List<Note> matches = noteMapper.selectList(
                new LambdaQueryWrapper<Note>()
                        .select(Note::getId, Note::getTitle)
                        .eq(Note::getUserId, userId)
                        .in(Note::getTitle, seen));
        Map<String, Long> titleToId = new LinkedHashMap<>();
        for (Note n : matches) titleToId.put(n.getTitle(), n.getId());

        LocalDateTime now = LocalDateTime.now();
        for (String title : seen) {
            Long dst = titleToId.get(title);
            if (dst == null || dst.equals(noteId)) continue;
            NoteLink link = new NoteLink();
            link.setUserId(userId);
            link.setSrcNoteId(noteId);
            link.setDstNoteId(dst);
            link.setLinkType("explicit");
            link.setCreateTime(now);
            try {
                linkMapper.insert(link);
            } catch (Exception e) {
                // 唯一索引冲突忽略
                log.debug("insert link skip dup: {} → {}", noteId, dst);
            }
        }
    }

    @Override
    public List<NoteLinkVO> findBacklinks(Long userId, Long noteId) {
        List<NoteLink> links = linkMapper.selectList(
                new LambdaQueryWrapper<NoteLink>()
                        .eq(NoteLink::getUserId, userId)
                        .eq(NoteLink::getDstNoteId, noteId));
        return enrichLinks(links);
    }

    @Override
    public List<NoteLinkVO> findForwardLinks(Long userId, Long noteId) {
        List<NoteLink> links = linkMapper.selectList(
                new LambdaQueryWrapper<NoteLink>()
                        .eq(NoteLink::getUserId, userId)
                        .eq(NoteLink::getSrcNoteId, noteId));
        return enrichLinks(links);
    }

    @Override
    public List<NoteLinkVO> findAllForUser(Long userId) {
        List<NoteLink> links = linkMapper.selectList(
                new LambdaQueryWrapper<NoteLink>().eq(NoteLink::getUserId, userId));
        return enrichLinks(links);
    }

    /** 批量回填两端标题 */
    private List<NoteLinkVO> enrichLinks(List<NoteLink> links) {
        if (links.isEmpty()) return List.of();
        Set<Long> ids = new HashSet<>();
        for (NoteLink l : links) { ids.add(l.getSrcNoteId()); ids.add(l.getDstNoteId()); }
        Map<Long, String> titles = new java.util.HashMap<>();
        for (Note n : noteMapper.selectBatchIds(ids)) titles.put(n.getId(), n.getTitle());
        return links.stream().map(l -> {
            NoteLinkVO v = new NoteLinkVO();
            v.setSrcNoteId(l.getSrcNoteId());
            v.setSrcTitle(titles.get(l.getSrcNoteId()));
            v.setDstNoteId(l.getDstNoteId());
            v.setDstTitle(titles.get(l.getDstNoteId()));
            v.setLinkType(l.getLinkType());
            v.setSimilarity(l.getSimilarity());
            return v;
        }).toList();
    }

    @Override
    public void deleteAllForNote(Long noteId) {
        linkMapper.delete(new LambdaQueryWrapper<NoteLink>()
                .eq(NoteLink::getSrcNoteId, noteId));
        linkMapper.delete(new LambdaQueryWrapper<NoteLink>()
                .eq(NoteLink::getDstNoteId, noteId));
    }
}
