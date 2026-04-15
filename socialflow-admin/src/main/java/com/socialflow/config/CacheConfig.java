package com.socialflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache 配置 —— 用 Redis 单层缓存承载所有应用级 @Cacheable 调用。
 *
 * <p>每个 cache 名独立配置 TTL，反映各自数据的"新鲜度"要求：</p>
 * <ul>
 *     <li>{@code wechatAccessToken} (110min) —— 微信 access_token 微信侧 TTL=120min，提前 10 分钟过期</li>
 *     <li>{@code promptTemplate} (10min) —— 提示词模板，更新不频繁但希望快速生效</li>
 *     <li>{@code guardrailDict} (30min) —— 护栏字典，配置改动后可接受半小时延迟</li>
 *     <li>{@code embeddingCache} (24h) —— 文本向量化结果（最大成本节约）</li>
 *     <li>{@code kbChunkSearch} (5min) —— KB 检索结果，热点查询缓存</li>
 *     <li>{@code platformAccount} (5min) —— 第三方平台账号信息</li>
 *     <li>{@code dashboardOverview} (1min) —— Dashboard 聚合数据</li>
 * </ul>
 *
 * <p>不引入 Caffeine L1 本地缓存：多实例间的失效一致性问题成本高于 Redis 本身延迟（&lt;2ms）。</p>
 *
 * <p>序列化：key 用 String，value 用 Jackson JSON。null 值不缓存，避免 cache stampede。</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer))
                .prefixCacheNameWith("sf:cache:");

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put("wechatAccessToken", defaultConfig.entryTtl(Duration.ofMinutes(110)));
        perCache.put("promptTemplate",    defaultConfig.entryTtl(Duration.ofMinutes(10)));
        perCache.put("guardrailDict",     defaultConfig.entryTtl(Duration.ofMinutes(30)));
        perCache.put("embeddingCache",    defaultConfig.entryTtl(Duration.ofHours(24)));
        perCache.put("kbChunkSearch",     defaultConfig.entryTtl(Duration.ofMinutes(5)));
        perCache.put("platformAccount",   defaultConfig.entryTtl(Duration.ofMinutes(5)));
        perCache.put("dashboardOverview", defaultConfig.entryTtl(Duration.ofMinutes(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .build();
    }
}
