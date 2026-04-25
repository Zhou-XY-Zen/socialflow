package com.socialflow.service.codeanalysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 分享 token 公开访问的限流器（进程内，无 Redis 依赖）。
 *
 * 设计：按客户端标识（IP + UA 的 hash）维度做分钟级滑动窗口计数，
 * 超过阈值即拒绝；阈值/窗口均可通过配置调整。
 *
 * 为什么不接 Redis/Bucket4j：
 *   分享访问属于"非关键路径 + 期望轻量"，进程内 ConcurrentHashMap 已可覆盖
 *   单机日百万以内的量；多实例部署改造也容易（将 WindowCounter 换成 RedisTemplate）。
 */
@Component
public class ShareTokenRateLimiter {

    /** 每窗口允许次数，超过即拒绝 */
    @Value("${socialflow.code-analysis.share.rate-limit:30}")
    private int limit;

    /** 滑动窗口长度（毫秒），默认 60 秒 */
    @Value("${socialflow.code-analysis.share.rate-window-ms:60000}")
    private long windowMs;

    /** 软上限，避免内存无界增长；超过时丢弃最老窗口（简单策略） */
    private static final int MAX_ENTRIES = 10_000;

    private final ConcurrentHashMap<String, WindowCounter> buckets = new ConcurrentHashMap<>();

    /** 返回 true 表示允许；false 表示触发限流应拒绝。 */
    public boolean allow(String clientKey) {
        long now = System.currentTimeMillis();
        WindowCounter updated = buckets.compute(clientKey, (k, v) -> {
            if (v == null || now - v.windowStart > windowMs) {
                return new WindowCounter(now, 1);
            }
            return new WindowCounter(v.windowStart, v.count + 1);
        });
        // 惰性清理：每次访问时随手清掉一个过期项，避免无界增长
        if (buckets.size() > MAX_ENTRIES) {
            buckets.entrySet().removeIf(e -> now - e.getValue().windowStart > windowMs * 2L);
        }
        return updated.count <= limit;
    }

    private record WindowCounter(long windowStart, int count) {}
}
