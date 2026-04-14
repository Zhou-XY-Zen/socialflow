package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户 API 密钥实体类 —— 对应数据库表 `user_api_key`
 *
 * 【作用】存储用户自行配置的第三方 AI 服务 API 密钥。
 *   用户可以绑定自己的 OpenAI、Anthropic 等大模型服务的 API Key，
 *   系统在调用 AI 生成文案时会使用该密钥。
 *
 * 【为什么需要它】
 *   本系统支持"自带 Key"（BYOK）模式，用户可以使用自己的 API 配额，
 *   这样既保护了系统的 API 额度，又让用户拥有更灵活的模型选择权。
 *   一个用户可以配置多个不同服务商的密钥，并指定其中一个为默认。
 *
 * 【关联关系】
 *   - user_api_key.user_id → sys_user.id （所属用户）
 *
 * 【使用场景】
 *   - 用户在"设置 -> API 密钥管理"页面添加/编辑/删除密钥
 *   - AI 生成文案时，根据用户选择的 provider 查找对应的密钥进行调用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_api_key")
public class UserApiKey extends BaseEntity {

    /**
     * 所属用户 ID
     *
     * 关联 sys_user.id，标识这条 API 密钥属于哪个用户。
     */
    private Long userId;

    /**
     * AI 服务提供商标识
     *
     * 可选值示例："OPENAI"、"ANTHROPIC"、"DEEPSEEK"、"MOONSHOT" 等。
     * 用于区分不同的大模型服务商。
     */
    private String provider;

    /**
     * 加密后的 API 密钥
     *
     * 出于安全考虑，API Key 经过 AES 等加密算法加密后存储，而非明文保存。
     * 使用时由服务端解密后传递给 AI 服务。
     */
    private String apiKeyEncrypted;

    /**
     * 自定义的 API 基础 URL
     *
     * 部分用户可能使用代理或自部署的 AI 服务，此字段用于指定自定义的接口地址。
     * 为空时使用服务商的官方默认地址。
     * 示例："https://api.openai.com/v1"
     */
    private String baseUrl;

    /**
     * 是否为默认密钥
     *
     * 0 = 非默认，1 = 默认。
     * 每个 provider 下最多只有一条记录为默认。
     * 用户未指定使用哪个密钥时，系统自动使用标记为默认的那一条。
     */
    private Integer isDefault;
}
