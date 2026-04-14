package com.socialflow.service.publish.impl;

import com.socialflow.common.enums.PlatformType;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.PlatformAccount;
import com.socialflow.service.publish.PublishResult;
import com.socialflow.service.publish.Publisher;
import org.springframework.stereotype.Component;

/**
 * 微信公众号发布者 —— 支持自动发布（API 对接中）。
 *
 * 微信公众号提供了官方的内容管理 API（草稿箱接口、群发接口等），
 * 理论上可以实现自动发布。但完整对接需要：
 *   1. 已认证的服务号或已备案的订阅号
 *   2. 获取微信公众平台的 AppID 和 AppSecret
 *   3. 通过 OAuth 获取 access_token
 *   4. 调用素材管理、草稿箱、群发等系列接口
 *
 * 当前实现为 Mock 版本，返回模拟的成功结果，实际微信 API 对接将在后续版本完成。
 *
 * @see Publisher 发布者策略接口
 */
@Component
public class WechatMpPublisher implements Publisher {

    @Override
    public PlatformType platform() {
        return PlatformType.WECHAT_MP;
    }

    @Override
    public boolean supportsAuto() {
        return true;
    }

    /**
     * 自动发布到微信公众号（当前为 Mock 实现）。
     *
     * 实际对接时需要：
     *   1. 使用 account 中的 accessToken 调用微信 API
     *   2. 先上传图文素材到草稿箱
     *   3. 再调用群发接口发布
     *   4. 处理微信回调确认发布状态
     */
    @Override
    public PublishResult publish(Content content, PlatformAccount account) {
        // Mock 实现：返回模拟成功结果
        return PublishResult.builder()
                .success(true)
                .status("SUCCESS")
                .publishedUrl("https://mp.weixin.qq.com/s/mock_article_id")
                .build();
    }

    /**
     * 准备辅助发布资源：将文案格式化为适合公众号的长文风格。
     * 公众号支持标题，不使用 Emoji，风格偏正式。
     */
    @Override
    public PublishResult prepare(Content content) {
        StringBuilder formatted = new StringBuilder();

        // 标题部分
        if (content.getTitle() != null && !content.getTitle().isEmpty()) {
            formatted.append("【").append(content.getTitle()).append("】\n\n");
        }

        // 正文部分
        if (content.getBody() != null) {
            formatted.append(content.getBody());
        }

        // 标签部分
        if (content.getTags() != null && !content.getTags().isEmpty()) {
            formatted.append("\n\n标签：").append(content.getTags());
        }

        return PublishResult.builder()
                .success(true)
                .status("PREPARED")
                .bundleUrl(formatted.toString())
                .build();
    }
}
