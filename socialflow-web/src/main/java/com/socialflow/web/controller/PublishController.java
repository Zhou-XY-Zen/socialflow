package com.socialflow.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.enums.PlatformType;
import com.socialflow.common.result.R;
import com.socialflow.dao.mapper.ContentMapper;
import com.socialflow.dao.mapper.PublishTaskMapper;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.PublishTask;
import com.socialflow.service.publish.PublishResult;
import com.socialflow.service.publish.PublishRouter;
import com.socialflow.service.publish.Publisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 发布控制器 —— 负责内容的分发和辅助发布功能。
 *
 * 本控制器处理的基础 URL 路径为 {@code /api/v1/publish}，包含以下功能：
 *     - 准备辅助发布资源（格式化文案供用户复制粘贴）
 *     - 查询当前用户的发布任务列表
 *
 * 所有接口都需要用户登录后才能访问，通过 Sa-Token 的 {@code StpUtil.getLoginIdAsLong()}
 * 获取当前登录用户的 ID，确保用户只能操作自己的数据。
 *
 * @see PublishRouter 发布路由器，根据平台类型选择对应的 Publisher 实现
 * @see Publisher 发布者接口
 */
@Tag(name = "publish", description = "content publishing and distribution")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/publish")
@RequiredArgsConstructor
public class PublishController {

    /** 发布路由器 —— 根据平台选择对应的 Publisher 实现 */
    private final PublishRouter publishRouter;

    /** 内容 Mapper —— 查询内容详情 */
    private final ContentMapper contentMapper;

    /** 发布任务 Mapper —— 查询发布任务列表 */
    private final PublishTaskMapper publishTaskMapper;

    /**
     * 准备辅助发布资源。
     *
     * 接口路径：POST /api/v1/publish/prepare
     *
     * 功能：根据内容 ID 查找对应文案，调用该平台的 Publisher.prepare() 方法，
     * 将文案格式化为适合该平台的风格（添加 Emoji、话题标签等），
     * 返回格式化后的文案文本供用户复制粘贴。
     *
     * @param body 请求体，包含 contentId 字段
     * @return 统一响应体 R，包含 PublishResult（bundleUrl 字段存放格式化后的文案）
     */
    @Operation(summary = "prepare content for assisted publishing")
    @PostMapping("/prepare")
    public R<PublishResult> prepare(@RequestBody Map<String, Long> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long contentId = body.get("contentId");

        // 查询内容并校验归属
        Content content = contentMapper.selectById(contentId);
        if (content == null || !content.getUserId().equals(userId)) {
            return R.fail("内容不存在或无权访问");
        }

        // 根据平台获取对应的 Publisher 并准备发布资源
        PlatformType platformType = PlatformType.of(content.getPlatform());
        Publisher publisher = publishRouter.get(platformType);
        PublishResult result = publisher.prepare(content);

        return R.ok(result);
    }

    /**
     * 自动发布到指定平台。
     *
     * 接口路径：POST /api/v1/publish/auto
     *
     * 功能：根据内容 ID 和平台类型，调用对应 Publisher 的 publish() 方法，
     * 通过平台官方 API 自动发布内容。同时创建一条 PublishTask 记录用于追踪。
     *
     * @param body 请求体，包含 contentId（内容ID）和 platform（平台类型）
     * @return 统一响应体 R，包含 PublishResult
     */
    @Operation(summary = "auto publish to platform")
    @PostMapping("/auto")
    public R<PublishResult> autoPublish(@RequestBody Map<String, Object> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long contentId = Long.valueOf(body.get("contentId").toString());
        String platform = body.get("platform").toString();

        // 查询内容并校验归属
        Content content = contentMapper.selectById(contentId);
        if (content == null || !content.getUserId().equals(userId)) {
            return R.fail("内容不存在或无权访问");
        }

        // 获取对应平台的 Publisher
        PlatformType platformType = PlatformType.of(platform);
        Publisher publisher = publishRouter.get(platformType);

        if (!publisher.supportsAuto()) {
            return R.fail("该平台不支持自动发布");
        }

        // 创建发布任务记录
        PublishTask task = new PublishTask();
        task.setContentId(contentId);
        task.setPublishType("IMMEDIATE");
        task.setStatus("EXECUTING");
        task.setExecutedTime(LocalDateTime.now());
        task.setRetryCount(0);
        publishTaskMapper.insert(task);

        // 调用 Publisher 执行自动发布（account 传 null，WechatMpPublisher 使用内部配置的 appId/appSecret）
        PublishResult result = publisher.publish(content, null);

        // 更新任务状态
        task.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
        task.setResultMsg(result.isSuccess()
                ? "发布成功" + (result.getPublishedUrl() != null ? ", URL: " + result.getPublishedUrl() : "")
                : result.getErrorMessage());
        publishTaskMapper.updateById(task);

        return R.ok(result);
    }

