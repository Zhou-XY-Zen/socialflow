package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档视图对象 —— GET /api/v1/kb/{kbId}/docs 返回。
 */
@Data
public class KbDocVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer charCount;
    private Integer chunkCount;
    /** PENDING / PARSING / COMPLETED / FAILED */
    private String parseStatus;
    private String parseError;
    private LocalDateTime createTime;
}
