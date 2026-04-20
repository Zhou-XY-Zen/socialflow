package com.socialflow.common.exception;

import java.util.Locale;

/**
 * LLM 调用错误分类枚举 —— 把"{@code catch Exception}"里一锅端的错误细化，
 * 便于告警规则（限流/超时/认证错分别告不同接收人）、仪表盘统计和重试策略。
 *
 * 【使用方式】
 *   1. {@link #classify(Throwable)} 基于异常类名 + message 启发式判定
 *   2. {@link #tag()} 返回形如 {@code "[RATE_LIMIT]"} 的前缀，写入 llm_call_log.error_msg
 *      便于后续按前缀 SQL 聚合统计
 */
public enum LlmErrorKind {

    /** 网络连接失败：DNS / TCP 拒绝 / 对端 reset */
    NETWORK,
    /** 读写超时 */
    TIMEOUT,
    /** 被上游限流（429） */
    RATE_LIMIT,
    /** 认证失败（401/403）—— 密钥无效、过期、额度用尽 */
    AUTH,
    /** 上游 5xx / 模型过载 / 内部错误 */
    SERVER,
    /** 模型返回的 JSON 格式无效、被 max_tokens 截断等应用层问题 */
    INVALID_RESPONSE,
    /** 无法识别的其他错误 */
    UNKNOWN;

    /** 日志前缀，形如 {@code "[TIMEOUT]"} */
    public String tag() {
        return "[" + name() + "]";
    }

    /**
     * 根据异常类型 + message 启发式判定；尽力做最常见的几类识别，其余归 UNKNOWN。
     * 不依赖具体 LLM SDK，走字符串模式匹配，简单鲁棒。
     */
    public static LlmErrorKind classify(Throwable t) {
        if (t == null) return UNKNOWN;
        String cls = t.getClass().getSimpleName();
        String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);

        // 1) 按异常类名判定常见类型（Java 标准异常）
        if (cls.contains("Timeout")) return TIMEOUT;
        if (cls.contains("ConnectException") || cls.contains("UnknownHost")
                || cls.contains("NoRouteToHost") || cls.contains("SocketException")) return NETWORK;

        // 2) 按 message 中的关键词 / HTTP 状态码判定
        if (msg.contains("timeout") || msg.contains("timed out")) return TIMEOUT;
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("too many requests"))
            return RATE_LIMIT;
        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized")
                || msg.contains("invalid api key") || msg.contains("forbidden")) return AUTH;
        if (msg.contains("connection reset") || msg.contains("connection refused")
                || msg.contains("unknown host") || msg.contains("network")) return NETWORK;
        if (msg.matches(".*\\b5\\d{2}\\b.*") || msg.contains("server error")
                || msg.contains("overloaded") || msg.contains("internal error")) return SERVER;
        if (msg.contains("json") || msg.contains("parse") || msg.contains("invalid response"))
            return INVALID_RESPONSE;
        return UNKNOWN;
    }
}
