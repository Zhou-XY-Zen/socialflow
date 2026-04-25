package com.socialflow.web.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link ApiLogAspect} 的单元测试 —— 直接调 around 方法，不启动 Spring AOP。
 *
 * <p>覆盖点：</p>
 * <ul>
 *   <li>同步返回：proceed 结果直接透传</li>
 *   <li>同步抛异常：异常透传（不被吞）</li>
 *   <li>Mono 返回：用 doOnSuccess/doOnError 装饰，业务值不变</li>
 *   <li>Flux 返回：用 doOnNext/doOnComplete/doOnError 装饰，全部 token 透传</li>
 *   <li>logArgs=false 时不暴露入参</li>
 *   <li>未登录场景 currentUserIdOrAnon 不抛</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiLogAspect")
class ApiLogAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    /** 构造一个 @ApiLog 注解实例（用反射上的代理替代复杂的 mock） */
    private ApiLog apiLog(String value, boolean logArgs) {
        return new ApiLog() {
            @Override public Class<? extends Annotation> annotationType() { return ApiLog.class; }
            @Override public String value() { return value; }
            @Override public boolean logArgs() { return logArgs; }
        };
    }

    /** 用一个真实的（trivial）方法来填充 MethodSignature.getMethod() */
    private static Method dummyMethod() throws NoSuchMethodException {
        return ApiLogAspectTest.class.getDeclaredMethod("dummyTarget");
    }
    @SuppressWarnings("unused")
    private void dummyTarget() {}

    private ApiLogAspect aspect() {
        return new ApiLogAspect();
    }

    @Test
    @DisplayName("同步方法：proceed 结果直接透传")
    void syncReturn_passesThrough() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(dummyMethod());
        when(joinPoint.getArgs()).thenReturn(new Object[]{"hello"});
        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect().around(joinPoint, apiLog("[测试]", true));

        assertThat(result).isEqualTo("OK");
    }

    @Test
    @DisplayName("同步方法抛异常：异常向上传递不吞")
    void syncException_propagates() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(dummyMethod());
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        RuntimeException boom = new RuntimeException("业务错");
        when(joinPoint.proceed()).thenThrow(boom);

        assertThatThrownBy(() -> aspect().around(joinPoint, apiLog("[测试]", true)))
                .isSameAs(boom);
    }

    @Test
    @DisplayName("Mono 返回：业务值原样透传")
    void monoReturn_passesValue() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(dummyMethod());
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenReturn(Mono.just("mono-result"));

        Object result = aspect().around(joinPoint, apiLog("[测试]", true));

        assertThat(result).isInstanceOf(Mono.class);
        assertThat(((Mono<?>) result).block()).isEqualTo("mono-result");
    }

    @Test
    @DisplayName("Mono 错误：错误信号不被吞，传给订阅方")
    void monoError_propagates() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(dummyMethod());
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenReturn(Mono.error(new IllegalStateException("mono fail")));

        Object result = aspect().around(joinPoint, apiLog("[测试]", true));

        assertThatThrownBy(() -> ((Mono<?>) result).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mono fail");
    }

    @Test
    @DisplayName("Flux 返回：所有 token 原样透传")
    void fluxReturn_allTokensPass() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(dummyMethod());
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenReturn(Flux.just("a", "b", "c"));

        Object result = aspect().around(joinPoint, apiLog("[流式]", true));

        assertThat(result).isInstanceOf(Flux.class);
        @SuppressWarnings("unchecked")
        List<Object> collected = (List<Object>) ((Flux<?>) result).collectList().block();
        assertThat(collected).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("Flux 出错：错误传给订阅方且不被静默吞")
    void fluxError_propagates() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(dummyMethod());
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenReturn(Flux.concat(
                Flux.just("first"),
                Flux.error(new RuntimeException("stream broke"))));

        Object result = aspect().around(joinPoint, apiLog("[流式]", true));

        // collectList 上抛业务异常，证明错误信号没被吞
        assertThatThrownBy(() -> ((Flux<?>) result).collectList().block())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("stream broke");
    }

    @Test
    @DisplayName("logArgs=false 时也能正常透传业务结果（仅日志路径不同）")
    void logArgsDisabled_stillWorks() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(dummyMethod());
        // logArgs=false 时切面不调 getArgs，但调了也无害
        when(joinPoint.proceed()).thenReturn("hidden-args-ok");

        Object result = aspect().around(joinPoint, apiLog("[隐参]", false));

        assertThat(result).isEqualTo("hidden-args-ok");
    }
}
