package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 导入任务 VO —— 含子项列表，审阅页一次性拉取
 */
@Data
public class NoteImportTaskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String sourceType;

    private String sourceName;

    private Integer totalFiles;

    private Integer processedFiles;

    private Integer failedFiles;

    private String status;

    private Integer enrichEnabled;

    private LocalDateTime createTime;

    private LocalDateTime finishedAt;

    private List<NoteImportItemVO> items;
}
