package com.socialflow.common.exception;

import com.socialflow.common.result.ResultCode;

/**
 * AI调用异常
 *
 * 【作用】当系统调用上游的AI服务（大语言模型LLM、Embedding向量化模型、Reranker重排模型）
 *   失败时，抛出此异常。错误码固定为 2001（AI_CALL_FAILED）。
 *
 * 【常见失败原因】
 *   - 网络超时：AI服务响应时间过长
 *   - API Key 无效：密钥过期或配额用尽
 *   - 模型过载：上游服务返回 429（限流）或 503（过载）
 *   - 响应格式异常：模型返回的内容无法解析
 *
 * 【使用场景举例】
 *   - try { callOpenAI(); } catch (Exception e) { throw new AiCallException("调用OpenAI失败", e); }
 *
 * 【与其他AI相关异常的区别】
 *   - AiCallException：AI服务调用本身失败
 *   - GuardrailException：AI调用成功但被安全护栏拦截
 *   - QuotaExceededException：还没调用AI就因为配额用完被拒绝
 */
public class AiCallException extends BaseException {

    /**
     * 构造AI调用异常（不包含原始异常信息）
     *
     * @param message 错误描述，如"调用DeepSeek模型超时"
     */
    public AiCallException(String message) {
        super(ResultCode.AI_CALL_FAILED, message);
    }

    /**
     * 构造AI调用异常（包含原始异常信息，便于日志追踪根因）
     *
     * @param message 错误描述
     * @param cause   导致此异常的原始异常（如 HttpTimeoutException、IOException 等）
     */
    public AiCallException(String message, Throwable cause) {
        super(ResultCode.AI_CALL_FAILED, message);
        initCause(cause); // 将原始异常链接到当前异常，形成异常链（exception chain）
    }
}
