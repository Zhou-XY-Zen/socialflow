package com.socialflow.service.ai.llm;

import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 请求路由器——将请求分发到正确的 {@link LlmProviderService} 实现。
 *
 * 【什么是策略模式路由？】
 *
 * 系统支持多种 LLM 提供者（DeepSeek、通义千问、OpenAI、Claude 等），
 * 每种提供者的 API 调用方式不同，但对外暴露统一的接口。
 * 路由器（Router）的职责就是根据用户选择的提供者，找到对应的实现类并返回。
 * 这是"策略模式"（Strategy Pattern）的一种应用：
 *     - 统一接口：{@link LlmProviderService}
 *     - 多种实现：DeepSeekLlmProvider、QwenLlmProvider 等
 *     - 路由选择：根据 {@link LlmProvider} 枚举值查找
 *
 * 【自动注册机制】
 *
 * 所有 Provider 实现类都是 Spring Bean，Spring 容器启动时会自动将它们
 * 注入到构造函数的 {@code providerBeans} 列表中。路由器在构造时遍历列表，
 * 将每个 Provider 按其枚举值注册到内部 Map 中，后续查找时间复杂度为 O(1)。
 */
@Component
public class LlmRouter {

    /** 提供者枚举 → 实现实例的映射表，用于 O(1) 查找 */
    private final Map<LlmProvider, LlmProviderService> providers = new HashMap<>();

    /**
     * 构造函数：Spring 自动注入所有 {@link LlmProviderService} 的实现 Bean。
     *
     * 遍历所有实现，按各自的 {@link LlmProviderService#provider()} 枚举值
     * 注册到内部映射表中。
     *
     * @param providerBeans Spring 容器中所有 LlmProviderService 实现的列表
     */
    public LlmRouter(List<LlmProviderService> providerBeans) {
        for (LlmProviderService svc : providerBeans) {
            providers.put(svc.provider(), svc);
        }
    }

    /**
     * 根据提供者枚举获取对应的 LLM 服务实现。
     *
     * @param provider LLM 提供者枚举值（如 DEEPSEEK、QWEN 等）
     * @return 对应的 Provider 实现实例
     * @throws BusinessException 如果该提供者没有注册实现，则抛出"模型不支持"异常
     */
    public LlmProviderService get(LlmProvider provider) {
        LlmProviderService svc = providers.get(provider);
        if (svc == null) {
            throw new BusinessException(ResultCode.MODEL_NOT_SUPPORTED,
                    "no provider registered for " + provider);
        }
        return svc;
    }

    /**
     * 根据提供者编码字符串获取对应的 LLM 服务实现。
     *
     * 将字符串转为大写后匹配枚举值，方便前端传入小写字符串时使用。
     *
     * @param providerCode 提供者编码字符串，如 "deepseek"、"qwen"
     * @return 对应的 Provider 实现实例
     */
    public LlmProviderService get(String providerCode) {
        return get(LlmProvider.valueOf(providerCode.toUpperCase()));
    }
}
