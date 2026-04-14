package com.socialflow.service.publish.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.enums.PlatformType;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.common.util.MarkdownUtil;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.PlatformAccount;
import com.socialflow.service.publish.PublishResult;
import com.socialflow.service.publish.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信公众号发布者 —— 通过微信公众平台 API 实现自动发布。
 *
 * 发布流程（正式认证账号 —— 首选）：
 *   1. 获取 access_token（GET /cgi-bin/token）
 *   2. 上传封面图为永久素材（POST /cgi-bin/material/add_material）
 *   3. 创建草稿（POST /cgi-bin/draft/add）—— 包含 thumb_media_id
 *   4. 提交发布（POST /cgi-bin/freepublish/submit）
 *
 * 降级流程（不支持草稿/发布接口的场景）：
 *   - 第二降级：图文群发（mpnews）—— 支持长内容
 *   - 最终降级：纯文本群发（限 600 字）
 *
 * access_token 和默认封面 media_id 缓存在内存中，过期前自动刷新。
 *
 * @see Publisher 发布者策略接口
 */
@Service
public class WechatMpPublisher implements Publisher {

    private static final Logger log = LoggerFactory.getLogger(WechatMpPublisher.class);

    /** 微信 API 基础地址 */
    private static final String WX_API_BASE = "https://api.weixin.qq.com";

    @Value("${socialflow.publish.wechat-mp.app-id:}")
    private String appId;

    @Value("${socialflow.publish.wechat-mp.app-secret:}")
    private String appSecret;

    private final WebClient webClient;

    /** 缓存的 access_token */
    private volatile String cachedAccessToken;

    /** access_token 的过期时间戳（毫秒） */
    private volatile long tokenExpiresAt = 0;

    /** 缓存的默认封面图永久素材 media_id（只需上传一次） */
    private volatile String defaultThumbMediaId;

