package com.socialflow.common.exception;

import com.socialflow.common.result.ResultCode;

/**
 * 通用业务异常
 *
 * 【作用】当业务逻辑出错但又不属于参数错误、资源不存在、AI调用失败等
 *   特定分类时，就抛出这个异常。它是一个"兜底"的业务异常类。
 *
 * 【使用场景举例】
 *   - 业务规则校验失败：throw new BusinessException("同一时间段只能预约一篇文案");
 *   - 状态不允许的操作：throw new BusinessException("已发布的文案不能再编辑");
 *   - 使用预定义错误码：throw new BusinessException(ResultCode.DATA_DUPLICATED);
 *
 * 【全局异常处理】全局异常处理器（ControllerAdvice）会捕获此异常，
 *   取出 code 和 message 包装成 R 对象返回给前端。
 */
public class BusinessException extends BaseException {

    /**
     * 使用默认错误码 500 和自定义错误信息构造异常
     * 这是最常用的构造方式
     *
     * @param message 错误描述信息，会展示给前端用户
     */
    public BusinessException(String message) {
        super(500, message);
    }

    /**
     * 使用自定义错误码和错误信息构造异常
     *
     * @param code    自定义错误码
     * @param message 错误描述信息
     */
    public BusinessException(Integer code, String message) {
        super(code, message);
    }

    /**
     * 使用预定义的 ResultCode 枚举构造异常
     * 错误码和信息都从枚举中取
     *
     * @param rc 预定义结果码枚举
     */
    public BusinessException(ResultCode rc) {
        super(rc);
    }

    /**
     * 使用 ResultCode 的错误码，但自定义错误信息
     *
     * @param rc      预定义结果码枚举（取其错误码）
     * @param message 自定义的错误描述信息
     */
    public BusinessException(ResultCode rc, String message) {
        super(rc, message);
    }
}
