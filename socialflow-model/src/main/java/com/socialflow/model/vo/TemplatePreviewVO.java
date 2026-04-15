package com.socialflow.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 模板预览结果 VO（Wave 4.4）。
 *
 * <p>用户在保存模板前可以"试渲染"——传入示例变量，看看最终生成的 system/user 提示词
 * 是什么样子，以及哪些变量在模板里出现但用户没提供（缺失诊断）。</p>
 */
@Data
@Builder
public class TemplatePreviewVO {

    /** 渲染后的 systemPrompt（已替换占位符） */
    private String renderedSystemPrompt;

    /** 渲染后的 userPrompt（已替换占位符 + 条件块处理） */
    private String renderedUserPrompt;

    /** 模板中检测到的所有变量名（{{var}} 与 {{#var}}{{/var}} 都算） */
    private List<String> declaredVariables;

    /** 用户提供的、且确实出现在模板中的变量名 */
    private List<String> usedVariables;

    /** 模板里出现但用户没传值的变量名（前端可高亮提示） */
    private List<String> missingVariables;

    /** 用户传了但模板里没用到的变量（提示用户简化输入） */
    private List<String> unusedVariables;
}
