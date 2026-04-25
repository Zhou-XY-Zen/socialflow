package com.socialflow.service.user;

import com.socialflow.common.enums.LlmProvider;

import java.util.List;
import java.util.Map;

/**
 * 用户 API 密钥管理服务 —— 安全地存储和管理用户的 AI 服务密钥。
 *
 * 负责的业务领域：用户自有 API Key 的增删查改，以及密钥的加解密处理。
 *
 * 安全机制：所有 API Key 都使用 AES-256-GCM 算法加密后存储在数据库中，
 * 绝不以明文形式保存。只有在实际需要调用 AI 服务时（即构建
 * {@link com.socialflow.service.ai.llm.LlmConfig} 时），才会即时解密。
 *
 * 使用场景：用户可以配置自己的 OpenAI、文心一言、通义千问等 AI 服务的 API Key，
 * 这样系统在生成内容时就会使用用户自己的配额，而不是系统的公共配额。
 *
 * 对应的 Controller：{@code ApiKeyController}，路由前缀为 {@code /api/v1/api-keys/*}。
 */
public interface ApiKeyService {

    /**
     * 保存（新增或更新）一个 AI 供应商的 API Key。
     *
     * 如果该用户在该供应商下已有 Key，则覆盖更新；否则新增一条记录。
     * 明文密钥在保存前会被 AES-256-GCM 加密。
     *
     * @param userId       当前登录用户的 ID
     * @param provider     AI 供应商枚举（如 OPENAI、WENXIN、QIANWEN 等）
     * @param plaintextKey 用户输入的明文 API Key（保存前会被加密）
     * @param baseUrl      AI 服务的自定义接口地址（可选，某些供应商支持自定义 endpoint）
     * @param isDefault    是否设置为该用户的默认供应商
     */
    void saveKey(Long userId, LlmProvider provider, String plaintextKey, String baseUrl, boolean isDefault);

    /**
     * 获取解密后的 API Key。
     *
     * 从数据库读取加密的 Key 并即时解密返回。仅在系统内部调用 AI 服务时使用，
     * 绝不应该将解密后的 Key 暴露给前端。
     *
     * @param userId   当前登录用户的 ID
     * @param provider AI 供应商枚举
     * @return 解密后的明文 API Key；如果用户未配置该供应商的 Key，返回 {@code null}
     */
    String getDecryptedKey(Long userId, LlmProvider provider);

    /**
     * 获取用户所有 API Key 的脱敏列表（用于前端展示）。
     *
     * 为了安全，返回的 Key 只显示最后 4 位字符，其余用 * 号遮盖。
     * 例如：{@code ****-****-****-3a9f}。
     *
     * @param userId 当前登录用户的 ID
     * @return 脱敏后的 Key 列表，每个元素是一个 Map，包含供应商名称、脱敏 Key、是否默认等信息
     */
    List<Map<String, Object>> listMasked(Long userId);

    /**
     * 删除用户某个 AI 供应商的 API Key。
     *
     * @param userId   当前登录用户的 ID
     * @param provider 要删除 Key 的 AI 供应商枚举
     */
    void delete(Long userId, LlmProvider provider);

    /**
     * 解析当前用户的默认 AI 供应商。
     *
     * 优先使用用户自己设置的默认供应商；如果用户没有设置，
     * 则回退到系统配置的全局默认供应商。
     *
     * @param userId 当前登录用户的 ID
     * @return 解析后的默认 AI 供应商枚举值
     */
    LlmProvider resolveDefaultProvider(Long userId);
}
