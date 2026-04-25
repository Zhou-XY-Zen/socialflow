package com.socialflow.service.publish.impl;

import com.socialflow.common.annotation.NotImplementedYet;
import com.socialflow.common.enums.PlatformType;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.PlatformAccount;
import com.socialflow.service.publish.PublishResult;
import com.socialflow.service.publish.Publisher;
import org.springframework.stereotype.Component;

/**
 * 抖音发布者 —— 辅助发布模式。
 *
 * 抖音的第三方发布 API 对普通开发者限制严格，因此目前只支持辅助发布：
 * 系统将文案格式化为适合抖音短视频描述的风格，用户手动复制到抖音 App。
 *
 * @see Publisher 发布者策略接口
 */
@Component
public class DouyinPublisher implements Publisher {

    @Override
    public PlatformType platform() {
        return PlatformType.DOUYIN;
    }

    @Override
    public boolean supportsAuto() {
        return false;
    }

    /**
     * 抖音不支持自动发布，调用此方法将抛出异常。
     */
    @Override
    @NotImplementedYet(value = "抖音 OpenAPI 对开发者准入严格，需要走个人开发者认证 → 视频接口申请",
            plannedFor = "Wave 5+（待商务对接）")
    public PublishResult publish(Content content, PlatformAccount account) {
        throw new UnsupportedOperationException("抖音暂不支持自动发布，请使用辅助发布模式");
    }

    /**
     * 准备辅助发布资源：将文案格式化为适合抖音的短视频描述风格。
     * 添加话题标签、Emoji 等抖音特色元素。
     */
    @Override
    public PublishResult prepare(Content content) {
        StringBuilder formatted = new StringBuilder();

        // 正文部分（抖音描述通常比较简短）
        if (content.getBody() != null) {
            formatted.append(content.getBody());
        }

        // 标签部分：转为 #话题 格式（抖音用单 # 号）
        if (content.getTags() != null && !content.getTags().isEmpty()) {
            formatted.append("\n\n");
            String[] tags = content.getTags().split(",");
            for (String tag : tags) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    formatted.append("#").append(trimmed).append(" ");
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
