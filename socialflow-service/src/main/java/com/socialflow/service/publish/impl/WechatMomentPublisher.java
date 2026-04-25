package com.socialflow.service.publish.impl;

import com.socialflow.common.annotation.NotImplementedYet;
import com.socialflow.common.enums.PlatformType;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.PlatformAccount;
import com.socialflow.service.publish.PublishResult;
import com.socialflow.service.publish.Publisher;
import org.springframework.stereotype.Component;

/**
 * 微信朋友圈发布者 —— 辅助发布模式。
 *
 * 微信朋友圈没有公开的第三方 API，只支持辅助发布：
 * 系统将文案格式化为简洁的朋友圈风格（无标题、无 Emoji），用户手动复制到微信。
 *
 * @see Publisher 发布者策略接口
 */
@Component
public class WechatMomentPublisher implements Publisher {

    @Override
    public PlatformType platform() {
        return PlatformType.WECHAT_MOMENT;
    }

    @Override
    public boolean supportsAuto() {
        return false;
    }

    /**
     * 朋友圈不支持自动发布，调用此方法将抛出异常。
     */
    @Override
    @NotImplementedYet(value = "微信朋友圈无公开 API，无法实现自动发布",
            plannedFor = "无计划（平台限制）")
    public PublishResult publish(Content content, PlatformAccount account) {
        throw new UnsupportedOperationException("微信朋友圈暂不支持自动发布，请使用辅助发布模式");
    }

    /**
     * 准备辅助发布资源：将文案格式化为适合朋友圈的简洁风格。
     * 朋友圈不支持标题，不使用 Emoji，风格偏正式简洁。
     */
    @Override
    public PublishResult prepare(Content content) {
        StringBuilder formatted = new StringBuilder();

        // 正文部分（朋友圈无标题字段，直接展示正文）
        if (content.getBody() != null) {
            formatted.append(content.getBody());
        }

        return PublishResult.builder()
                .success(true)
                .status("PREPARED")
                .bundleUrl(formatted.toString())
                .build();
    }
}
