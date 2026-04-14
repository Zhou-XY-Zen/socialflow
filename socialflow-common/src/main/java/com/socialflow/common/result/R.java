package com.socialflow.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一API响应包装类
 *
 * 【作用】所有后端接口返回给前端的数据，都会被包装成这个格式：
 * 
 *   {
 *     "code": 200,        // 状态码，200表示成功
 *     "message": "ok",    // 提示信息
 *     "data": { ... },    // 实际的业务数据
 *     "timestamp": 1234   // 响应时的时间戳（毫秒）
 *   }
 * 
 * 【为什么需要】前端拿到响应后，先看 code 是否为 200，
 *   如果不是就弹出 message 里的错误提示；如果是就从 data 取数据渲染页面。
 *   这样前后端有了统一的"语言"，不会因为格式不一致出bug。
 *
 * 【用法示例】
 *   成功：return R.ok(contentVO);
 *   失败：return R.fail("文案生成失败");
 *   带错误码的失败：return R.fail(ResultCode.AI_CALL_FAILED);
 *
 * 【泛型参数 T】代表 data 字段的具体类型，比如 R&lt;ContentVO&gt; 表示 data 里放的是 ContentVO 对象
 *
 * @param <T> data 字段中携带的业务数据类型
 */
@Data // Lombok注解：自动生成 getter/setter/toString/equals/hashCode 方法
@JsonInclude(JsonInclude.Include.NON_NULL) // Jackson注解：序列化为JSON时，值为null的字段不输出（减少传输数据量）
public class R<T> implements Serializable {

    /** 序列化版本号，用于Java对象序列化/反序列化时的版本兼容性校验 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 成功状态码常量，值为200，与HTTP 200含义一致 */
    public static final int CODE_SUCCESS = 200;

    /** 失败状态码常量，值为500，表示服务器内部错误 */
    public static final int CODE_FAIL = 500;

    /** 响应状态码：200=成功，其他值=失败（具体含义见 ResultCode 枚举） */
    private Integer code;

    /** 响应提示信息：成功时一般为"ok"，失败时为具体的错误描述 */
    private String message;

    /** 响应数据：成功时携带实际业务数据，失败时通常为null */
    private T data;

    /** 响应时间戳：记录服务器处理完请求的时刻（Unix毫秒时间戳），方便前端做超时判断或日志追踪 */
    private long timestamp;

    /**
     * 无参构造方法
     * 创建对象时自动记录当前时间戳
     */
    public R() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 全参构造方法
     *
     * @param code    状态码
     * @param message 提示信息
     * @param data    业务数据
     */
    public R(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis(); // 自动记录当前时间戳
    }

    /**
     * 构建一个"成功且不带数据"的响应
     * 常用于删除、更新等不需要返回具体数据的操作
     *
     * @param <T> 泛型占位符
     * @return 成功响应，data 为 null
     */
    public static <T> R<T> ok() {
        return new R<>(CODE_SUCCESS, "ok", null);
    }

    /**
     * 构建一个"成功且带数据"的响应
     * 最常用的成功响应方式
     *
     * @param data 要返回给前端的业务数据
     * @param <T>  数据的类型
     * @return 成功响应，包含数据
     */
    public static <T> R<T> ok(T data) {
        return new R<>(CODE_SUCCESS, "ok", data);
    }

    /**
     * 构建一个"成功且自定义提示信息"的响应
     *
     * @param message 自定义的成功提示信息
     * @param data    要返回给前端的业务数据
     * @param <T>     数据的类型
     * @return 成功响应，包含自定义提示和数据
     */
    public static <T> R<T> ok(String message, T data) {
        return new R<>(CODE_SUCCESS, message, data);
    }

    /**
     * 构建一个"失败"的响应（使用默认500错误码）
     *
     * @param message 错误提示信息，前端会展示给用户看
     * @param <T>     泛型占位符
     * @return 失败响应
     */
    public static <T> R<T> fail(String message) {
        return new R<>(CODE_FAIL, message, null);
    }

    /**
     * 构建一个"失败"的响应（自定义错误码）
     *
     * @param code    自定义错误码，比如 1000 表示参数错误
     * @param message 错误提示信息
     * @param <T>     泛型占位符
     * @return 失败响应
     */
    public static <T> R<T> fail(Integer code, String message) {
        return new R<>(code, message, null);
    }

    /**
     * 构建一个"失败"的响应（使用预定义的 ResultCode 枚举）
     * 推荐使用这种方式，因为错误码和提示信息已经在枚举中统一定义好了
     *
     * @param rc  预定义的结果码枚举，包含错误码和错误信息
     * @param <T> 泛型占位符
     * @return 失败响应
     */
    public static <T> R<T> fail(ResultCode rc) {
        return new R<>(rc.getCode(), rc.getMessage(), null);
    }

    /**
     * 判断本次响应是否成功
     * 前端/其他微服务拿到 R 对象后，可以调用这个方法快速判断
     *
     * @return true=成功，false=失败
     */
    public boolean isSuccess() {
        return code != null && code == CODE_SUCCESS;
    }
}
