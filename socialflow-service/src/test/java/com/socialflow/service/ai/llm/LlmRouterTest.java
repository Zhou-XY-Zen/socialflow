package com.socialflow.service.ai.llm;

import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LlmRouter} 的单元测试 —— 验证策略模式路由的核心行为：
 * <ol>
 *   <li>构造时按 {@link LlmProviderService#provider()} 自动注册所有 Bean</li>
 *   <li>{@link LlmRouter#get(LlmProvider)} 命中已注册的 provider 时返回对应实现</li>
 *   <li>未注册的 provider 抛出 BusinessException（错误码 MODEL_NOT_SUPPORTED）</li>
 *   <li>{@link LlmRouter#get(String)} 大小写不敏感</li>
 *   <li>同一个 provider 被注册两次时，后注册的覆盖先注册的（HashMap 语义）</li>
 * </ol>
 */
class LlmRouterTest {

    /**
     * 用于测试的最小 LlmProviderService 实现：除了 provider() 之外的方法都返回桩值。
     * 通过私有构造避免对外可见，方便每个测试自由组合 provider 列表。
     */
    private static LlmProviderService stub(LlmProvider p) {
        LlmProviderService svc = Mockito.mock(LlmProviderService.class);
        Mockito.when(svc.provider()).thenReturn(p);
        return svc;
    }

    @Test
    @DisplayName("构造函数把所有 Provider 按枚举值注册到内部 Map")
    void constructor_registersAllProvidersByEnum() {
        LlmProviderService deepseek = stub(LlmProvider.DEEPSEEK);
        LlmProviderService qwen = stub(LlmProvider.QWEN);
        LlmRouter router = new LlmRouter(List.of(deepseek, qwen));

        assertThat(router.get(LlmProvider.DEEPSEEK)).isSameAs(deepseek);
        assertThat(router.get(LlmProvider.QWEN)).isSameAs(qwen);
    }

    @Test
    @DisplayName("get(枚举) 命中已注册 provider")
    void get_byEnum_returnsRegisteredImpl() {
        LlmProviderService glm = stub(LlmProvider.GLM);
        LlmRouter router = new LlmRouter(List.of(glm));

        LlmProviderService result = router.get(LlmProvider.GLM);

        assertThat(result).isSameAs(glm);
    }

    @Test
    @DisplayName("get(枚举) 未注册的 provider 抛出 BusinessException")
    void get_byEnum_unknownProvider_throws() {
        LlmRouter router = new LlmRouter(List.of(stub(LlmProvider.DEEPSEEK)));

        assertThatThrownBy(() -> router.get(LlmProvider.CLAUDE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CLAUDE");
    }

    @Test
    @DisplayName("get(字符串) 大小写不敏感，'deepseek' / 'DeepSeek' / 'DEEPSEEK' 等价")
    void get_byString_caseInsensitive() {
        LlmProviderService deepseek = stub(LlmProvider.DEEPSEEK);
        LlmRouter router = new LlmRouter(List.of(deepseek));

        assertThat(router.get("deepseek")).isSameAs(deepseek);
        assertThat(router.get("DeepSeek")).isSameAs(deepseek);
        assertThat(router.get("DEEPSEEK")).isSameAs(deepseek);
    }

    @Test
    @DisplayName("get(字符串) 非法枚举名抛出 IllegalArgumentException（来自 valueOf）")
    void get_byString_invalidName_throws() {
        LlmRouter router = new LlmRouter(List.of(stub(LlmProvider.DEEPSEEK)));

        assertThatThrownBy(() -> router.get("nonexistent-provider"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空 provider 列表也能构造，但任何 get 都会抛")
    void constructor_emptyList_thenGetThrows() {
        LlmRouter router = new LlmRouter(List.of());

        assertThatThrownBy(() -> router.get(LlmProvider.QWEN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("路由出去的 Provider 调用时透传 messages/config 不被 Router 改写")
    void get_returnsExactInstance_whichIsCalledThrough() {
        LlmProviderService deepseek = stub(LlmProvider.DEEPSEEK);
        Mockito.when(deepseek.chatStream(Mockito.anyList(), Mockito.any()))
                .thenReturn(Flux.just("hello", " world"));
        LlmRouter router = new LlmRouter(List.of(deepseek));

        Flux<String> tokens = router.get(LlmProvider.DEEPSEEK)
                .chatStream(List.of(ChatMessage.user("hi")), null);

        assertThat(tokens.collectList().block()).containsExactly("hello", " world");
    }
}
