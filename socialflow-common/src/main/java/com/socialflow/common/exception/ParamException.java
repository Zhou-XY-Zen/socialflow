package com.socialflow.common.exception;

import com.socialflow.common.result.ResultCode;

/**
 * 参数异常（请求参数不合法）
 *
 * 【作用】当前端传过来的请求参数不满足校验规则时，抛出此异常。
 *   错误码固定为 400（与 HTTP 400 Bad Request 含义一致）。
 *
 * 【使用场景举例】
 *   - 必填参数为空：throw new ParamException("文案标题不能为空");
 *   - 参数格式错误：throw new ParamException("手机号格式不正确");
 *   - 参数值超出范围：throw new ParamException("每页条数不能超过100");
 *
 * 【与 @Valid 注解的关系】
 *   @Valid 注解的自动校验失败会由框架层处理，
 *   ParamException 用于在业务代码中手动校验参数时抛出。
 */
public class ParamException extends BaseException {

    /**
     * 构造参数异常
     * 错误码自动设为 400（BAD_REQUEST）
     *
     * @param message 具体的参数错误描述，如"文案标题不能为空"
     */
    public ParamException(String message) {
        super(ResultCode.BAD_REQUEST.getCode(), message);
    }
}
