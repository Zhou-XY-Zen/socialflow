package com.socialflow.common.exception;

import com.socialflow.common.result.ResultCode;

/**
 * 资源不存在异常
 *
 * 【作用】当请求的资源（数据库记录、文件等）不存在时，抛出此异常。
 *   错误码固定为 404（与 HTTP 404 Not Found 含义一致）。
 *
 * 【使用场景举例】
 *   - 根据ID查询文案为空：throw new NotFoundException("ID为123的文案不存在");
 *   - 知识库已被删除：throw new NotFoundException("该知识库已被删除");
 *   - 用户不存在：throw new NotFoundException("用户不存在");
 *
 * 【注意】与数据库层的"查询返回空"不同，这个异常表示
 *   "该资源本应存在但找不到"，属于一种业务错误。
 */
public class NotFoundException extends BaseException {

    /**
     * 构造资源不存在异常
     * 错误码自动设为 404（NOT_FOUND）
     *
     * @param message 具体的资源不存在描述，如"ID为123的文案不存在"
     */
    public NotFoundException(String message) {
        super(ResultCode.NOT_FOUND.getCode(), message);
    }
}
