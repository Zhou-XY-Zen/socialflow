package com.socialflow.service.note.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.exception.NotFoundException;
import com.socialflow.dao.mapper.NoteImportItemMapper;
import com.socialflow.dao.mapper.NoteImportTaskMapper;
import com.socialflow.dao.mapper.NoteMapper;
import com.socialflow.model.dto.NoteCreateDTO;
import com.socialflow.model.dto.NoteImportItemUpdateDTO;
import com.socialflow.model.dto.NoteUpdateDTO;
import com.socialflow.model.entity.Note;
import com.socialflow.model.entity.NoteImportItem;
import com.socialflow.model.entity.NoteImportTask;
import com.socialflow.model.vo.NoteImportItemVO;
import com.socialflow.model.vo.NoteImportTaskVO;
import com.socialflow.model.vo.NoteVO;
import com.socialflow.service.note.NoteImportService;
import com.socialflow.service.note.NoteService;
import com.socialflow.service.note.importer.ZipExtractor;
import com.socialflow.service.note.parser.NoteParser;
import com.socialflow.service.note.parser.NoteParserRegistry;
import com.socialflow.service.note.parser.ParsedNote;
import com.socialflow.service.note.pipeline.NoteImportPipeline;
import com.socialflow.service.note.pipeline.NoteImportSseRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteImportServiceImpl implements NoteImportService {

    private static final ObjectMapper M = new ObjectMapper();
    private static final Set<String> SUPPORTED_NESTED = Set.of(
            "md", "markdown", "mdown",
            "txt", "text", "log",
            "docx", "doc", "pdf", "rtf", "odt", "epub",
            "html", "htm", "ipynb"
    );

    private final NoteParserRegistry parserRegistry;
    private final NoteService noteService;
    private final NoteImportTaskMapper taskMapper;
    private final NoteImportItemMapper itemMapper;
    private final NoteMapper noteMapper;
    private final ZipExtractor zipExtractor;
    private final NoteImportPipeline pipeline;
    private final NoteImportSseRegistry sse;

    /* ============= P0：单文件同步入库 ============= */

    @Override
    @Transactional
    public NoteVO importSingleFile(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("文件为空");
        String fileName = file.getOriginalFilename();
        if (fileName == null) throw new BusinessException("文件名缺失");
        if (!parserRegistry.supports(fileName)) {
            throw new BusinessException("暂不支持该文件类型：" + fileName);
        }

        NoteImportTask task = newTask(userId, "file", fileName, 1, false);
        NoteImportItem item = newItem(task.getId(), userId, fileName, fileName, file.getSize());

        try {
            byte[] bytes = file.getBytes();
            // 二进制安全：直接 hash 字节，避免 new String() 在非 UTF-8 文件上丢数据
            item.setFileHash(DigestUtil.sha256Hex(bytes));

            NoteParser parser = parserRegistry.pick(fileName);
            ParsedNote parsed;
            try (InputStream in = file.getInputStream()) {
                parsed = parser.parse(fileName, in);
            }
            item.setParsedTitle(parsed.getTitle());
            item.setParseStatus("done");

            NoteCreateDTO dto = new NoteCreateDTO();
            dto.setTitle(parsed.getTitle());
            dto.setContentMd(parsed.getContentMd());
            dto.setStatus(1);
            NoteVO note = noteService.create(userId, dto);
            // 标记 source_type = upload
            NoteUpdateDTO mark = new NoteUpdateDTO();
            // sourceType not in update DTO; use direct mapper
            Note n = noteMapper.selectById(note.getId());
            n.setSourceType("upload");
            n.setSourceRef(String.valueOf(item.getId()));
            noteMapper.updateById(n);

            item.setFinalNoteId(note.getId());
            item.setResolution("create");
            item.setUpdateTime(LocalDateTime.now());
            itemMapper.updateById(item);

            task.setStatus("committed");
            task.setProcessedFiles(1);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return note;
        } catch (IOException | RuntimeException e) {
            log.error("import single file failed: {}", fileName, e);
            item.setParseStatus("failed");
            item.setErrorMsg(e.getMessage());
            item.setUpdateTime(LocalDateTime.now());
            itemMapper.updateById(item);
            task.setStatus("failed");
            task.setFailedFiles(1);
            task.setErrorMsg(e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            throw new BusinessException("导入失败：" + e.getMessage());
        }
    }

    /* ============= P1：批量异步导入（含 ZIP 自动展开） ============= */

    @Override
    public Long importBatchAsync(Long userId, MultipartFile[] files, boolean enrichEnabled) {
        if (files == null || files.length == 0) throw new BusinessException("没有文件");

        // 单 ZIP 自动展开
        if (files.length == 1 && hasExt(files[0].getOriginalFilename(), "zip")) {
            return importZipAsync(userId, files[0], enrichEnabled);
        }

        // 多文件 / 文件夹（webkitdirectory 也是平铺 MultipartFile[]，path 在 originalFilename）
        return importMultiAsync(userId, files, enrichEnabled);
    }

    private Long importZipAsync(Long userId, MultipartFile zipFile, boolean enrichEnabled) {
        Path tempDir = createTempDir("zip");
        List<ZipExtractor.ZipEntryFile> entries;
        try (InputStream in = zipFile.getInputStream()) {
            entries = zipExtractor.extract(in, tempDir, SUPPORTED_NESTED);
        } catch (IOException e) {
            cleanupSilently(tempDir);
            throw new BusinessException("ZIP 解压失败：" + e.getMessage());
        }
        if (entries.isEmpty()) {
            cleanupSilently(tempDir);
            throw new BusinessException("ZIP 中没有可识别的笔记文件");
        }

        NoteImportTask task = newTask(userId, "zip", zipFile.getOriginalFilename(),
                                       entries.size(), enrichEnabled);
        Map<Long, Path> pathMap = new LinkedHashMap<>();
        for (ZipExtractor.ZipEntryFile ze : entries) {
            NoteImportItem item = newItem(task.getId(), userId, ze.getPath(), ze.getFileName(), ze.getSize());
            pathMap.put(item.getId(), ze.getTempPath());
        }
        pipeline.run(task.getId(), pathMap, tempDir);
        return task.getId();
    }

    private Long importMultiAsync(Long userId, MultipartFile[] files, boolean enrichEnabled) {
        // 过滤支持的扩展名
        int ok = 0;
        for (MultipartFile f : files) if (parserRegistry.supports(f.getOriginalFilename())) ok++;
        if (ok == 0) throw new BusinessException("没有可识别的笔记文件");

        Path tempDir = createTempDir("multi");
        NoteImportTask task = newTask(userId, files.length > 1 ? "folder" : "file",
                                       files[0].getOriginalFilename(), ok, enrichEnabled);
        Map<Long, Path> pathMap = new LinkedHashMap<>();
        int seq = 0;
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename();
            if (name == null || !parserRegistry.supports(name)) continue;
            try {
                String displayName = baseName(name);
                NoteImportItem item = newItem(task.getId(), userId, name, displayName, f.getSize());
                Path tmp = tempDir.resolve("u" + (seq++) + "_" + sanitize(displayName));
                try (InputStream in = f.getInputStream()) {
                    Files.copy(in, tmp);
                }
                pathMap.put(item.getId(), tmp);
            } catch (IOException e) {
                log.warn("spool upload to tmp failed: {}", name, e);
            }
        }
        if (pathMap.isEmpty()) {
            cleanupSilently(tempDir);
            throw new BusinessException("所有上传文件落盘失败");
        }
        pipeline.run(task.getId(), pathMap, tempDir);
        return task.getId();
    }

    private Path createTempDir(String tag) {
        try {
            return Files.createTempDirectory("socialflow-note-" + tag + "-");
        } catch (IOException e) {
            throw new BusinessException("无法创建临时目录：" + e.getMessage());
        }
    }

    /** 同步路径异常时清理；pipeline 运行时的清理由 pipeline 自己 finally 处理 */
    private void cleanupSilently(Path dir) {
        if (dir == null) return;
        try (java.util.stream.Stream<Path> w = Files.walk(dir)) {
            w.sorted(java.util.Comparator.reverseOrder())
             .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    /** 文件名兜底：剥掉路径分隔符，避免落地文件穿透 */
    private static String sanitize(String name) {
        if (name == null) return "f";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @Override
    public SseEmitter subscribe(Long userId, Long taskId) {
        loadTask(userId, taskId); // 校验权限
        return sse.create(taskId);
    }

    /* ============= 任务管理 ============= */

    @Override
    public List<NoteImportTaskVO> listRecentTasks(Long userId) {
        List<NoteImportTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<NoteImportTask>()
                        .eq(NoteImportTask::getUserId, userId)
                        .orderByDesc(NoteImportTask::getId)
                        .last("LIMIT 50"));
        return tasks.stream().map(this::toTaskVO).toList();
    }

    @Override
    public NoteImportTaskVO getTask(Long userId, Long taskId) {
        NoteImportTask task = loadTask(userId, taskId);
        NoteImportTaskVO vo = toTaskVO(task);
        List<NoteImportItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<NoteImportItem>()
                        .eq(NoteImportItem::getTaskId, taskId)
                        .orderByAsc(NoteImportItem::getId));
        vo.setItems(items.stream().map(this::toItemVO).toList());
        return vo;
    }

    @Override
    public void updateItem(Long userId, Long taskId, Long itemId, NoteImportItemUpdateDTO dto) {
        NoteImportTask task = loadTask(userId, taskId);
        if (!"reviewing".equals(task.getStatus()) && !"running".equals(task.getStatus())) {
            throw new BusinessException("当前任务状态不允许修改");
        }
        NoteImportItem item = itemMapper.selectById(itemId);
        if (item == null || !item.getTaskId().equals(taskId)) {
            throw new NotFoundException("子项不存在");
        }
        if (dto.getParsedTitle() != null) item.setParsedTitle(dto.getParsedTitle());
        if (dto.getResolution() != null)  item.setResolution(dto.getResolution());

        // 把可改的 AI 字段（summary/tags/category/isPublic）落到 ai_payload
        if (dto.getSummary() != null || dto.getTags() != null
                || dto.getCategoryId() != null || dto.getIsPublic() != null) {
            try {
                Map<String, Object> payload = item.getAiPayload() == null
                        ? new LinkedHashMap<>()
                        : M.readValue(item.getAiPayload(), Map.class);
                if (dto.getSummary() != null)    payload.put("summary", dto.getSummary());
                if (dto.getTags() != null)       payload.put("tags", dto.getTags());
                if (dto.getCategoryId() != null) payload.put("categoryId", dto.getCategoryId());
                if (dto.getIsPublic() != null)   payload.put("isPublic", dto.getIsPublic());
                item.setAiPayload(M.writeValueAsString(payload));
            } catch (Exception e) {
                log.warn("merge ai_payload failed: {}", e.getMessage());
            }
        }
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    @Override
    @Transactional
    public void commit(Long userId, Long taskId) {
        NoteImportTask task = loadTask(userId, taskId);
        if (!"reviewing".equals(task.getStatus()) && !"running".equals(task.getStatus())) {
            throw new BusinessException("任务当前状态不允许提交：" + task.getStatus());
        }

        List<NoteImportItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<NoteImportItem>().eq(NoteImportItem::getTaskId, taskId));

        int created = 0;
        for (NoteImportItem item : items) {
            // 已经有 finalNoteId 跳过（避免重复入库）
            if (item.getFinalNoteId() != null) continue;
            // 解析失败的也跳过
            if (!"done".equals(item.getParseStatus()) && !"skipped".equals(item.getParseStatus())) continue;

            String resolution = item.getResolution() == null ? "skip" : item.getResolution();
            switch (resolution) {
                case "skip" -> {
                    item.setParsedMd(null); // 释放占用
                    itemMapper.updateById(item);
                }
                case "create"    -> commitAsCreate(userId, item);
                case "overwrite" -> commitAsOverwrite(userId, item);
                case "merge"     -> commitAsMerge(userId, item);
                default          -> log.warn("unknown resolution {} for item {}", resolution, item.getId());
            }
            if (item.getFinalNoteId() != null) created++;
        }

        task.setStatus("committed");
        task.setProcessedFiles(items.size());
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("committed task {} created {} notes", taskId, created);
    }

    private void commitAsCreate(Long userId, NoteImportItem item) {
        NoteCreateDTO dto = aiPayloadToCreateDTO(item);
        NoteVO note = noteService.create(userId, dto);
        markNoteSource(note.getId(), "upload", String.valueOf(item.getId()));
        item.setFinalNoteId(note.getId());
        item.setParsedMd(null);
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    private void commitAsOverwrite(Long userId, NoteImportItem item) {
        if (item.getConflictWithNoteId() == null) { commitAsCreate(userId, item); return; }
        NoteUpdateDTO upd = new NoteUpdateDTO();
        upd.setTitle(item.getParsedTitle());
        upd.setContentMd(item.getParsedMd());
        AiBag bag = readAiBag(item);
        upd.setSummary(bag.summary);
        upd.setTags(bag.tags);
        upd.setCategoryId(bag.categoryId);
        noteService.update(userId, item.getConflictWithNoteId(), upd);
        item.setFinalNoteId(item.getConflictWithNoteId());
        item.setParsedMd(null);
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    private void commitAsMerge(Long userId, NoteImportItem item) {
        if (item.getConflictWithNoteId() == null) { commitAsCreate(userId, item); return; }
        // 简化合并：把新内容追加到旧 note 末尾，加分隔
        Note old = noteMapper.selectById(item.getConflictWithNoteId());
        if (old == null) { commitAsCreate(userId, item); return; }
        String merged = (old.getContentMd() == null ? "" : old.getContentMd())
                + "\n\n---\n_merged at " + LocalDateTime.now() + " from import_\n\n"
                + (item.getParsedMd() == null ? "" : item.getParsedMd());
        NoteUpdateDTO upd = new NoteUpdateDTO();
        upd.setContentMd(merged);
        noteService.update(userId, old.getId(), upd);
        item.setFinalNoteId(old.getId());
        item.setParsedMd(null);
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    @Override
    public void cancel(Long userId, Long taskId) {
        NoteImportTask task = loadTask(userId, taskId);
        task.setStatus("cancelled");
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        List<NoteImportItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<NoteImportItem>().eq(NoteImportItem::getTaskId, taskId));
        for (NoteImportItem it : items) {
            if (it.getFinalNoteId() == null) {
                it.setParsedMd(null);
                itemMapper.updateById(it);
            }
        }
    }

    /* ============= helpers ============= */

    private NoteImportTask newTask(Long userId, String sourceType, String sourceName,
                                    int total, boolean enrichEnabled) {
        NoteImportTask t = new NoteImportTask();
        t.setUserId(userId);
        t.setSourceType(sourceType);
        t.setSourceName(sourceName);
        t.setTotalFiles(total);
        t.setProcessedFiles(0);
        t.setFailedFiles(0);
        t.setStatus("pending");
        t.setEnrichEnabled(enrichEnabled ? 1 : 0);
        taskMapper.insert(t);
        return t;
    }

    private NoteImportItem newItem(Long taskId, Long userId, String filePath, String fileName, Long size) {
        NoteImportItem item = new NoteImportItem();
        item.setTaskId(taskId);
        item.setUserId(userId);
        item.setFilePath(filePath);
        item.setFileName(fileName);
        item.setFileSize(size == null ? 0L : size);
        item.setParseStatus("pending");
        item.setEnrichStatus("pending");
        item.setResolution("pending");
        item.setRetryCount(0);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.insert(item);
        return item;
    }

    private NoteImportTask loadTask(Long userId, Long taskId) {
        NoteImportTask t = taskMapper.selectById(taskId);
        if (t == null) throw new NotFoundException("任务不存在");
        if (!t.getUserId().equals(userId)) throw new BusinessException("无权访问该任务");
        return t;
    }

    private NoteImportTaskVO toTaskVO(NoteImportTask t) {
        NoteImportTaskVO v = new NoteImportTaskVO();
        v.setId(t.getId());
        v.setSourceType(t.getSourceType());
        v.setSourceName(t.getSourceName());
        v.setTotalFiles(t.getTotalFiles());
        v.setProcessedFiles(t.getProcessedFiles());
        v.setFailedFiles(t.getFailedFiles());
        v.setStatus(t.getStatus());
        v.setEnrichEnabled(t.getEnrichEnabled());
        v.setCreateTime(t.getCreateTime());
        v.setFinishedAt(t.getFinishedAt());
        return v;
    }

    private NoteImportItemVO toItemVO(NoteImportItem it) {
        NoteImportItemVO v = new NoteImportItemVO();
        v.setId(it.getId());
        v.setTaskId(it.getTaskId());
        v.setFileName(it.getFileName());
        v.setFilePath(it.getFilePath());
        v.setFileSize(it.getFileSize());
        v.setParseStatus(it.getParseStatus());
        v.setEnrichStatus(it.getEnrichStatus());
        v.setParsedTitle(it.getParsedTitle());
        v.setParsedMd(it.getParsedMd());
        v.setAiPayload(it.getAiPayload());
        v.setConflictWithNoteId(it.getConflictWithNoteId());
        if (it.getConflictWithNoteId() != null) {
            Note conflict = noteMapper.selectById(it.getConflictWithNoteId());
            if (conflict != null) v.setConflictWithNoteTitle(conflict.getTitle());
        }
        v.setResolution(it.getResolution());
        v.setFinalNoteId(it.getFinalNoteId());
        v.setErrorMsg(it.getErrorMsg());
        return v;
    }

    private void markNoteSource(Long noteId, String type, String ref) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) return;
        n.setSourceType(type);
        n.setSourceRef(ref);
        noteMapper.updateById(n);
    }

    private NoteCreateDTO aiPayloadToCreateDTO(NoteImportItem item) {
        NoteCreateDTO dto = new NoteCreateDTO();
        dto.setTitle(item.getParsedTitle());
        dto.setContentMd(item.getParsedMd());
        AiBag bag = readAiBag(item);
        dto.setSummary(bag.summary);
        dto.setTags(bag.tags);
        dto.setCategoryId(bag.categoryId);
        dto.setIsPublic(bag.isPublic);
        dto.setStatus(1);
        return dto;
    }

    private AiBag readAiBag(NoteImportItem item) {
        AiBag bag = new AiBag();
        if (item.getAiPayload() == null) return bag;
        try {
            JsonNode root = M.readTree(item.getAiPayload());
            if (root.hasNonNull("summary")) bag.summary = root.get("summary").asText();
            if (root.hasNonNull("tags") && root.get("tags").isArray()) {
                bag.tags = new java.util.ArrayList<>();
                root.get("tags").forEach(t -> bag.tags.add(t.asText()));
            }
            if (root.hasNonNull("categoryId")) bag.categoryId = root.get("categoryId").asLong();
            if (root.hasNonNull("isPublic"))   bag.isPublic = root.get("isPublic").asInt();
        } catch (Exception e) {
            log.warn("read ai_payload failed: {}", e.getMessage());
        }
        return bag;
    }

    private static class AiBag {
        String summary;
        List<String> tags;
        Long categoryId;
        Integer isPublic;
    }

    private static boolean hasExt(String name, String ext) {
        return name != null && name.toLowerCase().endsWith("." + ext);
    }

    private static String baseName(String name) {
        if (name == null) return "未命名";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash < 0 ? name : name.substring(slash + 1);
    }

}
