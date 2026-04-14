package com.socialflow.service.publish;

import lombok.Builder;
import lombok.Data;

/**
 * 发布结果封装类 —— 统一表示内容发布到各平台后的执行结果。
 *
 * 无论是自动发布还是辅助发布，最终都会返回这个对象来描述发布的结果。
 *
 * 本类使用了 Lombok 注解简化代码：
 *     - {@code @Data} —— 自动生成 getter、setter、toString、equals、hashCode 方法
 *     - {@code @Builder} —— 自动生成建造者模式代码，支持链式构建对象，如：
 *       {@code PublishResult.builder().success(true).status("SUCCESS").build()}
 *
 * 被 {@link Publisher} 接口的 publish() 和 prepare() 方法用作返回值。
 * 对应的 Controller：{@code PublishController}。
 *
 * @see Publisher 发布者接口（生产此对象）
 */
@Data
@Builder
public class PublishResult {

    /** 发布是否成功的布尔标记。{@code true} 表示成功，{@code false} 表示失败 */
    private boolean success;

    /**
     * 发布状态的文本描述，取值范围：
     *     - {@code SUCCESS} —— 发布成功
     *     - {@code FAILED} —— 发布失败
     *     - {@code PENDING} —— 发布中/待审核（部分平台发布后需要审核通过才能展示）
     */
    private String status;

    /** 发布成功后的内容链接 URL（例如公众号文章的阅读链接） */
    private String publishedUrl;

    /** 发布失败时的错误信息，用于向用户展示具体的失败原因 */
    private String errorMessage;

    /**
     * 辅助发布模式下的资源包下载 URL。
     *
     * 当平台不支持自动发布时，系统会将文案和媒体素材打包成 ZIP 文件，
     * 用户可以通过此 URL 下载资源包，然后手动发布到对应平台。
     */
    private String bundleUrl;
}
