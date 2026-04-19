package com.socialflow.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.R;
import com.socialflow.model.entity.MediaAsset;
import com.socialflow.model.vo.ImagePromptVO;
import com.socialflow.model.vo.ImageTaskStatusVO;
import com.socialflow.service.media.ImageService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 智能配图控制器 —— 提供文生图和配图管理接口。
 */
@Slf4j
@Tag(name = "image", description = "AI 智能配图")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/image")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @Value("${socialflow.image.model:wanx-v1}")
    private String imageModel;

    @Value("${socialflow.image.image-size:1024*1024}")
    private String imageSize;

    /**
     * 诊断端点 —— 不需要登录，用于验证 ImageService 是否正常初始化。
     */
    @SaIgnore
    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("image service ok");
    }

    /**
     * 从文案内容中提取 AI 绘图提示词。
     */
    @Operation(summary = "从文案提取 AI 绘图提示词")
    @PostMapping("/extract-prompt")
    public R<ImagePromptVO> extractPrompt(@RequestBody Map<String, String> body) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            String text = body.get("text");
            if (text == null || text.isBlank()) {
                return R.fail("文案内容不能为空");
            }
            return R.ok(imageService.extractImagePrompt(userId, text));
        } catch (Exception e) {
            log.error("提取配图提示词失败", e);
            return R.fail("提取配图提示词失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * 提交 AI 文生图异步任务。
     */
    @Operation(summary = "提交 AI 文生图任务（Wave 4.2: 命中缓存直接返回 mediaIds，无需调用 wanx）")
    @PostMapping("/generate")
    @RateLimiter(name = "ai-image")
    public R<Map<String, Object>> generate(@RequestBody Map<String, String> body) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            String prompt = body.get("prompt");
            if (prompt == null || prompt.isBlank()) {
                return R.fail("绘图提示词不能为空");
            }
            // Wave 4.2: 缓存命中检查 —— 同 prompt+model+size 直接返回已生成的 MediaAsset
            List<MediaAsset> cached = imageService.lookupCache(userId, prompt, imageModel, imageSize);
            if (!cached.isEmpty()) {
                List<Long> ids = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                for (MediaAsset a : cached) {
                    ids.add(a.getId());
                    urls.add(a.getFileUrl());
                }
                Map<String, Object> resp = new HashMap<>();
                resp.put("cached", true);
                resp.put("mediaIds", ids);
                resp.put("imageUrls", urls);
                return R.ok(resp);
            }
            String taskId = imageService.submitGeneration(userId, prompt);
            Map<String, Object> resp = new HashMap<>();
            resp.put("cached", false);
            resp.put("taskId", taskId);
            return R.ok(resp);
        } catch (Exception e) {
            log.error("提交文生图任务失败", e);
            return R.fail("提交文生图任务失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * Wave 4.2 - 用户从生成的 4 个变体中选择 1 个并下载入库。
     *
     * <p>请求体：{@code {"prompt": "...", "imageUrls": ["url1","url2","url3","url4"],
     * "selected": [0,2], "tags": "AI生成"}}</p>
     *
     * <p>支持单选/多选；下载完成后写入 image_asset_cache，下次同 prompt 直接复用。</p>
     */
    @Operation(summary = "从 4 个变体中选择并下载入库（写入去重缓存）")
    @PostMapping("/select")
    public R<Map<String, Object>> selectVariants(@RequestBody Map<String, Object> body) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            String prompt = (String) body.get("prompt");
            String tags = (String) body.getOrDefault("tags", "AI生成");
            @SuppressWarnings("unchecked")
            List<String> imageUrls = (List<String>) body.get("imageUrls");
            @SuppressWarnings("unchecked")
            List<Integer> selected = (List<Integer>) body.get("selected");

            if (imageUrls == null || imageUrls.isEmpty() || selected == null || selected.isEmpty()) {
                return R.fail("imageUrls 和 selected 都不能为空");
            }

            List<MediaAsset> saved = new ArrayList<>();
            List<Long> savedIds = new ArrayList<>();
            for (Integer idx : selected) {
                if (idx < 0 || idx >= imageUrls.size()) continue;
                MediaAsset asset = imageService.downloadAndSave(userId, imageUrls.get(idx), tags);
                saved.add(asset);
                savedIds.add(asset.getId());
            }

            // 写入去重缓存
            if (prompt != null && !prompt.isBlank() && !savedIds.isEmpty()) {
                imageService.saveCache(userId, prompt, imageModel, imageSize, savedIds);
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("count", saved.size());
            resp.put("assets", saved);
            return R.ok(resp);
        } catch (Exception e) {
            log.error("选择变体失败", e);
            return R.fail("选择变体失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * 查询文生图任务状态。
     */
    @Operation(summary = "查询文生图任务状态")
    @GetMapping("/generate/status/{taskId}")
    public R<ImageTaskStatusVO> getStatus(@PathVariable String taskId) {
        return R.ok(imageService.getTaskStatus(taskId));
    }

    /**
     * 下载远程图片到 对象存储 并保存为素材。
     */
    @Operation(summary = "下载远程图片保存到素材库")
    @PostMapping("/download")
    public R<MediaAsset> download(@RequestBody Map<String, String> body) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            String imageUrl = body.get("imageUrl");
            String tags = body.getOrDefault("tags", "AI生成");
            if (imageUrl == null || imageUrl.isBlank()) {
                return R.fail("图片 URL 不能为空");
            }
            return R.ok(imageService.downloadAndSave(userId, imageUrl, tags));
        } catch (Exception e) {
            log.error("下载保存配图失败", e);
            return R.fail("下载保存配图失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}
