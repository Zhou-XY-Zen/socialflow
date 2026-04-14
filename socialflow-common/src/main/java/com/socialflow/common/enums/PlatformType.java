package com.socialflow.common.enums;

import lombok.Getter;

/**
 * 目标社交媒体平台类型枚举
 *
 * 【作用】定义系统支持发布内容的社交媒体平台。
 *   每个平台有不同的内容规则限制（标题长度、正文长度、是否允许Emoji等），
 *   AI生成文案时需要根据目标平台的规则来调整输出格式。
 *
 * 【为什么需要这些限制参数】
 *   不同平台对内容的要求不同：
 *   - 小红书：标题最多20字，正文最多800字，支持Emoji
 *   - 抖音：标题最多30字，正文最多300字，支持Emoji
 *   - 微信朋友圈：没有标题，正文最多200字，不支持Emoji（风格偏正式）
 *   - 微信公众号：标题最多30字，正文最多3000字，不支持Emoji
 *   AI生成时会读取这些参数来控制输出，发布前护栏也会检查是否超限。
 *
 * 【使用场景举例】
 *   PlatformType platform = PlatformType.of("XIAOHONGSHU");
 *   int maxLen = platform.getBodyMaxLength(); // 获取正文最大长度
 */
@Getter // Lombok注解：自动为所有字段生成 getter 方法
public enum PlatformType {

    /** 小红书——标题最多20字，正文最多800字，允许使用Emoji表情 */
    XIAOHONGSHU("XIAOHONGSHU", "小红书", 20, 800, true),

    /** 抖音——标题最多30字，正文（视频描述）最多300字，允许使用Emoji表情 */
    DOUYIN("DOUYIN", "抖音", 30, 300, true),

    /** 微信朋友圈——没有标题（titleMaxLength=0），正文最多200字，不允许Emoji */
    WECHAT_MOMENT("WECHAT_MOMENT", "微信朋友圈", 0, 200, false),

    /** 微信公众号——标题最多30字，正文最多3000字（长文），不允许Emoji */
    WECHAT_MP("WECHAT_MP", "微信公众号", 30, 3000, false);

    /** 平台编码（大写英文），用于数据库存储和前后端通信 */
    private final String code;

    /** 平台中文显示名称，用于前端界面展示 */
    private final String displayName;

    /** 标题最大字符数限制，0表示该平台没有标题字段（如朋友圈） */
    private final int titleMaxLength;

    /** 正文最大字符数限制 */
    private final int bodyMaxLength;

    /** 是否允许在文案中使用Emoji表情符号 */
    private final boolean allowsEmoji;

    /**
     * 枚举构造方法
     *
     * @param code           平台编码
     * @param displayName    中文显示名
     * @param titleMaxLength 标题最大长度（0=无标题）
     * @param bodyMaxLength  正文最大长度
     * @param allowsEmoji    是否允许Emoji
     */
    PlatformType(String code, String displayName, int titleMaxLength, int bodyMaxLength, boolean allowsEmoji) {
        this.code = code;
        this.displayName = displayName;
        this.titleMaxLength = titleMaxLength;
        this.bodyMaxLength = bodyMaxLength;
        this.allowsEmoji = allowsEmoji;
    }

    /**
     * 根据平台编码字符串查找对应的枚举值
     * 忽略大小写匹配，如 "xiaohongshu" 也能匹配到 XIAOHONGSHU
     *
     * @param code 平台编码字符串
     * @return 对应的 PlatformType 枚举值
     * @throws IllegalArgumentException 如果找不到匹配的平台，抛出异常
     */
    public static PlatformType of(String code) {
        // 遍历所有枚举值，忽略大小写进行匹配
        for (PlatformType p : values()) {
            if (p.code.equalsIgnoreCase(code)) return p;
        }
        // 没有找到匹配的平台，抛出非法参数异常
        throw new IllegalArgumentException("unknown platform: " + code);
    }
}
