package com.socialflow.common.exception;

import com.socialflow.common.result.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * SocialFlow 所有业务异常的基类（根异常）
 *
 * 【作用】作为系统中所有自定义异常的"老祖宗"，定义了异常必须携带的信息：
 *   - code（错误码）：告诉调用方出了哪种类型的错误
 *   - message（错误信息）：继承自 RuntimeException，描述具体的错误原因
 *
 * 【为什么继承 RuntimeException 而不是 Exception】
 *   RuntimeException 是"非受检异常"，不需要在方法签名上声明 throws，
 *   代码更简洁。业务异常一般由全局异常处理器统一捕获，不需要逐层 try-catch。
 *
 * 【继承关系】
 *   BaseException
 *     ├── BusinessException    （通用业务异常）
 *     ├── ParamException       （参数异常）
 *     ├── NotFoundException    （资源不存在异常）
 *     ├── AiCallException      （AI调用异常）
 *     ├── GuardrailException   （护栏拦截异常）
 *     └── QuotaExceededException（配额超限异常）
 *
 * 【使用场景】一般不直接使用 BaseException，而是使用它的子类。
 *   全局异常处理器会捕获 BaseException 并把 code 和 message 包装成 R 对象返回给前端。
 */
@Getter // Lombok注解：自动为 code 字段生成 getter 方法
public class BaseException extends RuntimeException {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 错误码，与 ResultCode 中定义的错误码对应，全局异常处理器会把它放进 R.code 中返回 */
    private final Integer code;

    /**
     * 通过错误码和错误信息构造异常
     *
     * @param code    错误码（如 500、1000、2001 等）
     * @param message 错误描述信息
     */
    public BaseException(Integer code, String message) {
        super(message); // 调用父类 RuntimeException 的构造方法，设置 message
        this.code = code;
    }

    /**
     * 通过预定义的 ResultCode 枚举构造异常
     * 错误码和错误信息都从枚举中获取
     *
     * @param rc 预定义结果码枚举
     */
    public BaseException(ResultCode rc) {
        super(rc.getMessage()); // 使用枚举中定义的错误信息
        this.code = rc.getCode(); // 使用枚举中定义的错误码
    }

    /**
     * 通过 ResultCode 获取错误码，但使用自定义的错误信息
     * 适用于同一类错误但需要更具体描述的场景
     * 例如：new BaseException(ResultCode.NOT_FOUND, "ID为123的文案不存在")
     *
     * @param rc      预定义结果码枚举（取其错误码）
     * @param message 自定义的错误描述信息
     */
    public BaseException(ResultCode rc, String message) {
        super(message); // 使用自定义的错误信息
        this.code = rc.getCode(); // 从枚举中取错误码
    }
}
