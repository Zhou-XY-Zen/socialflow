package com.socialflow.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.R;
import com.socialflow.model.dto.NoteImportItemUpdateDTO;
import com.socialflow.model.vo.NoteImportCommitVO;
import com.socialflow.model.vo.NoteImportTaskVO;
import com.socialflow.model.vo.NoteVO;
import com.socialflow.service.note.NoteImportService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 知识中枢 · 上传/导入流水线
 *
 * 路由前缀：/api/v1/notes/import
 *
 * P0 端点：单文件同步 importSingleFile
 * P1+ 端点：批量异步、URL 剪藏、SSE 进度推送（待补）
 */
@Tag(name = "notes-import")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/notes/import")
@RequiredArgsConstructor
public class NoteImportController {

    private final NoteImportService importService;

    @Operation(summary = "single file sync import (P0)")
    @PostMapping("/single")
    public R<NoteVO> importSingle(@RequestPart("file") MultipartFile file) {
        return R.ok(importService.importSingleFile(StpUtil.getLoginIdAsLong(), file));
    }

    @Operation(summary = "batch async import (with AI enrichment)")
    @RateLimiter(name = "ai-generate")
    @PostMapping("/batch")
    public R<Long> importBatch(@RequestPart("files") MultipartFile[] files,
                               @RequestParam(defaultValue = "true") boolean enrichEnabled) {
        return R.ok(importService.importBatchAsync(StpUtil.getLoginIdAsLong(), files, enrichEnabled));
    }

    @Operation(summary = "subscribe SSE stream for a running task")
    @GetMapping(value = "/tasks/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long taskId) {
        return importService.subscribe(StpUtil.getLoginIdAsLong(), taskId);
    }

    @Operation(summary = "list recent import tasks")
    @GetMapping("/tasks")
    public R<List<NoteImportTaskVO>> listTasks() {
        return R.ok(importService.listRecentTasks(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "get import task detail")
    @GetMapping("/tasks/{taskId}")
    public R<NoteImportTaskVO> getTask(@PathVariable Long taskId) {
        return R.ok(importService.getTask(StpUtil.getLoginIdAsLong(), taskId));
    }

    @Operation(summary = "update review-page item (title / tags / category / resolution)")
    @PutMapping("/tasks/{taskId}/items/{itemId}")
    public R<Void> updateItem(@PathVariable Long taskId,
                               @PathVariable Long itemId,
                               @RequestBody NoteImportItemUpdateDTO dto) {
        importService.updateItem(StpUtil.getLoginIdAsLong(), taskId, itemId, dto);
        return R.ok();
    }

    @Operation(summary = "commit task (apply resolutions); returns per-resolution stats")
    @PostMapping("/tasks/{taskId}/commit")
    public R<NoteImportCommitVO> commit(@PathVariable Long taskId) {
        return R.ok(importService.commit(StpUtil.getLoginIdAsLong(), taskId));
    }

    @Operation(summary = "cancel task")
    @PostMapping("/tasks/{taskId}/cancel")
    public R<Void> cancel(@PathVariable Long taskId) {
        importService.cancel(StpUtil.getLoginIdAsLong(), taskId);
        return R.ok();
    }
}
