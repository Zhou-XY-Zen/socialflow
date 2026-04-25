package com.socialflow.web.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要在执行前后输出业务级日志的 Controller 方法。
 *
 * <p>{@link com.socialflow.web.filter.AccessLogFilter} 已经记录每条 HTTP 请求的
 * method/uri/status/latency，但缺少业务上下文（哪个用户、传了什么 DTO、返回了什么）。
 * 加 {@link ApiLog} 后，{@link ApiLogAspect} 会在方法入口、出口、异常时各打一条
 * 带有同 traceId 的结构化日志，配合 ELK 时可直接按 op 聚合排查问题。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 *     @ApiLog("[内容生成]")
 *     @PostMapping("/generate")
 *     public R<ContentVO> generate(@Valid @RequestBody ContentGenerateDTO dto) {
 *         return R.ok(contentService.generate(StpUtil.getLoginIdAsLong(), dto));
 *     }
 * }</pre>
 *
 * <p>对返回 {@link reactor.core.publisher.Flux} 的 SSE 端点：切面会在订阅时打 entry，
 * 在 onComplete/onError 时打 exit/error，因此不会在流刚启动就误报"完成"。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiLog {

    /**
     * 业务操作标签，用作日志前缀，便于按业务维度过滤。
     * 推荐使用方括号包裹的中文描述，例如 {@code "[内容生成]"}、{@code "[发布任务]"}。
     */
    String value();

    /**
     * 是否在日志中打印方法参数摘要（截断后）。
     * 涉及大段文本（如 SSE 输入的长 Prompt）或敏感信息（API Key）时建议关闭。
     * 默认开启。
     */
    boolean logArgs() default true;
}
