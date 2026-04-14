package com.socialflow.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 多平台批量文案生成请求 DTO —— 用于一次性为多个平台生成文案的接口入参
 *
 * 【作用】继承 ContentGenerateDTO，在其基础上增加了多平台列表字段。
 *   用户可以一次性指定多个目标平台（如同时为小红书和抖音生成文案），
 *   系统会为每个平台分别生成一篇风格适配的文案。
 *
 * 【对应 API 接口】
 *   POST /api/content/batch-generate  —— 多平台批量文案生成
 *
 * 【使用场景】
 *   用户想将同一个主题的文案发布到多个平台时，不需要逐一生成，
 *   而是通过本接口一次性提交，系统自动为每个平台定制文案。
 *
 * 【与父类 ContentGenerateDTO 的区别】
 *   父类的 platform 字段指定单个平台，本类的 platforms 字段指定多个平台。
 *   生成时以 platforms 列表为准。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentBatchGenerateDTO extends ContentGenerateDTO {

    /**
     * 目标平台列表（必填，至少一个）
     *
     * 指定要为哪些平台分别生成文案。
     * 可选值："XIAOHONGSHU"、"DOUYIN"、"WECHAT_MOMENT"、"WECHAT_MP"
     * 示例：["XIAOHONGSHU", "DOUYIN"]
     * 系统会为列表中的每个平台各生成一篇文案。
     */
    @NotEmpty
    private List<@NotBlank String> platforms;
}
