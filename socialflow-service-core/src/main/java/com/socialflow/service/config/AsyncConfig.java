package com.socialflow.service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * service-core 的异步线程池配置 —— 仅定义本模块用到的池。
 *
 * <p>遵循《阿里巴巴 Java 开发手册（黄山版）》1.6.1：线程必须通过 ThreadPoolExecutor 显式创建，
 * 不用 Executors，便于规避资源耗尽。</p>
 *
 * <p><b>设计原则</b>：每个模块管自己的 @Async 池。</p>
 * <ul>
 *   <li>service-core 的 knowledgeIngestExecutor → 本类</li>
 *   <li>service-codeanalysis 的 codeAnalysisExecutor / moduleSummaryExecutor →
 *       {@code com.socialflow.service.codeanalysis.CodeAnalysisAsyncConfig}</li>
 * </ul>
 *
 * <p>这样做让 service-codeanalysis 可独立部署（不再依赖 service-core 提供线程池）。</p>
 *
 * <p>{@code @EnableAsync} 已在 {@code SocialFlowApplication} 全局开启，本类仅注册 Bean。</p>
 */
@Slf4j
@Configuration
public class AsyncConfig {

    /**
     * 知识库入库专用线程池。短耗时、偏 I/O，但并发可能较高（多文件批量上传）。
     */
    @Bean("knowledgeIngestExecutor")
    public Executor knowledgeIngestExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(3);
        e.setMaxPoolSize(8);
        e.setQueueCapacity(100);
        e.setKeepAliveSeconds(60);
        e.setThreadNamePrefix("kb-ingest-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        e.setWaitForTasksToCompleteOnShutdown(true);
        e.setAwaitTerminationSeconds(30);
        e.initialize();
        log.info("[AsyncConfig] knowledgeIngestExecutor 启动: core=3 max=8 queue=100");
        return e;
    }
}
