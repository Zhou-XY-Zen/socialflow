package com.socialflow.service.publish;

import com.socialflow.common.enums.PlatformType;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.PlatformAccount;

/**
 * 发布者接口 —— 策略模式的核心抽象，每个目标平台对应一个实现类。
 *
 * 负责的业务领域：将生成好的内容发布到各社交媒体平台（如微信公众号、微博、小红书等）。
 *
 * 设计模式说明（策略模式 Strategy Pattern）：
 *     - 本接口定义了"发布"这个行为的统一规范
 *     - 每个平台有自己的实现类（如 WeChatPublisher、WeiboPublisher 等）
 *     - 每个实现类通过 Spring 自动注册为 Bean
 *     - {@link PublishRouter} 根据平台类型自动选择对应的实现类
 * 这样新增一个平台时，只需要新增一个实现类，无需修改已有代码（开闭原则）。
 *
 * 发布模式分为两种：
 *     - 自动发布（Auto）—— 通过平台官方 API 直接发布，用户无需手动操作
 *     - 辅助发布（Assisted）—— 系统打包好文案和媒体素材，用户手动粘贴到平台发布
 *
 * 对应的 Controller：{@code PublishController}，路由前缀为 {@code /api/v1/publish/*}。
 *
 * @see PublishRouter 发布路由器，根据平台类型选择对应的 Publisher 实现
 * @see PublishResult 发布结果封装对象
 */
public interface Publisher {

    /**
     * 返回当前发布者对应的平台类型。
     *
     * {@link PublishRouter} 通过此方法识别每个 Publisher 实现负责哪个平台。
     *
     * @return 平台类型枚举值（如 WECHAT、WEIBO、XIAOHONGSHU 等）
     */
    PlatformType platform();

    /**
     * 当前平台是否支持自动发布。
     *
     * 自动发布指通过平台官方 API 直接发布内容，用户无需手动操作。
     * 目前只有微信公众号支持自动发布，其他平台因 API 限制只能使用辅助发布。
     *
     * @return {@code true} 表示支持自动发布，{@code false} 表示仅支持辅助发布
     */
    boolean supportsAuto();

    /**
     * 执行自动发布 —— 通过平台 API 将内容直接发布到目标平台。
     *
     * 需要用户已绑定平台账号（PlatformAccount），并且该账号的授权仍然有效。
     *
     * @param content 要发布的内容实体，包含标题、正文、标签、媒体素材等
     * @param account 用户绑定的平台账号信息，包含 access_token 等认证信息
     * @return 发布结果，包含发布状态（成功/失败/待审核）、发布后的 URL、错误信息等
     */
    PublishResult publish(Content content, PlatformAccount account);

    /**
     * 构建辅助发布资源包。
     *
     * 对于不支持自动发布的平台，系统会把内容整理成标准格式的文案文本，
     * 并将关联的图片/视频等媒体文件打包成 ZIP 压缩包。
     * 用户下载后可以手动粘贴文案并上传素材到平台的 App 中发布。
     *
     * @param content 要准备发布的内容实体
     * @return 发布结果，其中 bundleUrl 字段包含资源包的下载链接
     */
    PublishResult prepare(Content content);
}
