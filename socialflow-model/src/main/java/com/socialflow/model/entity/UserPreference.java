package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户偏好设置实体类 —— 对应数据库表 `user_preference`
 *
 * 【作用】存储每个用户的个性化偏好设置和风格记忆。
 *   系统会根据这些偏好在 AI 生成文案时自动应用用户的常用配置和写作风格，
 *   减少用户每次生成时重复设置参数的操作。
 *
 * 【为什么需要它】
 *   每个用户有自己固定的写作风格和常用平台。通过记录用户偏好：
 *   1. 提升用户体验 —— 自动填充默认平台、语气等参数
 *   2. 提升生成质量 —— 将风格备注和历史摘要作为额外上下文传给 AI
 *   3. 实现个性化 —— 让 AI 逐渐"学习"用户的风格偏好
 *
 * 【关联关系】
 *   - user_preference.user_id → sys_user.id （所属用户，一对一关系）
 *
 * 【使用场景】
 *   - 用户在"个人设置"页面编辑自己的偏好
 *   - 生成文案时，系统自动读取偏好作为默认参数
 *   - AI 生成时将 styleNotes 和 historySummary 作为补充上下文
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_preference")
public class UserPreference extends BaseEntity {

    /**
     * 所属用户 ID
     *
     * 关联 sys_user.id。每个用户只有一条偏好记录（一对一关系）。
     */
    private Long userId;

    /**
     * 默认目标平台
     *
     * 用户最常发布的平台，生成文案时自动选中。
     * 可选值："XIAOHONGSHU"、"DOUYIN"、"WECHAT_MOMENT"、"WECHAT_MP"
     */
    private String defaultPlatform;

    /**
     * 默认语气/风格
     *
     * 用户偏好的文案语气风格，生成文案时自动应用。
     * 可选值："casual"（随性）、"professional"（专业）、
     *         "humorous"（幽默）、"inspiring"（激励）
     */
    private String defaultTone;

    /**
     * 偏好关键词
     *
     * 用户经常使用的关键词列表，多个关键词用逗号分隔。
     * 系统在生成文案时可以自动参考这些关键词。
     * 示例："护肤,美妆,好物推荐"
     */
    private String preferredKeywords;

    /**
     * 写作风格备注
     *
     * 用户对自己写作风格的文字描述，会作为额外上下文传给 AI。
     * 示例："喜欢用短句，多用感叹号，经常加 emoji，行文活泼接地气"
     */
    private String styleNotes;

    /**
     * 历史生成摘要
     *
     * 系统自动从用户过往生成的文案中总结出的风格特征。
     * 随着用户使用量增加，系统会定期更新此字段，让 AI 更好地理解用户风格。
     * 示例："该用户偏好小红书平台，擅长种草类内容，语气轻松活泼……"
     */
    private String historySummary;
}
