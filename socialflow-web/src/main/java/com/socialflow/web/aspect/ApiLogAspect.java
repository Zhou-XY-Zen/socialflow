package com.socialflow.web.aspect;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * {@link ApiLog} 注解的切面实现 —— 在 Controller 方法入口、出口、异常处插结构化日志。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>userId</b> 通过 Sa-Token 取，未登录时记 {@code anon}（避免在公开接口炸异常）</li>
 *   <li><b>参数摘要</b> 用 {@code String.valueOf} 单参数转字符串后整体截断到 200 字符
 *       —— 既能看到关键字段，又不会被超长 prompt / RAG context 撑爆日志</li>
 *   <li><b>响应式返回</b>（{@link Flux} / {@link Mono}）必须用 {@code doOnComplete} /
 *       {@code doOnError} 包装才能拿到真实完成时刻；否则切面只能看到"订阅已建立"，
 *       SSE 端点会立刻打 exit，毫无价值</li>
 *   <li><b>traceId</b> 不显式打 —— 由 {@link com.socialflow.web.filter.MdcTraceIdFilter}
 *       注入到 MDC，logback pattern 已配置 {@code %X{traceId:-}} 自动带出</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class ApiLogAspect {

    /** 单条参数转 String 后保留的最大字符数，超出截断并加 ellipsis */
    private static final int MAX_ARG_CHARS = 200;

    @Around("@annotation(apiLog)")
    public Object around(ProceedingJoinPoint pjp, ApiLog apiLog) throws Throwable {
        String op = apiLog.value();
        String userId = currentUserIdOrAnon();
        String method = ((MethodSignature) pjp.getSignature()).getMethod().getName();
        String args = apiLog.logArgs() ? summarizeArgs(pjp.getArgs()) : "<omitted>";

        log.info("{} 开始 method={} userId={} args={}", op, method, userId, args);
        long startNanos = System.nanoTime();

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.warn("{} 失败 method={} userId={} durationMs={} errType={} errMsg={}",
                    op, method, userId, durationMs,
                    ex.getClass().getSimpleName(), trunc(ex.getMessage(), 200));
            throw ex;
        }

        // 同步返回：直接打完成日志
        if (!(result instanceof Flux<?>) && !(result instanceof Mono<?>)) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("{} 完成 method={} userId={} durationMs={} resultType={}",
                    op, method, userId, durationMs, simpleTypeName(result));
            return result;
        }

        // 响应式返回：在终止信号上才打日志
        if (result instanceof Mono<?> mono) {
            return mono
                    .doOnSuccess(v -> log.info("{} 完成 method={} userId={} durationMs={} resultType={}",
                            op, method, userId, elapsedMs(startNanos), simpleTypeName(v)))
                    .doOnError(err -> log.warn("{} 失败 method={} userId={} durationMs={} errType={} errMsg={}",
                            op, method, userId, elapsedMs(startNanos),
                            err.getClass().getSimpleName(), trunc(err.getMessage(), 200)));
        }

        // Flux：可能是 SSE 流，统计 token 数
        Flux<?> flux = (Flux<?>) result;
        AtomicLong tokenCount = new AtomicLong();
        return flux
                .doOnNext(v -> tokenCount.incrementAndGet())
                .doOnComplete(() -> log.info("{} 完成 method={} userId={} durationMs={} streamTokens={}",
                        op, method, userId, elapsedMs(startNanos), tokenCount.get()))
                .doOnError(err -> log.warn("{} 失败 method={} userId={} durationMs={} streamTokens={} errType={} errMsg={}",
                        op, method, userId, elapsedMs(startNanos), tokenCount.get(),
                        err.getClass().getSimpleName(), trunc(err.getMessage(), 200)));
    }

    private static String currentUserIdOrAnon() {
        try {
            return StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginIdAsLong()) : "anon";
        } catch (Exception e) {
            return "anon";
        }
    }

    private static String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return Arrays.stream(args)
                .map(a -> trunc(String.valueOf(a), MAX_ARG_CHARS))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String trunc(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "…(" + s.length() + ")";
    }

    private static String simpleTypeName(Object v) {
        return v == null ? "null" : v.getClass().getSimpleName();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
