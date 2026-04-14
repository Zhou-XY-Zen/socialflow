package com.socialflow.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.exception.NotFoundException;
import com.socialflow.common.result.R;
import com.socialflow.dao.mapper.PromptTemplateMapper;
import com.socialflow.model.entity.PromptTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Prompt 模板控制器 —— 模板的增删改查。
 */
@Tag(name = "template")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/template")
@RequiredArgsConstructor
public class TemplateController {

    private final PromptTemplateMapper templateMapper;

    @Operation(summary = "list templates")
    @GetMapping("/list")
    public R<List<PromptTemplate>> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String category) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isBlank()) {
            wrapper.eq(PromptTemplate::getPlatform, platform);
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(PromptTemplate::getCategory, category);
        }
        wrapper.orderByAsc(PromptTemplate::getSortOrder);
        return R.ok(templateMapper.selectList(wrapper));
    }

    @Operation(summary = "create template")
    @PostMapping
    public R<PromptTemplate> create(@RequestBody PromptTemplate tpl) {
        tpl.setUserId(StpUtil.getLoginIdAsLong());
        tpl.setIsSystem(0);
        templateMapper.insert(tpl);
        return R.ok(tpl);
    }

    @Operation(summary = "update template")
    @PutMapping("/{id}")
    public R<PromptTemplate> update(@PathVariable Long id, @RequestBody PromptTemplate tpl) {
        PromptTemplate existing = templateMapper.selectById(id);
        if (existing == null) throw new NotFoundException("template not found");
        existing.setTemplateName(tpl.getTemplateName());
        existing.setPlatform(tpl.getPlatform());
        existing.setCategory(tpl.getCategory());
        existing.setSystemPrompt(tpl.getSystemPrompt());
        existing.setUserPromptTemplate(tpl.getUserPromptTemplate());
        existing.setVariables(tpl.getVariables());
        existing.setOutputFormat(tpl.getOutputFormat());
        templateMapper.updateById(existing);
        return R.ok(existing);
    }

    @Operation(summary = "delete template")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateMapper.deleteById(id);
        return R.ok();
    }
}
