package com.socialflow.service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * 根据《阿里巴巴 Java 开发手册（黄山版）》1.6.1：
 *   【强制】线程资源必须通过线程池提供，不允许在应用中自行显式创建线程；
 *   线程池不允许使用 Executors 去创建，而是通过 ThreadPoolExecutor 的方式，
 *   这样的处理方式让写的同学更加明确线程池的运行规则，规避资源耗尽的风险。
 *
 * 本类为 @Async 方法提供**按业务域隔离的线程池**，避免所有异步任务共享一个池子
 * 互相阻塞（例如代码分析的长耗时任务把知识库入库的短任务卡住）。
 *
 * Bean 命名约定：xxxExecutor，使用时 @Async("xxxExecutor") 显式指定。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 代码分析专用线程池。分析单次耗时可达几分钟，队列要足够容纳等待任务。
     * core=2 避免闲置资源浪费；max=4 控制并发分析数（LLM 调用本身就限流）；
     * queue=50 足够容纳短时突发的触发；CALLER_RUNS 保证不丢任务。
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
        log.info("[AsyncConfig] codeAnalysisExecutor 启动: core=2 max=4 queue=50");
        return e;
    }

    /**
     * 模块摘要并发线程池 —— 专供项目概览阶段并行调用 LLM 给各模块做摘要。
     *
     * 设计：
     *   一个 analysisId 任务进入 codeAnalysisExecutor 后，在 runProjectOverview 内部
     *   会 fork 出 N 个子任务并行 summarizeOneModule，这些子任务走这个专用池。
     *   如果共用 codeAnalysisExecutor（max=4），几个用户同时分析时会互相抢线程，
     *   甚至出现"父任务持有 codeAnalysisExecutor 线程等子任务，子任务在队列里等
     *   codeAnalysisExecutor 线程"的死锁。独立池避免这种嵌套池依赖。
     *
     * 并发度 core=8 / max=10 的理由（不是 4）：
     *   - DeepSeek 每账户 RPM 500+，同时在途 30-50 并发都在限流阈值内
     *   - LLM 调用是纯 I/O 等，服务器 CPU/内存几乎不受影响（每并发约 20MB）
     *   - 典型项目 5-15 个模块，10 个通道 ≈ 所有模块几乎同时起跑
     *   - 加速曲线：并发 4 → 3.4x，8 → 5.6x，10 → 6.5x，16 → 8.5x；8-10 是"加速显著且收益未衰减"的甜点
     *   - max=10 给高峰弹性（两个用户同时分析不会互相饿死），queue=50 兜底
     */
    @Bean("moduleSummaryExecutor")
    public Executor moduleSummaryExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(8);
        e.setMaxPoolSize(10);
        e.setQueueCapacity(50);
        e.setKeepAliveSeconds(120);
        e.setThreadNamePrefix("module-summary-");
        // CALLER_RUNS 防止任务被丢；父任务已经 async，阻塞一小会让它降速即可
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        e.setWaitForTasksToCompleteOnShutdown(true);
        e.setAwaitTerminationSeconds(60);
        e.initialize();
        log.info("[AsyncConfig] moduleSummaryExecutor 启动: core=8 max=10 queue=50");
        return e;
    }

    /**
     * 知识库入库专用线程池。短耗时、偏 I/O，但并发可能较高。
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
