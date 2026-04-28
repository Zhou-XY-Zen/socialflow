package com.socialflow.service.note;

import com.socialflow.model.dto.NoteImportItemUpdateDTO;
import com.socialflow.model.vo.NoteImportTaskVO;
import com.socialflow.model.vo.NoteVO;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 笔记导入服务
 *
 * P0：单文件同步入库（保留 importSingleFile）
 * P1：批量异步（importBatchAsync 真异步）+ ZIP 自动解包 + 去重
 * P2：流水线 SSE 进度（subscribe）+ Review-before-Commit（commit）
 */
public interface NoteImportService {

    /** 单文件同步导入：解析 → 直接落库（保留 P0 路径） */
    NoteVO importSingleFile(Long userId, MultipartFile file);

    /**
     * 批量异步导入
     *  - 自动识别 ZIP（Notion / Obsidian 导出）→ 解包再处理
     *  - 立即返回 taskId，前端订阅 SSE 看进度，最后跳审阅页 commit
     */
    Long importBatchAsync(Long userId, MultipartFile[] files, boolean enrichEnabled);

    /** 订阅指定任务的 SSE 流 */
    SseEmitter subscribe(Long userId, Long taskId);

    /** 列出最近 50 条任务 */
    List<NoteImportTaskVO> listRecentTasks(Long userId);

    /** 任务详情（含 items） */
    NoteImportTaskVO getTask(Long userId, Long taskId);

    /** 审阅页修改单 item */
    void updateItem(Long userId, Long taskId, Long itemId, NoteImportItemUpdateDTO dto);

    /** 提交：按 resolution 把 reviewing 中的 items 真正入库为 note */
    void commit(Long userId, Long taskId);

    /** 放弃：清掉 parsed_md，task 置 cancelled */
    void cancel(Long userId, Long taskId);
}
