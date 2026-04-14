package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 发布任务实体类 —— 对应数据库表 `publish_task`
 *
 * 【作用】记录文案的发布任务信息。
 *   当用户对一篇文案执行"立即发布"或"定时发布"操作时，系统会创建一条发布任务记录，
 *   由后台定时任务或消息队列消费者来实际执行发布动作。
 *
 * 【为什么需要它】
 *   发布动作不是瞬时完成的，可能需要调用第三方平台 API、上传图片、等待审核等。
 *   将发布操作抽象为"任务"，可以实现：
 *   1. 异步发布 —— 用户不需要等待发布完成
 *   2. 定时发布 —— 按用户指定的时间自动执行
 *   3. 失败重试 —— 发布失败时可以自动或手动重试
 *   4. 发布状态追踪 —— 用户可以查看每个任务的执行结果
 *
 * 【关联关系】
 *   - publish_task.content_id → content.id （要发布的文案）
 *   - publish_task.platform_account_id → platform_account.id （使用的平台账号）
 *
 * 【使用场景】
 *   - 用户点击"发布"按钮时创建发布任务
 *   - 定时任务扫描到达排期时间的任务并执行
 *   - 用户在"发布任务"列表中查看任务状态
 *   - 发布失败后用户选择"重试"
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("publish_task")
public class PublishTask extends BaseEntity {

    /**
     * 关联的文案 ID
     *
     * 关联 content.id，标识要发布的是哪篇文案。
     */
    private Long contentId;

    /**
     * 使用的平台账号 ID
     *
     * 关联 platform_account.id，标识通过哪个授权账号来发布。
     */
    private Long platformAccountId;

    /**
     * 发布类型
     *
     * 可选值："IMMEDIATE"（立即发布）、"SCHEDULED"（定时发布）。
     */
    private String publishType;

    /**
     * 任务状态
     *
     * 标识任务的执行状态。
     * 可选值："PENDING"（等待执行）、"EXECUTING"（执行中）、
     *         "SUCCESS"（发布成功）、"FAILED"（发布失败）
     */
    private String status;

    /**
     * 排期时间
     *
     * 定时发布的预定执行时间。立即发布的任务该字段为空。
     */
    private LocalDateTime scheduledTime;

    /**
     * 实际执行时间
     *
     * 任务开始实际执行的时间，由系统在执行时自动填写。
     */
    private LocalDateTime executedTime;

    /**
     * 执行结果消息
     *
     * 记录任务执行的结果信息。成功时为平台返回的确认信息，
     * 失败时为错误原因描述，便于用户排查问题。
     * 示例（成功）："发布成功，文章 ID: xxxxx"
     * 示例（失败）："Token 已过期，请重新授权"
     */
    private String resultMsg;

    /**
     * 重试次数
     *
     * 记录该任务已经重试了多少次。初始为 0。
     * 每次发布失败后自动重试时加 1，超过最大重试次数后停止。
     */
    private Integer retryCount;
}