    public WechatMpPublisher(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(WX_API_BASE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    @Override
    public PlatformType platform() {
        return PlatformType.WECHAT_MP;
    }

    @Override
    public boolean supportsAuto() {
        return true;
    }

    /**
     * 自动发布到微信公众号。
     *
     * 先尝试草稿+发布流程（适用于已认证的正式公众号），
     * 若草稿接口失败，则降级为图文群发或纯文本群发。
     */
    @Override
    public PublishResult publish(Content content, PlatformAccount account) {
        try {
            String accessToken = getAccessToken();

            // ---- 第一优先级：草稿 + 发布流程（正式公众号推荐方式）----
            try {
                // 确保有封面图素材 ID
                String thumbMediaId = ensureThumbMediaId(accessToken);
                String mediaId = createDraft(accessToken, content, thumbMediaId);
                String publishId = submitFreePublish(accessToken, mediaId);
                log.info("【微信公众号】草稿发布成功, publishId={}", publishId);

                return PublishResult.builder()
                        .success(true)
                        .status("SUCCESS")
                        .publishedUrl("https://mp.weixin.qq.com/s/" + publishId)
                        .build();
            } catch (Exception draftEx) {
                log.warn("【微信公众号】草稿/发布接口失败，降级为图文群发: {}", draftEx.getMessage());
            }

            // ---- 第二降级：图文群发（mpnews），支持长内容 ----
            try {
                String mediaId = uploadNews(accessToken, content);
                String result = massSendNews(accessToken, mediaId);
                log.info("【微信公众号】图文群发成功, result={}", result);

                return PublishResult.builder()
                        .success(true)
                        .status("SUCCESS")
                        .publishedUrl(result)
                        .build();
            } catch (Exception newsEx) {
                log.warn("【微信公众号】图文群发失败，再降级为纯文本: {}", newsEx.getMessage());
            }

            // ---- 最终降级：纯文本群发（限 600 字）----
            String textContent = buildPlainText(content);
            if (textContent.length() > 580) {
                textContent = textContent.substring(0, 580) + "...\n\n【全文请在SocialFlow平台查看】";
            }
            String result = massSendText(accessToken, textContent);
            log.info("【微信公众号】纯文本群发成功, result={}", result);

            return PublishResult.builder()
                    .success(true)
                    .status("SUCCESS")
                    .publishedUrl(result)
                    .build();

        } catch (Exception e) {
            log.error("【微信公众号】发布失败: {}", e.getMessage(), e);
            return PublishResult.builder()
                    .success(false)
                    .status("FAILED")
                    .errorMessage("微信公众号发布失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 准备辅助发布资源：将文案格式化为适合公众号的长文风格。
     * 公众号支持标题，不使用 Emoji，风格偏正式。
     * 自动去除 Markdown 语法标记，输出干净的纯文本预览。
     */
    @Override
    public PublishResult prepare(Content content) {
        StringBuilder formatted = new StringBuilder();

        // 标题部分
        if (content.getTitle() != null && !content.getTitle().isEmpty()) {
            formatted.append("【").append(content.getTitle()).append("】\n\n");
        }

        // 正文部分——去除 Markdown 标记
        if (content.getBody() != null) {
            formatted.append(MarkdownUtil.stripMarkdown(content.getBody()));
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

    // ======================== 微信 API 调用方法 ========================

    /**
     * 获取 access_token，支持内存缓存，过期前 5 分钟自动刷新。
     */
    private synchronized String getAccessToken() throws Exception {
        long now = System.currentTimeMillis();
        // 提前 5 分钟刷新，避免临界过期
        if (cachedAccessToken != null && now < tokenExpiresAt - 300_000) {
            return cachedAccessToken;
        }

        log.info("【微信公众号】获取新的 access_token, appId={}", appId);

        String json = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/token")
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", appId)
                        .queryParam("secret", appSecret)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = JsonUtil.mapper().readTree(json);

        // 检查错误响应
        if (root.has("errcode") && root.get("errcode").asInt() != 0) {
            String errMsg = root.path("errmsg").asText("unknown error");
            throw new RuntimeException("获取access_token失败: errcode="
                    + root.get("errcode").asInt() + ", errmsg=" + errMsg);
        }

        cachedAccessToken = root.get("access_token").asText();
        int expiresIn = root.get("expires_in").asInt(7200);
        tokenExpiresAt = now + (long) expiresIn * 1000;

        log.info("【微信公众号】access_token 获取成功, expires_in={}s", expiresIn);
        return cachedAccessToken;
    }

    // ======================== 封面图管理 ========================

    /**
     * 确保有一个可用的默认封面图永久素材 ID。
     *
     * 正式公众号的草稿接口（draft/add）要求每篇文章必须有封面图（thumb_media_id），
     * 这里自动生成一张纯色封面图并上传为永久素材，缓存 media_id 后续复用。
     *
     * @param accessToken 有效的 access_token
     * @return 封面图的永久素材 media_id
     */
    private synchronized String ensureThumbMediaId(String accessToken) throws Exception {
        if (defaultThumbMediaId != null) {
            return defaultThumbMediaId;
        }

        log.info("【微信公众号】上传默认封面图为永久素材...");

        // 生成一张 900×383 的渐变封面图（微信推荐封面比例 2.35:1）
        byte[] imageBytes = generateDefaultCoverImage();

        // 使用 multipart/form-data 上传永久图片素材
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("media", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "socialflow_cover.png";
            }
        }).contentType(MediaType.IMAGE_PNG);

        String json = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/material/add_material")
                        .queryParam("access_token", accessToken)
                        .queryParam("type", "image")
                        .build())
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = JsonUtil.mapper().readTree(json);
        checkWxError(root, "上传封面图素材");

        defaultThumbMediaId = root.get("media_id").asText();
        log.info("【微信公众号】默认封面图上传成功, media_id={}", defaultThumbMediaId);
        return defaultThumbMediaId;
    }

    /**
     * 程序化生成一张 900×383 像素的默认封面图（渐变蓝色 + SocialFlow 文字）。
     *
     * 微信公众号文章封面推荐尺寸为 900×383（比例 2.35:1），
     * 这里使用 Java AWT 绘制简洁的渐变背景加白色文字。
     *
     * @return PNG 图片字节数组
     */
    private byte[] generateDefaultCoverImage() throws Exception {
        int width = 900;
        int height = 383;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 开启抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 绘制渐变背景（深蓝 → 浅蓝）
        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(41, 98, 255),        // 左上角：深蓝
                width, height, new Color(0, 176, 255) // 右下角：浅蓝
        );
        g.setPaint(gradient);
        g.fillRect(0, 0, width, height);

        // 绘制白色标题文字
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 48));
        String title = "SocialFlow";
        FontMetrics fm = g.getFontMetrics();
        int textX = (width - fm.stringWidth(title)) / 2;
        int textY = height / 2 - 10;
        g.drawString(title, textX, textY);

        // 绘制副标题
        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        String subtitle = "AI Content Creation Platform";
        FontMetrics fm2 = g.getFontMetrics();
        int subX = (width - fm2.stringWidth(subtitle)) / 2;
        g.drawString(subtitle, subX, textY + 45);

        g.dispose();

        // 输出为 PNG 字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    // ======================== 草稿 + 发布（正式公众号） ========================

    /**
     * 创建草稿（适用于正式公众号）。
     *
     * POST /cgi-bin/draft/add?access_token={TOKEN}
     * Body: {"articles":[{"title":"标题","content":"正文HTML","thumb_media_id":"封面ID","digest":"摘要"}]}
     *
     * @param accessToken  有效的 access_token
     * @param content      要发布的内容
     * @param thumbMediaId 封面图永久素材 media_id
     * @return media_id 草稿的素材 ID
     */
    private String createDraft(String accessToken, Content content, String thumbMediaId) throws Exception {
        String title = content.getTitle() != null ? content.getTitle() : "SocialFlow 文章";
        // 将 Markdown 正文转换为 HTML（公众号草稿接口要求 HTML 格式）
        String bodyText = content.getBody() != null ? content.getBody() : "";
        String htmlContent = MarkdownUtil.markdownToHtml(bodyText);
        // 摘要使用纯文本（去除 Markdown 标记），截取前 120 字
        String plainBody = MarkdownUtil.stripMarkdown(bodyText);
        String digest = plainBody.length() > 120 ? plainBody.substring(0, 120) : plainBody;

        // 组装文章字段（正式号要求 thumb_media_id 必填）
        Map<String, Object> article = new HashMap<>();
        article.put("title", title);
        article.put("content", htmlContent);
        article.put("thumb_media_id", thumbMediaId);
        article.put("digest", digest);
        article.put("author", "SocialFlow AI");
        article.put("content_source_url", "");
        article.put("need_open_comment", 0);
        article.put("only_fans_can_comment", 0);

        Map<String, Object> body = Map.of("articles", List.of(article));

        log.debug("【微信公众号】创建草稿, title={}, thumb_media_id={}", title, thumbMediaId);

        String json = webClient.post()
                .uri("/cgi-bin/draft/add?access_token=" + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = JsonUtil.mapper().readTree(json);
        checkWxError(root, "创建草稿");

        String mediaId = root.get("media_id").asText();
        log.info("【微信公众号】草稿创建成功, media_id={}", mediaId);
        return mediaId;
    }

    /**
     * 提交发布（将草稿发布为正式文章）。
     *
     * POST /cgi-bin/freepublish/submit?access_token={TOKEN}
     * Body: {"media_id":"xxx"}
     *
     * @return publish_id 发布任务 ID
     */
    private String submitFreePublish(String accessToken, String mediaId) throws Exception {
        Map<String, Object> body = Map.of("media_id", mediaId);

        log.debug("【微信公众号】提交发布, media_id={}", mediaId);

        String json = webClient.post()
                .uri("/cgi-bin/freepublish/submit?access_token=" + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = JsonUtil.mapper().readTree(json);
        checkWxError(root, "提交发布");

        String publishId = root.path("publish_id").asText("");
        log.info("【微信公众号】发布提交成功, publish_id={}", publishId);
        return publishId;
    }

    // ======================== 降级群发方案 ========================

    /**
     * 上传图文素材（降级方案：mpnews 群发，不走草稿箱）。
     *
     * 接口：POST /cgi-bin/media/uploadnews
     */
    private String uploadNews(String accessToken, Content content) throws Exception {
        String title = content.getTitle() != null ? content.getTitle() : "SocialFlow AI 生成文案";
        // 将 Markdown 正文转换为 HTML（图文素材接口支持 HTML 富文本）
        String htmlContent = MarkdownUtil.markdownToHtml(content.getBody() != null ? content.getBody() : "");
        // 摘要使用纯文本（去除 Markdown 标记），截取前 50 字
        String plainBody = MarkdownUtil.stripMarkdown(content.getBody() != null ? content.getBody() : "");

        Map<String, Object> article = new HashMap<>();
        article.put("title", title);
        article.put("content", htmlContent);
        article.put("digest", plainBody.length() > 50 ? plainBody.substring(0, 50) + "..." : plainBody);
        article.put("show_cover_pic", 0);
        // thumb_media_id 必填，尝试使用默认封面
        try {
            String thumbId = ensureThumbMediaId(accessToken);
            article.put("thumb_media_id", thumbId);
        } catch (Exception e) {
            log.warn("获取封面图失败，使用空值: {}", e.getMessage());
            article.put("thumb_media_id", "");
        }

        Map<String, Object> body = Map.of("articles", List.of(article));

        String json = webClient.post()
                .uri("/cgi-bin/media/uploadnews?access_token=" + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = JsonUtil.mapper().readTree(json);
        checkWxError(root, "上传图文素材");
        return root.path("media_id").asText();
    }

    /**
     * 群发图文消息（mpnews），支持长内容。
     */
    private String massSendNews(String accessToken, String mediaId) throws Exception {
        Map<String, Object> body = Map.of(
                "filter", Map.of("is_to_all", true),
                "mpnews", Map.of("media_id", mediaId),
                "msgtype", "mpnews"
        );

        String json = webClient.post()
                .uri("/cgi-bin/message/mass/sendall?access_token=" + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = JsonUtil.mapper().readTree(json);
        checkWxError(root, "群发图文");
        return root.path("msg_id").asText("");
    }

    /**
     * 群发纯文本消息（最终降级，限 600 字）。
     */
    private String massSendText(String accessToken, String textContent) throws Exception {
        Map<String, Object> body = Map.of(
                "filter", Map.of("is_to_all", true),
                "text", Map.of("content", textContent),
                "msgtype", "text"
        );

        log.debug("【微信公众号】群发文本消息, length={}", textContent.length());

        String json = webClient.post()
                .uri("/cgi-bin/message/mass/sendall?access_token=" + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = JsonUtil.mapper().readTree(json);
        checkWxError(root, "群发文本");

        String msgId = root.path("msg_id").asText("");
        String msgDataId = root.path("msg_data_id").asText("");
        log.info("【微信公众号】群发文本成功, msg_id={}, msg_data_id={}", msgId, msgDataId);
        return "msg_id=" + msgId;
    }

    // ======================== 工具方法 ========================

    /**
     * 构建纯文本内容（用于群发文本消息）。
     * 会自动去除 Markdown 语法标记，避免微信显示原始标记符号。
     */
    private String buildPlainText(Content content) {
        StringBuilder sb = new StringBuilder();
        if (content.getTitle() != null && !content.getTitle().isEmpty()) {
            sb.append("【").append(content.getTitle()).append("】\n\n");
        }
        if (content.getBody() != null) {
            // 去除 Markdown 标记，转换为可读纯文本
            sb.append(MarkdownUtil.stripMarkdown(content.getBody()));
        }
        if (content.getTags() != null && !content.getTags().isEmpty()) {
            sb.append("\n\n标签：").append(content.getTags());
        }
        return sb.toString();
    }

    /**
     * 检查微信 API 响应中是否包含错误码，非零则抛出异常。
     */
    private void checkWxError(JsonNode root, String operation) {
        if (root.has("errcode") && root.get("errcode").asInt() != 0) {
            int errcode = root.get("errcode").asInt();
            String errmsg = root.path("errmsg").asText("unknown error");
            throw new RuntimeException(operation + "失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }
    }
}