    /**
     * 查询当前用户的发布任务列表。
     *
     * 接口路径：GET /api/v1/publish/tasks
     *
     * 功能：查询当前登录用户创建的所有发布任务，按创建时间倒序排列。
     * 通过关联 content 表的 userId 字段来过滤当前用户的任务。
     *
     * @return 统一响应体 R，包含发布任务列表
     */
    /**
     * 创建定时发布任务（Wave 3.1）。
     *
     * <p>请求体：{@code {"contentId": 123, "scheduledTime": "2026-04-15T20:00:00", "platformAccountId": null}}</p>
     *
     * <p>创建后任务以 {@code status=PENDING, publish_type=SCHEDULED} 入库，
     * 由 {@link com.socialflow.service.publish.ScheduledPublishExecutor} 每 30s 扫描到期触发。</p>
     */
    @Operation(summary = "schedule a publish task at a future time")
    @PostMapping("/schedule")
    public R<PublishTask> schedule(@RequestBody Map<String, Object> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long contentId = Long.valueOf(body.get("contentId").toString());
        String scheduledTimeStr = String.valueOf(body.get("scheduledTime"));

        Content content = contentMapper.selectById(contentId);
        if (content == null || !content.getUserId().equals(userId)) {
            return R.fail("内容不存在或无权访问");
        }

        LocalDateTime scheduledTime;
        try {
            scheduledTime = LocalDateTime.parse(scheduledTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return R.fail("scheduledTime 格式错误，需要 ISO yyyy-MM-ddTHH:mm:ss");
        }
        if (scheduledTime.isBefore(LocalDateTime.now())) {
            return R.fail("scheduledTime 必须晚于当前时间");
        }

        PublishTask task = new PublishTask();
        task.setContentId(contentId);
        Object accId = body.get("platformAccountId");
        if (accId != null) {
            task.setPlatformAccountId(Long.valueOf(accId.toString()));
        }
        task.setPublishType("SCHEDULED");
        task.setStatus("PENDING");
        task.setScheduledTime(scheduledTime);
        task.setRetryCount(0);
        publishTaskMapper.insert(task);

        return R.ok(task);
    }

    /**
     * 取消尚未执行的定时任务（Wave 3.1）。
     *
     * <p>仅 status=PENDING 的任务可取消；EXECUTING/SUCCESS/FAILED_PERMANENT 都拒绝。</p>
     */
    @Operation(summary = "cancel a pending scheduled publish task")
    @PostMapping("/tasks/{id}/cancel")
    public R<PublishTask> cancelTask(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        PublishTask task = publishTaskMapper.selectById(id);
        if (task == null) {
            return R.fail("任务不存在");
        }
        Content content = contentMapper.selectById(task.getContentId());
        if (content == null || !content.getUserId().equals(userId)) {
            return R.fail("无权操作此任务");
        }
        if (!"PENDING".equals(task.getStatus())) {
            return R.fail("仅 PENDING 状态的任务可取消，当前状态：" + task.getStatus());
        }
        task.setStatus("CANCELLED");
        task.setResultMsg("用户主动取消");
        publishTaskMapper.updateById(task);
        return R.ok(task);
    }

    /**
     * 手动重试失败的任务（Wave 3.1）。
     *
     * <p>把 status FAILED/FAILED_PERMANENT 重置为 PENDING，retryCount 清零，
     * scheduledTime 设为现在，让 ScheduledPublishExecutor 下次扫描时立即处理。</p>
     */
    @Operation(summary = "manually retry a failed publish task")
    @PostMapping("/tasks/{id}/retry")
    public R<PublishTask> retryTask(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        PublishTask task = publishTaskMapper.selectById(id);
        if (task == null) {
            return R.fail("任务不存在");
        }
        Content content = contentMapper.selectById(task.getContentId());
        if (content == null || !content.getUserId().equals(userId)) {
            return R.fail("无权操作此任务");
        }
        if (!"FAILED".equals(task.getStatus()) && !"FAILED_PERMANENT".equals(task.getStatus())) {
            return R.fail("仅失败的任务可重试，当前状态：" + task.getStatus());
        }
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setScheduledTime(LocalDateTime.now());
        task.setResultMsg("用户手动重试");
        publishTaskMapper.updateById(task);
        return R.ok(task);
    }

    @Operation(summary = "list publish tasks for current user")
    @GetMapping("/tasks")
    public R<List<PublishTask>> tasks() {
        Long userId = StpUtil.getLoginIdAsLong();

        // 查询当前用户的内容 ID 列表
        LambdaQueryWrapper<Content> contentWrapper = new LambdaQueryWrapper<>();
        contentWrapper.eq(Content::getUserId, userId)
                      .select(Content::getId);
        List<Long> contentIds = contentMapper.selectList(contentWrapper)
                .stream()
                .map(Content::getId)
                .toList();

        if (contentIds.isEmpty()) {
            return R.ok(List.of());
        }

        // 根据内容 ID 查询关联的发布任务
        LambdaQueryWrapper<PublishTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.in(PublishTask::getContentId, contentIds)
                   .orderByDesc(PublishTask::getCreateTime);
        List<PublishTask> tasks = publishTaskMapper.selectList(taskWrapper);

        return R.ok(tasks);
    }
}
