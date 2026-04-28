package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 笔记导入任务 —— 一次拖拽 / 一次 ZIP / 一次 URL 剪藏 = 一个 task
 *
 * 状态机：
 *   pending → running（流水线在跑）→ reviewing（等用户审阅）
 *           → committed（已全部入库）/ cancelled（用户放弃）/ failed
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("note_import_task")
public class NoteImportTask extends BaseEntity {

    private Long userId;

    private String sourceType;

    private String sourceName;

    private Integer totalFiles;

    private Integer processedFiles;

    private Integer failedFiles;

    private String status;

    private Integer enrichEnabled;

    private String errorMsg;

    private LocalDateTime finishedAt;
}
