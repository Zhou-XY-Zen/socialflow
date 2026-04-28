package com.socialflow.service.note.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialflow.dao.mapper.NoteImportItemMapper;
import com.socialflow.dao.mapper.NoteImportTaskMapper;
import com.socialflow.dao.mapper.NoteMapper;
import com.socialflow.model.entity.NoteImportItem;
import com.socialflow.model.entity.NoteImportTask;
import com.socialflow.service.note.importer.NoteDedupService;
import com.socialflow.service.note.parser.NoteParser;
import com.socialflow.service.note.parser.NoteParserRegistry;
import com.socialflow.service.note.parser.ParsedNote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 异步导入流水线 —— 从 tempDir 读字节，逐 item 处理：
 *   parse → dedup（L1/L2）→ 写 item 状态
 *
 * SSE 事件：stage / item-done / task-done / error
 *
 * 整个 run() 包在 try/catch/finally —— 任意异常都不会让 task 卡在 running，
 * finally 强制 sse.complete + 清理临时目录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteImportPipeline {

    private final ObjectMapper objectMapper;

    private final NoteParserRegistry parserRegistry;
    private final NoteDedupService dedupService;
    private final NoteMapper noteMapper;
    private final NoteImportTaskMapper taskMapper;
    private final NoteImportItemMapper itemMapper;
    private final NoteImportSseRegistry sse;

    @Async
    public void run(Long taskId, Map<Long, Path> itemPaths, Path tempDir) {
        NoteImportTask task = taskMapper.selectById(taskId);
        if (task == null) { cleanupDir(tempDir); return; }
        int processed = 0, failed = 0;
        try {
            task.setStatus("running");
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
            sse.push(taskId, "stage", Map.of("taskId", taskId, "stage", "running"));

            for (Map.Entry<Long, Path> e : itemPaths.entrySet()) {
                Long itemId = e.getKey();
                Path filePath = e.getValue();
                NoteImportItem item = itemMapper.selectById(itemId);
                if (item == null) continue;
                try {
                    processOne(task, item, filePath);
                    processed++;
                } catch (Exception ex) {
                    log.warn("pipeline item {} failed: {}", itemId, ex.getMessage());
                    failed++;
                    item.setParseStatus("failed");
                    item.setErrorMsg(ex.getMessage());
                    item.setUpdateTime(LocalDateTime.now());
                    itemMapper.updateById(item);
                    sse.push(taskId, "error", Map.of("itemId", itemId, "msg", String.valueOf(ex.getMessage())));
                }
                task.setProcessedFiles(processed);
                task.setFailedFiles(failed);
                taskMapper.updateById(task);
            }

            task.setStatus("reviewing");
            task.setProcessedFiles(processed);
            task.setFailedFiles(failed);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
            sse.push(taskId, "task-done", Map.of("taskId", taskId, "processed", processed, "failed", failed));
        } catch (Exception fatal) {
            log.error("pipeline run task {} fatal", taskId, fatal);
            try {
                task.setStatus("failed");
                task.setFailedFiles(failed);
                task.setProcessedFiles(processed);
                task.setErrorMsg("pipeline fatal: " + fatal.getMessage());
                task.setFinishedAt(LocalDateTime.now());
                taskMapper.updateById(task);
                sse.push(taskId, "error", Map.of("msg", "pipeline fatal: " + fatal.getMessage()));
            } catch (Exception ee) { /* swallow secondary */ }
        } finally {
            sse.complete(taskId);
            cleanupDir(tempDir);
        }
    }

    private void processOne(NoteImportTask task, NoteImportItem item, Path filePath) throws Exception {
        Long taskId = task.getId();
        Long itemId = item.getId();
        sse.push(taskId, "stage", Map.of("itemId", itemId, "stage", "parsing", "fileName", item.getFileName()));

        byte[] bytes = Files.readAllBytes(filePath);

        // L1 dedup
        String fileHash = dedupService.fileHash(bytes);
        item.setFileHash(fileHash);
        Long l1Hit = dedupService.findExistingByFileHash(item.getUserId(), fileHash);
        if (l1Hit != null) {
            item.setConflictWithNoteId(l1Hit);
            item.setParseStatus("skipped");
            item.setEnrichStatus("skipped");
            item.setResolution("skip");
            item.setUpdateTime(LocalDateTime.now());
            itemMapper.updateById(item);
            sse.push(taskId, "item-done", Map.of("itemId", itemId, "conflictWithNoteId", l1Hit,
                    "reason", "duplicate file"));
            return;
        }

        // parse
        item.setParseStatus("running");
        itemMapper.updateById(item);
        NoteParser parser = parserRegistry.pick(item.getFileName());
        ParsedNote parsed;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            parsed = parser.parse(item.getFileName(), in);
        }
        item.setParsedTitle(parsed.getTitle());
        item.setParsedMd(parsed.getContentMd());
        item.setParseStatus("done");

        // L2 dedup
        String contentHash = dedupService.contentHash(parsed.getContentMd());
        item.setContentHash(contentHash);
        Long l2Hit = dedupService.findExistingByContentHash(item.getUserId(), contentHash);
        if (l2Hit != null) {
            item.setConflictWithNoteId(l2Hit);
            item.setResolution("pending");
        }
        sse.push(taskId, "stage", Map.of("itemId", itemId, "stage", "parsed", "title", parsed.getTitle()));

        item.setEnrichStatus("skipped");

        if (item.getResolution() == null || item.getResolution().equals("pending")) {
            if (item.getConflictWithNoteId() == null) item.setResolution("create");
        }
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(item);

        sse.push(taskId, "item-done", Map.of(
                "itemId", itemId,
                "parsedTitle", parsed.getTitle(),
                "conflictWithNoteId", item.getConflictWithNoteId() == null ? 0 : item.getConflictWithNoteId(),
                "enrichStatus", item.getEnrichStatus()
        ));
    }

    /** 递归删临时目录；失败仅记录不抛 */
    private void cleanupDir(Path dir) {
        if (dir == null) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            log.debug("cleanup tempDir {} failed: {}", dir, e.getMessage());
        }
    }
}
