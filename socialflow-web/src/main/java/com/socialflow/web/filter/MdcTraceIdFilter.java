package com.socialflow.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 在每个请求开始时往 MDC 写入 traceId，并通过响应头 {@code X-Trace-Id} 回传给前端。
 *
 * <p>Logback pattern 里的 {@code %X{traceId:-}} 占位符会自动取出当前线程的 traceId，
 * 从而把同一请求链路上所有日志（controller / service / WebClient 回调）串起来。</p>
 *
 * <p>支持上游传入的 traceId（如网关已经生成）：若请求头里有 {@code X-Trace-Id}，
 * 直接复用，否则生成新的 UUID（无连字符，便于复制）。</p>
 *
 * <p>注意：必须在 finally 中清理 MDC，否则线程被复用时会污染下个请求。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcTraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
