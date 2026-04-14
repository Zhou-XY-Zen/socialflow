package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话会话实体类 —— 对应数据库表 `conversation_session`
 *
 * 【作用】存储用户与 AI 的多轮对话会话记录。
 *   用户可以通过对话方式与 AI 交互来逐步完善文案、获取建议、调整内容等。
 *   每个会话保存完整的消息历史，支持上下文连续对话。
 *
 * 【为什么需要它】
 *   与 AI 的交互通常不是一次性的，而是需要多轮对话来逐步优化结果。
 *   保存对话历史可以：
 *   1. 让 AI 在后续回复中参考之前的对话上下文
 *   2. 用户可以回顾之前的对话内容
 *   3. 支持中断后继续对话
 *
 * 【关联关系】
 *   - conversation_session.user_id → sys_user.id （所属用户）
 *
 * 【使用场景】
 *   - 用户在"AI 对话"页面开始一段新对话时创建新会话
 *   - 每轮对话追加消息到 messages 字段
 *   - 会话结束或长时间未活动时归档
 *   - 用户查看历史对话列表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_session")
public class ConversationSession extends BaseEntity {

    /**
     * 所属用户 ID
     *
     * 关联 sys_user.id，标识该对话会话属于哪个用户。
     */
    private Long userId;

    /**
     * 会话类型
     *
     * 标识对话的业务场景，不同类型的会话可能使用不同的系统提示词。
     * 可选值示例："CONTENT_CREATION"（文案创作对话）、"STYLE_CONSULT"（风格咨询）、
     *             "GENERAL"（通用对话）
     */
    private String sessionType;

    /**
     * 消息历史（JSON 数组）
     *
     * 保存本次会话中所有消息的完整记录。
     * JSON 格式：[{"role": "user", "content": "帮我写一篇...", "timestamp": "2024-01-01T10:00:00"}, ...]
     * - role: 消息角色，"user"（用户）或 "assistant"（AI 助手）
     * - content: 消息的文字内容
     * - timestamp: 消息发送的时间戳
     */
    private String messages;

    /**
     * 对话摘要
     *
     * 系统自动对本次对话内容生成的简要总结。
     * 用于在对话列表中展示每个会话的主题，方便用户快速回顾。
     * 示例："关于春季护肤文案的创作讨论"
     */
    private String summary;

    /**
     * 会话状态
     *
     * 可选值："ACTIVE"（活跃，对话进行中）、"ARCHIVED"（已归档，对话结束）。
     * 长时间未活动的会话会被自动归档。
     */
    private String status;   // ACTIVE | ARCHIVED
}
