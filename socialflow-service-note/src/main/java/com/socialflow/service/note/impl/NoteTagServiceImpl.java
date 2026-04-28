package com.socialflow.service.note.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.exception.NotFoundException;
import com.socialflow.dao.mapper.NoteTagMapper;
import com.socialflow.dao.mapper.NoteTagRelMapper;
import com.socialflow.model.entity.NoteTag;
import com.socialflow.model.entity.NoteTagRel;
import com.socialflow.model.vo.NoteTagVO;
import com.socialflow.service.note.NoteTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteTagServiceImpl implements NoteTagService {

    private final NoteTagMapper tagMapper;
    private final NoteTagRelMapper tagRelMapper;

    @Override
    public List<NoteTagVO> listAll(Long userId) {
        return tagMapper.selectList(
                new LambdaQueryWrapper<NoteTag>()
                        .eq(NoteTag::getUserId, userId)
                        .orderByDesc(NoteTag::getUsageCount)
                        .orderByAsc(NoteTag::getName))
                .stream().map(t -> {
                    NoteTagVO v = new NoteTagVO();
                    v.setId(t.getId());
                    v.setName(t.getName());
                    v.setUsageCount(t.getUsageCount());
                    return v;
                }).toList();
    }

    @Override
    @Transactional
    public List<NoteTag> resolveOrCreate(Long userId, Collection<String> names) {
        if (CollectionUtils.isEmpty(names)) return List.of();

        // 去空 + 去重 + 保序
        LinkedHashMap<String, Boolean> uniq = new LinkedHashMap<>();
        for (String n : names) {
            if (StringUtils.hasText(n)) uniq.put(n.trim(), true);
        }
        if (uniq.isEmpty()) return List.of();

        List<NoteTag> existing = tagMapper.selectList(
                new LambdaQueryWrapper<NoteTag>()
                        .eq(NoteTag::getUserId, userId)
                        .in(NoteTag::getName, uniq.keySet()));
        Map<String, NoteTag> byName = existing.stream()
                .collect(Collectors.toMap(NoteTag::getName, t -> t));

        List<NoteTag> result = new ArrayList<>(uniq.size());
        for (String name : uniq.keySet()) {
            NoteTag tag = byName.get(name);
            if (tag == null) {
                tag = new NoteTag();
                tag.setUserId(userId);
                tag.setName(name);
                tag.setUsageCount(0);
                tagMapper.insert(tag);
            }
            result.add(tag);
        }
        return result;
    }

    @Override
    public void rename(Long userId, Long id, String newName) {
        NoteTag t = loadOwnedOrThrow(userId, id);
        if (!StringUtils.hasText(newName)) throw new BusinessException("标签名不能为空");
        t.setName(newName.trim());
        tagMapper.updateById(t);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        loadOwnedOrThrow(userId, id);
        tagRelMapper.delete(new LambdaQueryWrapper<NoteTagRel>().eq(NoteTagRel::getTagId, id));
        tagMapper.deleteById(id);
    }

    private NoteTag loadOwnedOrThrow(Long userId, Long id) {
        NoteTag t = tagMapper.selectById(id);
        if (t == null) throw new NotFoundException("标签不存在");
        if (!t.getUserId().equals(userId)) throw new BusinessException("无权操作他人标签");
        return t;
    }
}
