package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * AI 文生图任务状态 VO —— DashScope 异步任务的查询结果。
 *
 * DashScope 的文生图接口是异步的（提交任务 → 轮询状态 → 获取结果），
 * 本 VO 封装了轮询返回的任务状态和生成结果。
 */
@Data
public class ImageTaskStatusVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务状态。
     * 可选值：PENDING（排队中）、RUNNING（生成中）、SUCCEEDED（成功）、FAILED（失败）
     */
    private String status;

    /** 生成的图片 URL 列表（仅 SUCCEEDED 时有值） */
    private List<String> imageUrls;

    /** 错误信息（仅 FAILED 时有值） */
    private String errorMessage;
}
