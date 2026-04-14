package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 社交平台账号实体类 —— 对应数据库表 `platform_account`
 *
 * 【作用】存储用户绑定的社交媒体平台账号信息和授权凭证。
 *   用户需要将自己的小红书、抖音等账号绑定到本系统，才能实现自动发布功能。
 *
 * 【为什么需要它】
 *   本系统的核心功能之一是"一键发布"，即将生成的文案自动发布到指定的社交平台。
 *   要实现这个功能，就需要保存用户在各个平台的授权凭证（AccessToken / RefreshToken），
 *   系统通过这些凭证代替用户调用平台的发布 API。
 *
 * 【关联关系】
 *   - platform_account.user_id → sys_user.id （所属用户）
 *   - publish_task.platform_account_id → platform_account.id （发布任务使用的账号）
 *
 * 【使用场景】
 *   - 用户在"账号管理"页面绑定/解绑社交平台账号
 *   - 发布文案时选择要发布到哪个平台账号
 *   - 系统定期检查 Token 是否过期并自动刷新
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("platform_account")
public class PlatformAccount extends BaseEntity {

    /**
     * 所属用户 ID
     *
     * 关联 sys_user.id，标识该平台账号属于哪个用户。
     */
    private Long userId;

    /**
     * 社交平台标识
     *
     * 可选值："XIAOHONGSHU"（小红书）、"DOUYIN"（抖音）、"WECHAT_MP"（微信公众号）等。
     */
    private String platform;

    /**
     * 平台账号名称
     *
     * 用户在该平台上的账号名称/昵称，用于展示识别。
     * 示例："我的小红书号"、"品牌官方抖音"
     */
    private String accountName;

    /**
     * 平台应用 ID（App ID）
     *
     * 在平台开发者后台申请的应用 ID，用于 OAuth 授权流程。
     * 不同平台的叫法可能不同（如 AppKey、ClientID 等），统一存在此字段。
     */
    private String appId;

    /**
     * 访问令牌（Access Token）
     *
     * 通过 OAuth 授权获取的访问令牌，用于调用平台 API（如发布文案）。
     * 通常有有效期限制，过期后需要用 refreshToken 刷新。
     */
    private String accessToken;

    /**
     * 刷新令牌（Refresh Token）
     *
     * 用于在 accessToken 过期后获取新的 accessToken。
     * refreshToken 的有效期通常比 accessToken 长得多。
     */
    private String refreshToken;

    /**
     * 令牌过期时间
     *
     * accessToken 的到期时间。系统会在到期前自动使用 refreshToken 刷新。
     */
    private LocalDateTime tokenExpiresAt;

    /**
     * 账号状态
     *
     * 可选值：0 = 已禁用（授权失效或用户主动解绑），1 = 正常（可正常使用）。
     */
    private Integer status;
}
