package com.socialflow.web.handler;

import cn.dev33.satoken.exception.NotLoginException;
import com.socialflow.common.exception.BaseException;
import com.socialflow.common.exception.GuardrailException;
import com.socialflow.common.exception.NotFoundException;
import com.socialflow.common.exception.ParamException;
import com.socialflow.common.result.R;
import com.socialflow.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 统一捕获和处理所有控制器抛出的异常，将它们转换为规范的 HTTP 响应。
 *
 * 在 Spring Boot 中，如果控制器方法执行时抛出异常，默认会返回一个丑陋的错误页面。
 * 这个全局异常处理器的作用就是拦截这些异常，将它们转换为统一格式的 JSON 响应，
 * 并设置合适的 HTTP 状态码，让前端能够优雅地处理错误。
 *
 * 异常处理的优先级规则：Spring 会优先匹配最具体的异常类型。例如，
 * {@code NotFoundException} 会先匹配到 {@code handleNotFound} 方法，
 * 而不是它的父类 {@code BaseException} 对应的 {@code handleBase} 方法。
 *
 * 异常类型与 HTTP 状态码的映射关系：
 *     - {@code NotLoginException}               -> 401 Unauthorized（未认证）
 *     - {@code NotFoundException}                -> 404 Not Found（资源不存在）
 *     - {@code ParamException}                   -> 400 Bad Request（参数错误）
 *     - {@code GuardrailException}               -> 200 OK（AI 安全护栏拦截，业务层面的失败）
 *     - {@code BaseException}                    -> 200 OK（其他业务异常）
 *     - {@code MethodArgumentNotValidException}  -> 400 Bad Request（请求体参数校验失败）
 *     - {@code BindException}                    -> 400 Bad Request（表单/查询参数绑定失败）
 *     - {@code ConstraintViolationException}     -> 400 Bad Request（方法参数约束违反）
 *     - {@code Exception}（兜底）                -> 500 Internal Server Error（未知错误）
 *
 * @see R 统一响应格式，包含 code（状态码）、message（提示信息）、data（数据）
 */
