package com.socialflow.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文案改写请求 DTO —— 用于对已有文案进行 AI 改写的接口入参
 *
 * 【作用】封装用户对已有文案进行改写/转换时需要的参数。
 *   用户可以将一篇已有文案改写为适合另一个平台的风格，或者调整语气。
 *   例如将小红书文案改写为适合抖音的版本，或将轻松语气改为专业语气。
 *
 * 【对应 API 接口】
 *   POST /api/content/rewrite  —— 文案改写
 *
 * 【使用场景】
 *   - 用户在文案详情页点击"改写"按钮
 *   - 选择目标平台和/或目标语气
 *   - AI 基于原始文案进行改写，生成新版本
 *   - 改写后的内容会自动保存为原文案的新版本（content_version）
 */
@Data
public class ContentRewriteDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 要改写的原文案 ID（必填）
     *
     * 关联 content.id，指定要对哪篇文案进行改写。
     * 系统会读取该文案的 body 作为改写的输入。
     */
    @NotNull
    private Long contentId;

    /**
     * 目标平台（选填）
     *
     * 改写后文案的目标平台。AI 会将内容调整为该平台的风格和格式。
     * 可选值："XIAOHONGSHU"、"DOUYIN"、"WECHAT_MOMENT"、"WECHAT_MP"
     * 为空时保持原平台不变。
     */
    private String targetPlatform;

    /**
     * 目标语气/风格（选填）
     *
     * 改写后文案的目标语气。
     * 可选值："casual"（随性）、"professional"（专业）、
     *         "humorous"（幽默）、"inspiring"（激励）
     * 为空时保持原语气不变。
     */
    private String targetTone;

    /**
     * 指定 AI 模型（选填）
     *
     * 覆盖默认模型，使用指定的模型进行改写。
     * 示例："gpt-4o"、"claude-3-sonnet"
     * 为空时使用系统默认模型。
     */
    private String model;
}
