package com.socialflow.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.PageResult;
import com.socialflow.common.result.R;
import com.socialflow.model.dto.NoteCategoryUpsertDTO;
import com.socialflow.model.dto.NoteCreateDTO;
import com.socialflow.model.dto.NoteQueryDTO;
import com.socialflow.model.dto.NoteUpdateDTO;
import com.socialflow.model.vo.NoteCategoryVO;
import com.socialflow.model.vo.NoteVO;
import com.socialflow.service.note.NoteCategoryService;
import com.socialflow.service.note.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识中枢 · 笔记 + 分类
 *
 * 路由前缀：/api/v1/notes
 * 鉴权：所有端点要求 Sa-Token 登录态
 */
@Tag(name = "notes")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final NoteCategoryService categoryService;

    @Operation(summary = "list notes (paginated, with filters)")
    @PostMapping("/list")
    public R<PageResult<NoteVO>> list(@RequestBody NoteQueryDTO query) {
        return R.ok(noteService.list(StpUtil.getLoginIdAsLong(), query));
    }

    @Operation(summary = "get note detail")
    @GetMapping("/{id}")
    public R<NoteVO> get(@PathVariable Long id) {
        return R.ok(noteService.get(StpUtil.getLoginIdAsLong(), id));
    }

    @Operation(summary = "create note")
    @PostMapping
    public R<NoteVO> create(@Valid @RequestBody NoteCreateDTO dto) {
        return R.ok(noteService.create(StpUtil.getLoginIdAsLong(), dto));
    }

    @Operation(summary = "update note (partial)")
    @PutMapping("/{id}")
    public R<NoteVO> update(@PathVariable Long id, @RequestBody NoteUpdateDTO dto) {
        return R.ok(noteService.update(StpUtil.getLoginIdAsLong(), id, dto));
    }

    @Operation(summary = "trash note (soft delete)")
    @PostMapping("/{id}/trash")
    public R<Void> trash(@PathVariable Long id) {
        noteService.trash(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @Operation(summary = "restore note from trash")
    @PostMapping("/{id}/restore")
    public R<Void> restore(@PathVariable Long id) {
        noteService.restore(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @Operation(summary = "permanently delete note (trash only)")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        noteService.remove(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @Operation(summary = "toggle pin")
    @PostMapping("/{id}/pin")
    public R<Void> pin(@PathVariable Long id) {
        noteService.togglePin(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @Operation(summary = "toggle public")
    @PostMapping("/{id}/public")
    public R<Void> togglePublic(@PathVariable Long id) {
        noteService.togglePublic(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    // ==================== categories ====================

    @Operation(summary = "list categories as tree")
    @GetMapping("/categories")
    public R<List<NoteCategoryVO>> listCategories() {
        return R.ok(categoryService.tree(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "create category")
    @PostMapping("/categories")
    public R<NoteCategoryVO> createCategory(@Valid @RequestBody NoteCategoryUpsertDTO dto) {
        return R.ok(categoryService.create(StpUtil.getLoginIdAsLong(), dto));
    }

    @Operation(summary = "delete category (note.category_id will be set NULL)")
    @DeleteMapping("/categories/{id}")
    public R<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }
}
