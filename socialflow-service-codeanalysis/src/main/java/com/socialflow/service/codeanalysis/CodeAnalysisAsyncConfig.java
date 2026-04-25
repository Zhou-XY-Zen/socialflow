package com.socialflow.service.codeanalysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 代码分析模块专属的异步线程池配置。
 *
 * <p>从 service-core 的 {@code AsyncConfig} 中拆出来——之前这两个池子
 * 定义在 service-core，导致 service-codeanalysis 不能独立部署（漏线程池）。
 * 现在每个模块管自己的池子，谁用 @Async("xxx") 谁定义。</p>
 *
 * <p>{@code @EnableAsync} 已在 {@code SocialFlowApplication} 全局开启，本类仅注册 Bean，
 * 无需重复声明。</p>
 *
 * <p><b>为什么用两个池子（codeAnalysis + moduleSummary）而不是一个：</b></p>
 * <ul>
 *   <li>父任务（runProjectOverview）跑在 codeAnalysisExecutor</li>
 *   <li>父任务内部 fork 子任务（summarizeOneModule）跑 moduleSummaryExecutor</li>
 *   <li>共用一个池会导致"父任务持线程等子任务、子任务在队列里等线程"的嵌套死锁</li>
 * </ul>
 */
@Slf4j
@Configuration
public class CodeAnalysisAsyncConfig {

    /**
     * 代码分析主任务线程池。单次分析耗时几分钟，core=2 避免闲置；max=4 限并发分析数；
     * queue=50 容纳突发；CALLER_RUNS 保证不丢任务。
     */
    @Bean("codeAnalysisExecutor")
    public Executor codeAnalysisExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(4);
        e.setQueueCapacity(50);
        e.setKeepAliveSeconds(60);
        e.setThreadNamePrefix("code-analysis-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        e.setWaitForTasksToCompleteOnShutdown(true);
        e.setAwaitTerminationSeconds(30);
        e.initialize();
        log.info("[CodeAnalysisAsyncConfig] codeAnalysisExecutor 启动: core=2 max=4 queue=50");
        return e;
    }

    /**
     * 模块摘要并发池 —— 项目概览阶段并行给各模块做摘要。
     *
     * <p>并发度 core=8 / max=10 的理由：</p>
     * <ul>
     *   <li>DeepSeek 单账户 RPM 500+，30-50 并发在限流阈值内</li>
     *   <li>LLM 调用是纯 I/O 等待，CPU/内存影响小（每并发约 20MB）</li>
     *   <li>典型项目 5-15 个模块，10 通道几乎所有模块同时起跑</li>
     *   <li>加速曲线甜点：8-10（5.6x-6.5x），16 收益开始衰减</li>
     * </ul>
     */
    @Bean("moduleSummaryExecutor")
    public Executor moduleSummaryExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(8);
        e.setMaxPoolSize(10);
        e.setQueueCapacity(50);
        e.setKeepAliveSeconds(120);
        e.setThreadNamePrefix("module-summary-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        e.setWaitForTasksToCompleteOnShutdown(true);
        e.setAwaitTerminationSeconds(60);
        e.initialize();
        log.info("[CodeAnalysisAsyncConfig] moduleSummaryExecutor 启动: core=8 max=10 queue=50");
        return e;
    }
}
