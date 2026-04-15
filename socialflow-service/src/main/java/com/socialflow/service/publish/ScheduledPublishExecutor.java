package com.socialflow.service.publish;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.socialflow.common.enums.PlatformType;
import com.socialflow.dao.mapper.ContentMapper;
import com.socialflow.dao.mapper.PublishTaskMapper;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.PublishTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时发布执行器 —— 扫描到期的 SCHEDULED PublishTask 并触发实际发布。
 *
 * <p>这是 Wave 3.1 对核心硬伤"PublishTask 表有 scheduled_time 字段但无任何 @Scheduled
 * 消费者"的修复。修复前定时发布功能完全是空架子；修复后调度器每 30s 扫一次到期任务，
 * 把状态机驱动起来：</p>
 *
 * <pre>
 *   PENDING --(到期)--&gt; EXECUTING --(发布成功)--&gt; SUCCESS
 *                                |--(失败 retry&lt;3)--&gt; PENDING(指数退避后再扫)
 *                                |--(失败 retry&gt;=3)--&gt; FAILED_PERMANENT
 * </pre>
 *
 * <p>多实例部署时通过 ShedLock + Redis 保证全集群只有一个实例在执行（避免重复发布）。</p>
 *
 * <p>使用 {@code UPDATE ... WHERE id=? AND status='PENDING'} 做乐观锁，确保即便
 * 不同实例同时拿到任务，只有一个能成功 claim。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPublishExecutor {

    /** 单次扫描最多处理多少个任务，避免单次扫描时间过长 */
    private static final int BATCH_SIZE = 50;

    /** 永久失败前的最大重试次数 */
    private static final int MAX_RETRY = 3;

    private final PublishTaskMapper publishTaskMapper;
    private final ContentMapper contentMapper;
    private final PublishRouter publishRouter;

    /**
     * 每 30 秒扫描一次到期的 SCHEDULED PublishTask。
     * <p>{@code lockAtMostFor=PT5M} 给 ShedLock 的兜底锁时长，防止本节点 crash 后锁卡死。
     * {@code lockAtLeastFor=PT5S} 即使任务跑得很快也至少持锁 5s，避免节点之间频繁竞争。</p>
     */
    @Scheduled(fixedDelay = 30_000)
    @SchedulerLock(name = "scheduled-publish-scan", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void scanAndExecute() {
        List<PublishTask> due = findDueTasks();
        if (due.isEmpty()) {
            log.debug("[ScheduledPublish] no due tasks");
            return;
        }
        log.info("[ScheduledPublish] picked {} due tasks", due.size());
        for (PublishTask task : due) {
            executeOne(task);
        }
    }

    /**
     * 查询到期任务：publish_type='SCHEDULED' AND status='PENDING' AND scheduled_time &lt;= NOW()。
     */
    private List<PublishTask> findDueTasks() {
        LambdaQueryWrapper<PublishTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PublishTask::getPublishType, "SCHEDULED")
                .eq(PublishTask::getStatus, "PENDING")
                .le(PublishTask::getScheduledTime, LocalDateTime.now())
                .orderByAsc(PublishTask::getScheduledTime)
                .last("LIMIT " + BATCH_SIZE);
        return publishTaskMapper.selectList(wrapper);
    }

    /**
     * 执行单个任务：claim → publish → 更新状态 → 失败时退避重排或永久失败。
     */
    private void executeOne(PublishTask task) {
        // 1. claim：UPDATE WHERE id=? AND status='PENDING' 防止多实例并发 picked
        if (!claim(task)) {
            log.debug("[ScheduledPublish] task {} already claimed by other instance", task.getId());
            return;
        }

        // 2. 加载 content
        Content content = contentMapper.selectById(task.getContentId());
        if (content == null) {
            markFailed(task, "content not found: " + task.getContentId(), true);
            return;
        }

        // 3. 找 Publisher 并执行
        try {
            PlatformType platform = PlatformType.of(content.getPlatform());
            Publisher publisher = publishRouter.get(platform);

            // platformAccountId 暂时传 null（与现有 PublishController.autoPublish 行为一致）
            // Wave 3.3 微博 auto-publish 时会在这里加 PlatformAccount 加载
            PublishResult result = publisher.publish(content, null);

            if (result.isSuccess()) {
                markSuccess(task, result);
            } else {
                handleFailure(task, result.getErrorMessage() != null
                        ? result.getErrorMessage() : "unknown publish failure");
            }
        } catch (Exception e) {
            log.error("[ScheduledPublish] task {} threw exception", task.getId(), e);
            handleFailure(task, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    /**
     * 乐观锁 claim —— 把 status PENDING → EXECUTING，只允许一个实例成功。
     * @return true 表示 claim 成功（影响行数=1）
     */
    private boolean claim(PublishTask task) {
        LambdaUpdateWrapper<PublishTask> update = new LambdaUpdateWrapper<>();
        update.eq(PublishTask::getId, task.getId())
                .eq(PublishTask::getStatus, "PENDING")
                .set(PublishTask::getStatus, "EXECUTING")
                .set(PublishTask::getExecutedTime, LocalDateTime.now());
        int affected = publishTaskMapper.update(null, update);
        if (affected > 0) {
            // 同步 in-memory 状态便于后续日志
            task.setStatus("EXECUTING");
            task.setExecutedTime(LocalDateTime.now());
        }
        return affected == 1;
    }

    private void markSuccess(PublishTask task, PublishResult result) {
        task.setStatus("SUCCESS");
        task.setResultMsg(result.getPublishedUrl() != null
                ? "发布成功, URL: " + result.getPublishedUrl()
                : "发布成功");
        publishTaskMapper.updateById(task);
        log.info("[ScheduledPublish] task {} SUCCESS", task.getId());
    }

    private void markFailed(PublishTask task, String reason, boolean permanent) {
        task.setStatus(permanent ? "FAILED_PERMANENT" : "FAILED");
        task.setResultMsg(reason);
        publishTaskMapper.updateById(task);
        log.warn("[ScheduledPublish] task {} {}: {}", task.getId(), task.getStatus(), reason);
    }

    /**
     * 失败处理：retry &lt; MAX_RETRY 时回 PENDING 并指数退避（2^retry * 60s），否则永久失败。
     */
    private void handleFailure(PublishTask task, String reason) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        retryCount++;

        if (retryCount >= MAX_RETRY) {
            task.setRetryCount(retryCount);
            markFailed(task, "retry exhausted (" + retryCount + "): " + reason, true);
            return;
        }

        // 指数退避：1min, 2min, 4min ...
        long backoffSeconds = (long) Math.pow(2, retryCount) * 60L;
        LocalDateTime nextScheduled = LocalDateTime.now().plusSeconds(backoffSeconds);

        task.setStatus("PENDING");
        task.setRetryCount(retryCount);
        task.setScheduledTime(nextScheduled);
        task.setResultMsg("retry " + retryCount + " scheduled at " + nextScheduled + " - " + reason);
        publishTaskMapper.updateById(task);
        log.warn("[ScheduledPublish] task {} requeued (retry={}, next={}): {}",
                task.getId(), retryCount, nextScheduled, reason);
    }
}
