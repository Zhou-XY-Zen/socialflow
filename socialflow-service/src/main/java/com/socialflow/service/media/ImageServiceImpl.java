package com.socialflow.service.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.dao.mapper.MediaAssetMapper;
import com.socialflow.model.entity.MediaAsset;
import com.socialflow.model.vo.ImagePromptVO;
import com.socialflow.model.vo.ImageTaskStatusVO;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmResponse;
import com.socialflow.service.ai.llm.LlmRouter;
import com.socialflow.service.storage.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 智能配图服务实现 —— 基于 DashScope wanx 模型的文生图。
 *
 * 核心能力：
 *   1. 使用 Qwen LLM 从文案中提取英文绘图提示词
 *   2. 调用 DashScope text2image API 异步生成图片
 *   3. 将生成的图片下载到 MinIO 并保存为素材记录
 *
 * DashScope text2image API 是异步的：
 *   - 提交任务 → 返回 task_id
 *   - 轮询任务状态 → SUCCEEDED 时获取图片 URL
 *   - 图片 URL 有时效，需及时下载到 MinIO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final MediaAssetMapper mediaAssetMapper;
    private final LlmRouter llmRouter;
    private final StorageService storageService;

    // ==================== DashScope 文生图配置 ====================

    @Value("${socialflow.image.dashscope-api-key}")
    private String dashscopeApiKey;

    @Value("${socialflow.image.dashscope-base-url:https://dashscope.aliyuncs.com/api/v1}")
    private String dashscopeBaseUrl;

    @Value("${socialflow.image.model:wanx-v1}")
    private String imageModel;

    @Value("${socialflow.image.image-count:4}")
    private int imageCount;

    @Value("${socialflow.image.image-size:1024*1024}")
    private String imageSize;

    /** LLM 系统 API Key（用于提取提示词） */
    @Value("${socialflow.ai.system-api-key}")
    private String systemApiKey;

    /** HTTP 客户端（用于调用 DashScope API 和下载图片） */
    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        log.info("AI 配图服务初始化完成: model={}, imageCount={}", imageModel, imageCount);
    }

    // ==================== 1. 提取配图提示词 ====================

    /**
     * 使用 Qwen LLM 从文案内容中提取适合文生图模型的中文提示词。
     *
     * 系统提示词要求模型：
     *   - 分析文案主题、情感、视觉场景
     *   - 输出一段适合通义万相文生图模型的中文绘图描述
     *   - 包含画面构图、风格、色调等绘图要素
     */
    @Override
    public ImagePromptVO extractImagePrompt(Long userId, String contentText) {
        log.info("提取配图提示词: userId={}, textLength={}", userId, contentText.length());

        // 截取前 500 字避免超出上下文
        String truncated = contentText.length() > 500
                ? contentText.substring(0, 500) + "..."
                : contentText;

        // 构建 LLM 对话消息
        String systemPrompt = """
                你是一位专业的 AI 绘图提示词专家。
                用户会提供一段社交媒体文案，你需要根据文案的具体内容和主题，生成一段适合文生图 AI 模型的中文提示词。

                要求：
                1. 提示词必须是中文
                2. 必须紧密围绕文案的具体主题（如美食、旅行、穿搭、风景等），描述与文案直接相关的视觉场景
                3. 包含具体的场景元素、物品、色调、氛围等细节
                4. 适合作为中国社交媒体（小红书、抖音、朋友圈、公众号）的配图
                5. 长度控制在 30-80 个中文字
                6. 不要包含任何文字、水印、标题的描述
                7. 不要输出通用的万能描述，必须体现文案的独特主题

                示例：
                - 文案关于深圳旅行美食 → "深圳城市天际线夜景，前景是热闹的夜市街道，各色美食小吃摊位灯火通明，现代摩天大楼灯光璀璨，暖色调，烟火气氛围"
                - 文案关于春季护肤 → "梳妆台上精致的护肤品瓶罐，搭配新鲜花瓣和嫩绿枝叶，柔和的晨光透过白纱窗帘洒入，清新淡雅色调"
                - 文案关于咖啡探店 → "文艺咖啡馆角落，木质桌面上一杯拉花拿铁，旁边放着一本翻开的书，午后阳光斜照，温暖治愈的氛围"

                直接输出中文提示词，不要输出任何其他内容。
                """;

        List<ChatMessage> messages = List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user("请为以下文案生成配图提示词：\n\n" + truncated)
        );

        // 使用 DeepSeek 提取提示词（DeepSeek 是默认 provider，API Key 已验证可用）
        LlmConfig config = LlmConfig.builder()
                .model("deepseek-chat")
                .apiKey(systemApiKey)
                .temperature(0.7)
                .maxTokens(300)
                .build();

        try {
            LlmResponse response = llmRouter.get(LlmProvider.DEEPSEEK).chat(messages, config);
            String prompt = response.getContent().trim();

            // 去除可能的引号包裹
            if (prompt.startsWith("\"") && prompt.endsWith("\"")) {
                prompt = prompt.substring(1, prompt.length() - 1);
            }
            if (prompt.startsWith("「") && prompt.endsWith("」")) {
                prompt = prompt.substring(1, prompt.length() - 1);
            }

            ImagePromptVO vo = new ImagePromptVO();
            vo.setImagePrompt(prompt);

            log.info("配图提示词提取成功: {}", prompt.substring(0, Math.min(80, prompt.length())));
            return vo;
        } catch (Exception e) {
            log.error("提取配图提示词失败，使用文案内容生成降级提示词", e);
            // 降级：从文案中直接提取关键内容作为提示词，而不是用毫无关系的通用文案
            ImagePromptVO vo = new ImagePromptVO();
            String fallback = buildFallbackPrompt(truncated);
            vo.setImagePrompt(fallback);
            return vo;
        }
    }

    // ==================== 2. 提交文生图任务 ====================

    /**
     * 调用 DashScope wanx 模型提交异步文生图任务。
     *
     * API: POST {baseUrl}/services/aigc/text2image/image-synthesis
     * Header: Authorization: Bearer {key}, X-DashScope-Async: enable
     * Body: {"model":"wanx-v1","input":{"prompt":"..."},"parameters":{"n":4,"size":"1024*1024"}}
     *
     * 返回任务 ID（output.task_id），前端通过轮询获取结果。
     */
    @Override
    public String submitGeneration(Long userId, String prompt) {
        log.info("提交文生图任务: userId={}, prompt={}", userId, prompt.substring(0, Math.min(80, prompt.length())));

        try {
            // 构建请求体
            Map<String, Object> body = Map.of(
                    "model", imageModel,
                    "input", Map.of("prompt", prompt),
                    "parameters", Map.of(
                            "n", imageCount,
                            "size", imageSize
                    )
            );
            String jsonBody = JsonUtil.toJson(body);

            // 发送异步请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dashscopeBaseUrl + "/services/aigc/text2image/image-synthesis"))
                    .header("Authorization", "Bearer " + dashscopeApiKey)
                    .header("X-DashScope-Async", "enable")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            log.debug("DashScope 提交响应: {}", responseBody);

            JsonNode root = JsonUtil.mapper().readTree(responseBody);

            // 检查错误
            if (root.has("code") && !root.get("code").asText().isEmpty()) {
                String code = root.get("code").asText();
                String message = root.path("message").asText("Unknown error");
                throw new RuntimeException("DashScope 错误: code=" + code + ", message=" + message);
            }

            // 提取任务 ID
            String taskId = root.path("output").path("task_id").asText();
            if (taskId.isEmpty()) {
                throw new RuntimeException("DashScope 响应中缺少 task_id: " + responseBody);
            }

            log.info("文生图任务提交成功: taskId={}", taskId);
            return taskId;

        } catch (Exception e) {
            log.error("提交文生图任务失败", e);
            throw new RuntimeException("提交文生图任务失败: " + e.getMessage(), e);
        }
    }

    // ==================== 3. 查询任务状态 ====================

    /**
     * 查询 DashScope 异步任务状态。
     *
     * API: GET {baseUrl}/tasks/{taskId}
     * Header: Authorization: Bearer {key}
     *
     * 返回状态：PENDING → RUNNING → SUCCEEDED / FAILED
     * SUCCEEDED 时 output.results 包含图片 URL 列表。
     */
    @Override
    public ImageTaskStatusVO getTaskStatus(String taskId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dashscopeBaseUrl + "/tasks/" + taskId))
                    .header("Authorization", "Bearer " + dashscopeApiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            JsonNode root = JsonUtil.mapper().readTree(responseBody);
            JsonNode output = root.path("output");

            ImageTaskStatusVO vo = new ImageTaskStatusVO();
            String status = output.path("task_status").asText("UNKNOWN");
            vo.setStatus(status);

            if ("SUCCEEDED".equals(status)) {
                // 提取生成的图片 URL
                List<String> imageUrls = new ArrayList<>();
                JsonNode results = output.path("results");
                if (results.isArray()) {
                    for (JsonNode result : results) {
                        String url = result.path("url").asText("");
                        if (!url.isEmpty()) {
                            imageUrls.add(url);
                        }
                    }
                }
                vo.setImageUrls(imageUrls);
                log.info("文生图任务完成: taskId={}, imageCount={}", taskId, imageUrls.size());
            } else if ("FAILED".equals(status)) {
                String message = output.path("message").asText("生成失败");
                vo.setErrorMessage(message);
                log.warn("文生图任务失败: taskId={}, message={}", taskId, message);
            }

            return vo;

        } catch (Exception e) {
            log.error("查询文生图任务状态失败: taskId={}", taskId, e);
            ImageTaskStatusVO vo = new ImageTaskStatusVO();
            vo.setStatus("FAILED");
            vo.setErrorMessage("查询任务状态失败: " + e.getMessage());
            return vo;
        }
    }

    // ==================== 4. 下载保存到 MinIO ====================

    /**
     * 将远程图片下载到 MinIO 并创建 MediaAsset 记录。
     *
     * 流程：
     *   1. HTTP GET 下载图片字节数据
     *   2. 生成 MinIO objectKey: media/{userId}/{UUID}_ai_generated.png
     *   3. 调用 minioClient.putObject 上传
     *   4. 创建 MediaAsset 实体并写入数据库
     *
     * 复用与 MediaServiceImpl.upload() 完全一致的 MinIO 上传模式。
     */
    @Override
    public MediaAsset downloadAndSave(Long userId, String imageUrl, String tags) {
        log.info("下载保存 AI 配图: userId={}, url={}", userId, imageUrl.substring(0, Math.min(80, imageUrl.length())));

        try {
            // 1. 下载远程图片
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] imageBytes = response.body();

            // 获取 MIME 类型
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("image/png");
            // 根据 Content-Type 确定文件扩展名
            String ext = switch (contentType) {
                case "image/jpeg", "image/jpg" -> ".jpg";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                default -> ".png";
            };

            // 2. 生成文件名和对象键
            String fileName = "ai_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            String objectKey = "media/" + userId + "/" + UUID.randomUUID() + "_" + fileName;

            // 3. 上传到对象存储（COS 或 MinIO）
            storageService.upload(objectKey, imageBytes, contentType);

            // 4. 获取公开访问 URL
            String fileUrl = storageService.getPublicUrl(objectKey);

            // 5. 创建 MediaAsset 记录
            MediaAsset asset = new MediaAsset();
            asset.setUserId(userId);
            asset.setFileName(fileName);
            asset.setFileType("IMAGE");
            asset.setMimeType(contentType);
            asset.setFileUrl(fileUrl);
            asset.setThumbnailUrl(fileUrl);
            asset.setFileSize((long) imageBytes.length);
            asset.setTags(tags != null ? tags : "AI生成");

            mediaAssetMapper.insert(asset);

            log.info("AI 配图保存成功: assetId={}, fileName={}, size={}KB",
                    asset.getId(), fileName, imageBytes.length / 1024);
            return asset;

        } catch (Exception e) {
            log.error("下载保存 AI 配图失败", e);
            throw new RuntimeException("下载保存配图失败: " + e.getMessage(), e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 当 LLM 提取失败时，从文案内容中智能提取关键词构建降级提示词。
     *
     * 策略：扫描文案中的高频场景词，拼接成有意义的绘图描述，
     * 而不是返回一段与文案毫无关系的通用文案。
     */
    private String buildFallbackPrompt(String text) {
        // 场景关键词库：关键词 → 对应的视觉场景描述
        Map<String, String> sceneMap = new java.util.LinkedHashMap<>();
        // 旅行类
        sceneMap.put("旅行", "旅行风景，远处的山水或城市天际线");
        sceneMap.put("旅游", "旅游目的地风光");
        sceneMap.put("深圳", "深圳城市天际线，现代摩天大楼与蓝天");
        sceneMap.put("北京", "北京故宫红墙金瓦，蓝天白云");
        sceneMap.put("上海", "上海外滩夜景，灯火辉煌的黄浦江畔");
        sceneMap.put("成都", "成都街景，竹林与川味美食");
        sceneMap.put("杭州", "杭州西湖美景，柳树倒影湖面");
        sceneMap.put("三亚", "三亚海滩，碧蓝海水与白沙滩");
        sceneMap.put("重庆", "重庆山城夜景，立体城市灯火璀璨");
        sceneMap.put("西安", "西安古城墙，历史文化氛围");
        // 美食类
        sceneMap.put("美食", "精致美食摆盘，暖色调灯光");
        sceneMap.put("火锅", "热气腾腾的火锅，丰富食材围绕锅边");
        sceneMap.put("咖啡", "文艺咖啡馆，木桌上的拉花拿铁");
        sceneMap.put("奶茶", "手持一杯精致奶茶，街头背景");
        sceneMap.put("蛋糕", "精致蛋糕甜品，梦幻糖霜装饰");
        sceneMap.put("烧烤", "夜市烧烤摊，烟火气氛围");
        // 美妆护肤类
        sceneMap.put("护肤", "梳妆台上的护肤品，柔和晨光");
        sceneMap.put("美妆", "精致化妆品平铺，时尚杂志风格");
        sceneMap.put("口红", "唇膏特写，丝绒质感色彩");
        // 穿搭类
        sceneMap.put("穿搭", "时尚穿搭街拍，都市背景");
        sceneMap.put("连衣裙", "飘逸连衣裙，花园阳光下");
        // 健身类
        sceneMap.put("健身", "健身房运动场景，阳光活力");
        sceneMap.put("瑜伽", "清晨瑜伽练习，宁静自然背景");
        // 宠物类
        sceneMap.put("猫", "可爱猫咪特写，温暖柔和光线");
        sceneMap.put("狗", "活泼狗狗户外奔跑，阳光草地");
        // 自然风景类
        sceneMap.put("海滩", "金色海滩日落，海浪轻拍沙滩");
        sceneMap.put("山", "壮丽山峰云海，大气磅礴");
        sceneMap.put("森林", "幽静森林小径，阳光穿透树叶");
        sceneMap.put("花", "鲜花盛开的花园，色彩缤纷");

        // 从文案中匹配场景词
        StringBuilder prompt = new StringBuilder();
        int matched = 0;
        for (Map.Entry<String, String> entry : sceneMap.entrySet()) {
            if (text.contains(entry.getKey())) {
                if (matched > 0) prompt.append("，");
                prompt.append(entry.getValue());
                matched++;
                if (matched >= 3) break; // 最多取 3 个场景
            }
        }

        // 补充通用画面描述
        if (matched > 0) {
            prompt.append("，精致高清，适合社交媒体分享");
        } else {
            // 实在匹配不到，从文案前 30 字提取作为提示
            String head = text.length() > 30 ? text.substring(0, 30) : text;
            // 去除 Markdown 标记
            head = head.replaceAll("[#*>\\-_`]", "").trim();
            prompt.append(head).append("，精美配图，高清画质，氛围感");
        }

        return prompt.toString();
    }
}
