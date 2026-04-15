package com.socialflow.web.filter;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 访问日志 Filter —— 每个 HTTP 请求结束后输出一条结构化日志：
 * {@code [ACCESS] method=POST uri=/api/v1/content/generate status=200 latency=1234ms userId=42}
 *
 * <p>排除路径：/actuator/** 和 OpenAPI 文档，避免淹没业务日志。</p>
 *
 * <p>userId 通过 Sa-Token 获取，未登录写 -。</p>
 *
 * <p>顺序排在 {@link MdcTraceIdFilter} 之后（同一个 traceId 会出现在 access log 和业务 log）。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger-ui")
                || uri.equals("/doc.html")
                || uri.startsWith("/webjars")
                || uri.startsWith("/favicon");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long latency = System.currentTimeMillis() - start;
            String userId;
            try {
                userId = StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginIdAsLong()) : "-";
            } catch (Exception e) {
                userId = "-";
            }
            log.info("[ACCESS] method={} uri={} status={} latency={}ms userId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    latency,
                    userId);
        }
    }
}
