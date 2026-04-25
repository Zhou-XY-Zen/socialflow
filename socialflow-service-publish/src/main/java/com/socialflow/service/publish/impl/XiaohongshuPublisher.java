package com.socialflow.service.publish.impl;

import com.socialflow.common.annotation.NotImplementedYet;
import com.socialflow.common.enums.PlatformType;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.PlatformAccount;
import com.socialflow.service.publish.PublishResult;
import com.socialflow.service.publish.Publisher;
import org.springframework.stereotype.Component;

/**
 * 小红书发布者 —— 辅助发布模式。
 *
 * 小红书目前没有开放第三方发布 API，因此只支持辅助发布：
 * 系统将文案格式化（添加 Emoji、话题标签等），用户手动复制粘贴到小红书 App 发布。
 *
 * @see Publisher 发布者策略接口
 */
@Component
public class XiaohongshuPublisher implements Publisher {

    @Override
    public PlatformType platform() {
        return PlatformType.XIAOHONGSHU;
    }

    @Override
    public boolean supportsAuto() {
        return false;
    }

    /**
     * 小红书不支持自动发布，调用此方法将抛出异常。
     */
    @Override
    @NotImplementedYet(value = "小红书未提供公开发布 API，仅支持辅助发布",
            plannedFor = "无计划（平台限制）")
    public PublishResult publish(Content content, PlatformAccount account) {
        throw new UnsupportedOperationException("小红书暂不支持自动发布，请使用辅助发布模式");
    }

    /**
     * 准备辅助发布资源：将文案格式化为适合小红书的风格。
     * 添加 Emoji 分隔符、话题标签等小红书特色元素。
     */
    @Override
    public PublishResult prepare(Content content) {
        StringBuilder formatted = new StringBuilder();

        // 标题部分：添加 Emoji 装饰
        if (content.getTitle() != null && !content.getTitle().isEmpty()) {
            formatted.append("\uD83D\uDCCC ").append(content.getTitle()).append("\n\n");
        }

        // 正文部分
        if (content.getBody() != null) {
            formatted.append(content.getBody());
        }

        // 标签部分：转为 #话题# 格式
        if (content.getTags() != null && !content.getTags().isEmpty()) {
            formatted.append("\n\n");
            String[] tags = content.getTags().split(",");
            for (String tag : tags) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    formatted.append("#").append(trimmed).append("# ");
                }
            }
        }

        return PublishResult.builder()
                .success(true)
                .status("PREPARED")
                .bundleUrl(formatted.toString())
                .build();
    }
}
