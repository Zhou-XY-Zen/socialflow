package com.socialflow.service.codeanalysis.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.dao.mapper.RuleLibraryItemMapper;
import com.socialflow.model.entity.RuleLibraryItem;
import com.socialflow.model.vo.RuleLibraryItemVO;
import com.socialflow.service.codeanalysis.RuleLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleLibraryServiceImpl implements RuleLibraryService {

    private final RuleLibraryItemMapper mapper;

    @Override
    public List<RuleLibraryItemVO> list(String topCategory, String level, String keyword, Boolean enabledOnly) {
        LambdaQueryWrapper<RuleLibraryItem> q = new LambdaQueryWrapper<>();
        if (topCategory != null && !topCategory.isBlank()) q.eq(RuleLibraryItem::getTopCategory, topCategory);
        if (level != null && !level.isBlank()) q.eq(RuleLibraryItem::getLevel, level);
        if (Boolean.TRUE.equals(enabledOnly)) q.eq(RuleLibraryItem::getEnabled, 1);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(RuleLibraryItem::getCode, keyword)
                    .or().like(RuleLibraryItem::getTitle, keyword)
                    .or().like(RuleLibraryItem::getBody, keyword));
        }
        // 按 code 自然排序：1.1.1 / 1.1.2 / 1.2.1 / 2.1.1 ... 用字符串排序通常即可
        q.orderByAsc(RuleLibraryItem::getTopCategory)
         .orderByAsc(RuleLibraryItem::getCode);
        List<RuleLibraryItem> rows = mapper.selectList(q);
        List<RuleLibraryItemVO> vos = new ArrayList<>(rows.size());
        for (RuleLibraryItem r : rows) {
            RuleLibraryItemVO vo = new RuleLibraryItemVO();
            BeanUtils.copyProperties(r, vo);
            vo.setId(r.getId() == null ? null : String.valueOf(r.getId()));
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public void toggleEnabled(Long id, Integer enabled) {
        RuleLibraryItem exist = mapper.selectById(id);
        if (exist == null) throw new BusinessException("规约不存在: " + id);
        RuleLibraryItem patch = new RuleLibraryItem();
        patch.setId(id);
        patch.setEnabled(enabled == null || enabled == 0 ? 0 : 1);
        mapper.updateById(patch);
    }

    @Override
    public RuleLibraryItemVO save(RuleLibraryItem entity) {
        if (entity.getCode() == null || entity.getCode().isBlank()) {
            throw new BusinessException("规约编号不能为空");
        }
        if (entity.getId() == null) {
            // 新增：标为用户自定义
            entity.setIsCustom(1);
            entity.setSource("user-custom");
            if (entity.getEnabled() == null) entity.setEnabled(1);
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        RuleLibraryItem fresh = mapper.selectById(entity.getId());
        RuleLibraryItemVO vo = new RuleLibraryItemVO();
        BeanUtils.copyProperties(fresh, vo);
        vo.setId(String.valueOf(fresh.getId()));
        return vo;
    }

    @Override
    public void deleteCustom(Long id) {
        RuleLibraryItem exist = mapper.selectById(id);
        if (exist == null) throw new BusinessException("规约不存在: " + id);
        if (exist.getIsCustom() == null || exist.getIsCustom() != 1) {
            throw new BusinessException("黄山版内置规约禁止删除（可禁用）");
        }
        mapper.deleteById(id); // 走 @TableLogic 软删
    }

    /**
     * 启动时调用：从 classpath JSON upsert 进表。
     *   - 表里没有 code → INSERT
     *   - 表里已有 code → 仅更新内容字段（title/body/...），**保留 enabled / is_custom 不动**
     *
     * 这样设计：用户禁用过的规约升级后仍是禁用状态，不会被 upsert 重置。
     */
    @Override
    public int upsertFromJsonResource(String classpathJson) {
        try (InputStream in = new ClassPathResource(classpathJson).getInputStream()) {
            JsonNode root = JsonUtil.mapper().readTree(in);
            JsonNode arr = root.path("rules");
            if (!arr.isArray()) {
                log.warn("[RuleLibrary] {} 格式异常，rules 字段不是数组", classpathJson);
                return 0;
            }
            String source = root.path("version").asText("huangshan-?")
                          + "@" + root.path("publishDate").asText("?");
            // 已有 code → id 映射
            List<RuleLibraryItem> existing = mapper.selectList(null);
            Map<String, RuleLibraryItem> byCode = new HashMap<>(existing.size() * 2);
            for (RuleLibraryItem e : existing) byCode.put(e.getCode(), e);

            int inserted = 0, updated = 0;
            for (JsonNode r : arr) {
                String code = r.path("code").asText("");
                if (code.isBlank()) continue;
                RuleLibraryItem prev = byCode.get(code);
                if (prev == null) {
                    RuleLibraryItem n = new RuleLibraryItem();
                    n.setCode(code);
                    n.setTopCategory(r.path("topCategory").asText(""));
                    n.setSubCategory(emptyToNull(r.path("subCategory").asText("")));
                    n.setLevel(r.path("level").asText("REFERENCE"));
                    n.setTitle(r.path("title").asText(""));
                    n.setBody(emptyToNull(r.path("body").asText("")));
                    n.setDescription(emptyToNull(r.path("description").asText("")));
                    n.setExampleGood(emptyToNull(r.path("exampleGood").asText("")));
                    n.setExampleBad(emptyToNull(r.path("exampleBad").asText("")));
                    n.setEnabled(1);
                    n.setIsCustom(0);
                    n.setSource(source);
                    mapper.insert(n);
                    inserted++;
                } else {
                    // 更新内容字段，保留 enabled / is_custom
                    RuleLibraryItem patch = new RuleLibraryItem();
                    patch.setId(prev.getId());
                    patch.setTopCategory(r.path("topCategory").asText(prev.getTopCategory()));
                    patch.setSubCategory(emptyToNull(r.path("subCategory").asText("")));
                    patch.setLevel(r.path("level").asText(prev.getLevel()));
                    patch.setTitle(r.path("title").asText(prev.getTitle()));
                    patch.setBody(emptyToNull(r.path("body").asText("")));
                    patch.setDescription(emptyToNull(r.path("description").asText("")));
                    patch.setExampleGood(emptyToNull(r.path("exampleGood").asText("")));
                    patch.setExampleBad(emptyToNull(r.path("exampleBad").asText("")));
                    patch.setSource(source);
                    mapper.updateById(patch);
                    updated++;
                }
            }
            log.info("[RuleLibrary] upsert 完成: inserted={}, updated={}, source={}", inserted, updated, source);
            return inserted + updated;
        } catch (Exception e) {
            log.error("[RuleLibrary] upsert 失败: {}", classpathJson, e);
            return 0;
        }
    }

    @Override
    public List<RuleLibraryItem> listAllEnabled() {
        return mapper.selectList(new LambdaQueryWrapper<RuleLibraryItem>()
                .eq(RuleLibraryItem::getEnabled, 1));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
