package com.socialflow.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS（跨域资源共享）配置类 —— 允许前端应用跨域访问后端 API。
 *
 * 什么是 CORS？
 * 浏览器有一个安全策略叫"同源策略"（Same-Origin Policy），它规定网页只能访问
 * 与自己同源（协议 + 域名 + 端口都相同）的资源。例如：
 *     - 前端运行在 http://localhost:3000
 *     - 后端运行在 http://localhost:8080
 * 端口不同，属于"跨域"请求，浏览器默认会阻止。
 *
 * 为什么需要 CORS 配置？
 * 本项目采用前后端分离架构：前端（Vue/React）和后端（Spring Boot）分开部署，
 * 运行在不同的端口甚至不同的服务器上。如果不配置 CORS，前端的 AJAX 请求
 * 会被浏览器拦截，导致无法正常调用后端 API。
 *
 * 通过 CORS 配置，后端告诉浏览器"我允许这些来源的请求访问我的资源"，
 * 浏览器收到后端的许可后，就会放行跨域请求。
 */
/*
 * @Configuration —— 标记这是一个 Spring 配置类，等价于一个 XML 配置文件
 *                   Spring 启动时会自动加载此类中的配置
 */
@Configuration
public class CorsConfig {

    /**
     * 创建 CORS 过滤器 Bean，注册到 Spring 容器中。
     *
     * 这个过滤器会拦截所有 HTTP 请求，在响应头中添加 CORS 相关的 Header，
     * 告诉浏览器允许跨域访问。
     *
     * 配置说明：
     *     - {@code addAllowedOriginPattern("*")} —— 允许任意来源（域名）的请求。
     *       生产环境建议改为具体的前端域名，如 "https://www.example.com"
     *     - {@code addAllowedHeader("*")} —— 允许请求携带任意 Header
     *       （如 Authorization、Content-Type 等）
     *     - {@code addAllowedMethod("*")} —— 允许所有 HTTP 方法
     *       （GET、POST、PUT、DELETE 等）
     *     - {@code setAllowCredentials(true)} —— 允许请求携带凭证信息
     *       （如 Cookie、Authorization Header）。前端使用 Token 认证时需要开启此项
     *     - {@code setMaxAge(3600L)} —— 预检请求（OPTIONS）的缓存时间为 3600 秒（1 小时）。
     *       浏览器在发送跨域请求前会先发一个 OPTIONS 预检请求，
     *       设置缓存时间可以减少预检请求的次数，提升性能
     *
     * {@code "/**"} 表示此 CORS 配置应用于所有 URL 路径。
     *
     * @return 配置好的 CorsFilter 实例
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.addAllowedOriginPattern("*");
        cfg.addAllowedHeader("*");
        cfg.addAllowedMethod("*");
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(source);
    }
}
