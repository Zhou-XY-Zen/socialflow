package com.socialflow.service.note.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialflow.dao.mapper.NoteImportItemMapper;
import com.socialflow.dao.mapper.NoteImportTaskMapper;
import com.socialflow.dao.mapper.NoteMapper;
import com.socialflow.model.entity.NoteImportItem;
import com.socialflow.model.entity.NoteImportTask;
import com.socialflow.service.note.enrich.NoteEnrichResult;
import com.socialflow.service.note.enrich.NoteEnrichService;
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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 异步导入流水线 —— 从 tempDir 读字节，逐 item 处理
 *
 * SSE 事件：
 *   stage / item-done / task-done / error
 *
 * 关键保证：
 *   - 整个 run() 包在 try/catch/finally —— 任意异常都不会让 task 卡在 running
 *   - finally 强制 sse.complete + 清理临时目录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteImportPipeline {

    private static final ObjectMapper M = new ObjectMapper();

    private final NoteParserRegistry parserRegistry;
    private final NoteDedupService dedupService;
    private final NoteEnrichService enrichService;
    private final NoteMapper noteMapper;
    private final NoteImportTaskMapper taskMapper;
    private final NoteImportItemMapper itemMapper;
    private final NoteImportSseRegistry sse;

    /**
     * @param taskId         任务 id
     * @param itemPaths      item.id → 已落到磁盘的临时文件路径
     * @param tempDir        本次任务的临时根目录（结束时整体删掉）
     */
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

        // 一次性读出该文件字节（单文件，OOM 风险可控；如果担心超大文件可改 stream 解析）
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

        // enrich
        if (task.getEnrichEnabled() != null && task.getEnrichEnabled() == 1) {
            item.setEnrichStatus("running");
            itemMapper.updateById(item);
            sse.push(taskId, "stage", Map.of("itemId", itemId, "stage", "enriching"));
            NoteEnrichResult er = enrichService.enrich(item.getUserId(), parsed.getTitle(), parsed.getContentMd());
            Map<String, Object> payload = new HashMap<>();
            payload.put("summary", er.getSummary());
            payload.put("tags", er.getTags());
            payload.put("outline", er.getOutline());
            payload.put("categoryGuess", er.getCategoryGuess());
            payload.put("enriched", er.isEnriched());
            payload.put("failures", er.getFailures());
            payload.put("skippedReason", er.getSkippedReason());
            try { item.setAiPayload(M.writeValueAsString(payload)); }
            catch (Exception e) { item.setAiPayload(null); }
            item.setEnrichStatus(er.isEnriched() ? "done" : "skipped");
        } else {
            item.setEnrichStatus("skipped");
        }

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
