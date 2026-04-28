package com.socialflow.service.note.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.exception.NotFoundException;
import com.socialflow.dao.mapper.NoteCategoryMapper;
import com.socialflow.dao.mapper.NoteMapper;
import com.socialflow.model.dto.NoteCategoryUpsertDTO;
import com.socialflow.model.entity.Note;
import com.socialflow.model.entity.NoteCategory;
import com.socialflow.model.vo.NoteCategoryVO;
import com.socialflow.service.note.NoteCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteCategoryServiceImpl implements NoteCategoryService {

    private final NoteCategoryMapper categoryMapper;
    private final NoteMapper noteMapper;

    @Override
    public List<NoteCategoryVO> tree(Long userId) {
        List<NoteCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<NoteCategory>()
                        .eq(NoteCategory::getUserId, userId)
                        .orderByAsc(NoteCategory::getSortOrder));

        // 一次查所有笔记的 category_id 计数
        List<Note> notes = noteMapper.selectList(
                new LambdaQueryWrapper<Note>()
                        .select(Note::getCategoryId)
                        .eq(Note::getUserId, userId)
                        .ne(Note::getStatus, 3));
        Map<Long, Long> countByCat = notes.stream()
                .filter(n -> n.getCategoryId() != null)
                .collect(Collectors.groupingBy(Note::getCategoryId, Collectors.counting()));

        // 转 VO + 计数
        Map<Long, NoteCategoryVO> map = all.stream().map(c -> {
            NoteCategoryVO v = new NoteCategoryVO();
            v.setId(c.getId());
            v.setParentId(c.getParentId());
            v.setName(c.getName());
            v.setSortOrder(c.getSortOrder());
            v.setColor(c.getColor());
            v.setNoteCount(countByCat.getOrDefault(c.getId(), 0L).intValue());
            v.setChildren(new ArrayList<>());
            return v;
        }).collect(Collectors.toMap(NoteCategoryVO::getId, v -> v));

        // 组装树
        List<NoteCategoryVO> roots = new ArrayList<>();
        for (NoteCategoryVO v : map.values()) {
            if (v.getParentId() == null) {
                roots.add(v);
            } else {
                NoteCategoryVO parent = map.get(v.getParentId());
                if (parent != null) parent.getChildren().add(v);
                else roots.add(v); // parent 缺失就当根节点
            }
        }
        roots.sort(Comparator.comparing(v -> v.getSortOrder() == null ? 0 : v.getSortOrder()));
        return roots;
    }

    @Override
    public NoteCategoryVO create(Long userId, NoteCategoryUpsertDTO dto) {
        NoteCategory c = new NoteCategory();
        c.setUserId(userId);
        c.setParentId(dto.getParentId());
        c.setName(dto.getName());
        c.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        c.setColor(dto.getColor());
        categoryMapper.insert(c);
        NoteCategoryVO v = new NoteCategoryVO();
        v.setId(c.getId());
        v.setParentId(c.getParentId());
        v.setName(c.getName());
        v.setSortOrder(c.getSortOrder());
        v.setColor(c.getColor());
        v.setNoteCount(0);
        return v;
    }

    @Override
    @Transactional
    public NoteCategoryVO update(Long userId, Long id, NoteCategoryUpsertDTO dto) {
        NoteCategory c = loadOwnedOrThrow(userId, id);
        c.setName(dto.getName());
        c.setParentId(dto.getParentId());
        if (dto.getSortOrder() != null) c.setSortOrder(dto.getSortOrder());
        if (dto.getColor() != null) c.setColor(dto.getColor());
        categoryMapper.updateById(c);
        NoteCategoryVO v = new NoteCategoryVO();
        v.setId(c.getId());
        v.setParentId(c.getParentId());
        v.setName(c.getName());
        v.setSortOrder(c.getSortOrder());
        v.setColor(c.getColor());
        return v;
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        loadOwnedOrThrow(userId, id);
        // 关联笔记的 category_id 置 NULL（不连带删笔记）
        noteMapper.update(null,
                new LambdaUpdateWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .eq(Note::getCategoryId, id)
                        .set(Note::getCategoryId, null));
        categoryMapper.deleteById(id);
    }

    private NoteCategory loadOwnedOrThrow(Long userId, Long id) {
        NoteCategory c = categoryMapper.selectById(id);
        if (c == null) throw new NotFoundException("分类不存在");
        if (!c.getUserId().equals(userId)) throw new BusinessException("无权操作他人分类");
        return c;
    }
}
