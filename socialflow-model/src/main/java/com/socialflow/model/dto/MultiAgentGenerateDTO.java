package com.socialflow.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多 Agent 协作生成请求 DTO —— 用于多个 AI Agent 协作生成文案的接口入参
 *
 * 【作用】继承 ContentGenerateDTO，在其基础上增加了多 Agent 协作的配置参数。
 *   多 Agent 模式下，系统会让多个 AI 角色（如"写手 Agent"、"审核 Agent"、"优化 Agent"）
 *   进行多轮协作，逐步打磨文案，从而提升最终文案的质量。
 *
 * 【对应 API 接口】
 *   POST /api/content/multi-agent-generate  —— 多 Agent 协作生成
 *
 * 【使用场景】
 *   用户希望获得更高质量的文案时，选择"多 Agent 模式"。
 *   与普通生成相比，该模式会花更多时间和 Token，但文案经过多轮打磨，质量通常更高。
 *
 * 【工作流程示例】
 *   第 1 轮：写手 Agent 根据主题生成初稿
 *   第 2 轮：审核 Agent 提出修改建议
 *   第 3 轮：写手 Agent 根据建议修改优化
 *   （直到达到 maxRounds 或质量满意为止）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MultiAgentGenerateDTO extends ContentGenerateDTO {

    /**
     * 最大协作轮次（选填）
     *
     * 控制多 Agent 之间最多进行几轮协作。
     * 取值范围：1 ~ 5，默认值 3。
     * - 轮次越多，文案打磨越充分，但消耗的时间和 Token 也越多
     * - 建议使用默认值 3，兼顾质量和效率
     */
    @Min(1)
    @Max(5)
    private Integer maxRounds = 3;
}