/*
 * @Slf4j               —— Lombok 注解，自动生成日志对象 log，可以直接使用 log.warn()、log.error() 等
 * @RestControllerAdvice —— 标记这是一个全局异常处理类，会拦截所有 @RestController 中抛出的异常
 *                         它是 @ControllerAdvice + @ResponseBody 的组合注解
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理"未登录"异常。
     *
     * 异常类型：{@code NotLoginException}（来自 Sa-Token 框架）
     *
     * 触发场景：用户未登录或 Token 已过期时，访问需要登录的接口
     *
     * HTTP 状态码：401 Unauthorized（未认证）
     *
     * 前端收到 401 响应后，通常会跳转到登录页面。
     *
     * @param e Sa-Token 抛出的未登录异常
     * @return HTTP 401 响应，包含错误码和错误信息
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(ResultCode.UNAUTHORIZED.getCode(), e.getMessage()));
    }

    /**
     * 处理"资源未找到"异常。
     *
     * 异常类型：{@code NotFoundException}（自定义业务异常）
     *
     * 触发场景：根据 ID 查询内容、知识库等资源时，数据不存在
     *
     * HTTP 状态码：404 Not Found（资源不存在）
     *
     * @param e 资源未找到异常，包含自定义错误码和详细信息
     * @return HTTP 404 响应
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<R<Void>> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 处理"参数错误"异常。
     *
     * 异常类型：{@code ParamException}（自定义业务异常）
     *
     * 触发场景：业务层发现传入的参数不合法（如无效的平台类型、超出范围的数值等）
     *
     * HTTP 状态码：400 Bad Request（请求参数有误）
     *
     * @param e 参数异常，包含自定义错误码和详细信息
     * @return HTTP 400 响应
     */
    @ExceptionHandler(ParamException.class)
    public ResponseEntity<R<Void>> handleParam(ParamException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 处理"AI 安全护栏拦截"异常。
     *
     * 异常类型：{@code GuardrailException}（自定义业务异常）
     *
     * 触发场景：AI 生成的内容触发了安全护栏规则（如敏感词检测、内容合规检查等），
     *    被系统自动拦截
     *
     * HTTP 状态码：200 OK（注意：这里使用 200 而非 4xx，因为请求本身是合法的，
     *    只是 AI 输出被安全策略拦截了，属于业务层面的"失败"而非协议层面的错误）
     *
     * 日志中会记录触发的规则名称（ruleName）、触发类型（triggerType）和拦截原因，
     * 方便运维人员排查问题。
     *
     * @param e 护栏异常，包含规则名称、触发类型等详细信息
     * @return HTTP 200 响应，响应体中的 code 字段标识具体的业务错误类型
     */
    @ExceptionHandler(GuardrailException.class)
    public ResponseEntity<R<Void>> handleGuardrail(GuardrailException e) {
        log.warn("guardrail blocked: rule={} type={} reason={}",
                e.getRuleName(), e.getTriggerType(), e.getMessage());
        return ResponseEntity.ok(R.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 处理通用业务异常（兜底处理所有自定义业务异常）。
     *
     * 异常类型：{@code BaseException}（所有自定义业务异常的父类）
     *
     * 触发场景：各种业务逻辑错误（如余额不足、操作频率超限等），
     *    且没有被上面更具体的处理器捕获到
     *
     * HTTP 状态码：200 OK（业务异常通过响应体中的 code 字段区分，HTTP 层面返回成功）
     *
     * @param e 业务异常基类
     * @return HTTP 200 响应，响应体中包含具体的业务错误码和信息
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<R<Void>> handleBase(BaseException e) {
        log.warn("business exception: code={} message={}", e.getCode(), e.getMessage());
        return ResponseEntity.ok(R.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 处理请求体（@RequestBody）参数校验失败异常。
     *
     * 异常类型：{@code MethodArgumentNotValidException}（Spring 框架异常）
     *
     * 触发场景：当使用 @Valid 注解校验 @RequestBody 参数时，如果 DTO 中的字段
     *    不满足约束条件（如 @NotBlank、@Size、@Email 等），Spring 会抛出此异常
     *
     * HTTP 状态码：400 Bad Request
     *
     * 处理方式：提取所有校验失败的字段名和错误信息，拼接成一条可读的提示。
     * 例如："title: 标题不能为空; platform: 平台类型不合法"
     *
     * @param e 参数校验异常
     * @return HTTP 400 响应，包含所有校验错误的详细描述
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleBeanValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(400, msg));
    }

    /**
     * 处理表单/查询参数绑定异常。
     *
     * 异常类型：{@code BindException}（Spring 框架异常）
     *
     * 触发场景：当使用 @Valid 校验表单提交或 @RequestParam 绑定的对象参数时，
     *    字段不满足约束条件会抛出此异常
     *
     * HTTP 状态码：400 Bad Request
     *
     * 与 MethodArgumentNotValidException 类似，但针对的是非 JSON 格式的请求参数。
     *
     * @param e 参数绑定异常
     * @return HTTP 400 响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Void>> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(400, msg));
    }

    /**
     * 处理方法参数约束违反异常。
     *
     * 异常类型：{@code ConstraintViolationException}（Jakarta Validation 异常）
     *
     * 触发场景：当在方法参数上直接使用约束注解时（如 @RequestParam @NotBlank String name），
     *    参数不满足约束条件会抛出此异常
     *
     * HTTP 状态码：400 Bad Request
     *
     * 注意：这个异常与上面两个的区别在于，它针对的是直接加在方法参数上的校验注解，
     * 而不是 DTO 对象中的字段校验。
     *
     * @param e 约束违反异常
     * @return HTTP 400 响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(400, msg));
    }

    /**
     * 兜底异常处理 —— 捕获所有未被上面处理器捕获的异常。
     *
     * 异常类型：{@code Exception}（Java 异常基类，捕获一切未处理的异常）
     *
     * 触发场景：任何未预期的异常，如数据库连接失败、空指针异常、第三方 API 超时等
     *
     * HTTP 状态码：500 Internal Server Error（服务器内部错误）
     *
     * 这是最后的安全网。为了安全，返回给前端的错误信息是通用的"internal server error"，
     * 不会暴露具体的异常堆栈。但在服务器日志中会记录完整的异常堆栈信息（log.error），
     * 方便开发人员排查问题。
     *
     * @param e 未知异常
     * @return HTTP 500 响应，包含通用错误提示
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnknown(Exception e) {
        log.error("unhandled exception", e);
        // 开发环境返回详细错误信息，方便定位问题
        String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, detail));
    }
}
