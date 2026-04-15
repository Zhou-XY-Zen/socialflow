package com.socialflow.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 定时任务 + 分布式锁配置 —— 让 {@code @Scheduled} 任务在多实例部署时只跑一份。
 *
 * <p>{@code @EnableScheduling} 已在 {@code SocialFlowApplication} 上声明，本类专注 ShedLock。</p>
 *
 * <p>{@code @EnableSchedulerLock(defaultLockAtMostFor = "PT5M")} 表示：默认每个 @Scheduled
 * 任务的 lock 最多持有 5 分钟（防止 JVM crash 后锁卡死）。具体任务可在 @SchedulerLock
 * 注解里覆盖。</p>
 *
 * <p>RedisLockProvider 使用项目已有的 RedisConnectionFactory，key 前缀 {@code sf:lock:}
 * 与缓存（{@code sf:cache:}）和 Sa-Token Session 区分开。</p>
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "sf:lock");
    }
}
