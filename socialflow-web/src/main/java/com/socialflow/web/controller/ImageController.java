package com.socialflow.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.socialflow.common.constant.CommonConstants;
import com.socialflow.common.result.R;
import com.socialflow.model.entity.MediaAsset;
import com.socialflow.model.vo.ImagePromptVO;
import com.socialflow.model.vo.ImageTaskStatusVO;
import com.socialflow.service.media.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @Operation(summary = "提交 AI 文生图任务")
    @PostMapping("/generate")
    public R<Map<String, String>> generate(@RequestBody Map<String, String> body) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            String prompt = body.get("prompt");
            if (prompt == null || prompt.isBlank()) {
                return R.fail("绘图提示词不能为空");
            }
            String taskId = imageService.submitGeneration(userId, prompt);
            return R.ok(Map.of("taskId", taskId));
        } catch (Exception e) {
            log.error("提交文生图任务失败", e);
            return R.fail("提交文生图任务失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
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
     * 下载远程图片到 MinIO 并保存为素材。
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
