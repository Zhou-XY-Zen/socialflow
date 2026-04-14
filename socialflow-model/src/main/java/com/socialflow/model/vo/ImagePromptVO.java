package com.socialflow.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 配图提示词 VO —— 从文案内容中提取的绘图提示词。
 *
 * 用于传递给 DashScope wanx 文生图模型生成配图。
 * 提示词为英文（wanx 模型英文 prompt 效果更好）。
 */
@Data
public class ImagePromptVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 中文 AI 绘图提示词（传给通义万相 wanx 模型） */
    private String imagePrompt;
}
