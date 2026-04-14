package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内容安全护栏日志实体类 —— 对应数据库表 `guardrail_log`
 *
 * 【作用】记录内容安全审核（Guardrail）的每一次触发记录。
 *   当用户输入或 AI 输出的内容触发了安全规则（如敏感词、违规信息等），
 *   系统会记录触发详情和采取的措施。
 *
 * 【为什么需要它】
 *   AI 生成的文案可能包含不合规内容（如违禁词、虚假宣传用语等）。
 *   护栏机制在 AI 生成前后进行审查，本表记录所有审查事件，用于：
 *   1. 合规审计 —— 证明系统有有效的内容安全措施
 *   2. 规则优化 —— 分析误报/漏报情况来调整安全规则
 *   3. 用户通知 —— 告知用户哪些内容被标记或拦截
 *
 * 【关联关系】
 *   - guardrail_log.user_id → sys_user.id （触发者）
 *
 * 【使用场景】
 *   - AI 生成文案前，检查用户输入是否包含违规内容
 *   - AI 生成文案后，检查输出是否包含违规内容
 *   - 管理员查看安全审计日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("guardrail_log")
public class GuardrailLog extends BaseEntity {

    /**
     * 触发者用户 ID
     *
     * 关联 sys_user.id，标识是哪个用户的操作触发了护栏规则。
     */
    private Long userId;

    /**
     * 触发的规则名称
     *
     * 标识具体触发了哪条安全规则。
     * 示例："SENSITIVE_WORD"（敏感词）、"FALSE_ADVERTISING"（虚假宣传）、
     *       "POLITICAL_CONTENT"（涉政内容）、"PERSONAL_INFO"（个人信息泄露）
     */
    private String ruleName;

    /**
     * 触发类型
     *
     * 标识是在检查输入还是输出时触发的。
     * 可选值："INPUT"（用户输入触发）、"OUTPUT"（AI 输出触发）
     */
    private String triggerType;   // INPUT | OUTPUT

    /**
     * 触发时的输入文本
     *
     * 触发规则时用户发送给 AI 的原始输入内容。
     * 用于审计时回溯原始请求。
     */
    private String inputText;

    /**
     * 触发时的输出文本
     *
     * 触发规则时 AI 生成的原始输出内容（仅当 triggerType=OUTPUT 时有值）。
     * INPUT 类型触发时该字段为空。
     */
    private String outputText;

    /**
     * 触发原因说明
     *
     * 详细描述为什么该内容触发了规则。
     * 示例："包含敏感词'xxx'"、"检测到虚假宣传用语'绝对有效'"
     */
    private String reason;

    /**
     * 采取的处理措施
     *
     * 系统对触发规则的内容采取了什么行动。
     * 可选值："BLOCKED"（直接拦截，不返回结果）、
     *         "REGENERATED"（自动重新生成一次）、
     *         "WARNING"（仍然返回结果，但附带警告提示）
     */
    private String actionTaken;   // BLOCKED | REGENERATED | WARNING
}
