package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 导入任务 commit 后的统计 —— 让前端能直接告诉用户"新建 X、跳过 Y、合并 Z"
 */
@Data
public class NoteImportCommitVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int total;
    private int created;        // 新建
    private int overwritten;    // 覆盖
    private int merged;         // 合并
    private int skipped;        // 显式跳过
    private int skippedDup;     // L1 重复自动跳过（包含在 skipped 里也包含此处统计）
    private int failed;         // parse_status=failed
}
