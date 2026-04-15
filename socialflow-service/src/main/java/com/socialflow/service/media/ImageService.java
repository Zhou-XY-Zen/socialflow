package com.socialflow.service.media;

import com.socialflow.model.entity.MediaAsset;
import com.socialflow.model.vo.ImagePromptVO;
import com.socialflow.model.vo.ImageTaskStatusVO;

/**
 * AI 智能配图服务接口。
 *
 * 提供文生图全流程能力：
 *   1. 从文案内容中智能提取适合绘图的英文提示词
 *   2. 调用 DashScope wanx 模型提交异步文生图任务
 *   3. 查询文生图任务状态与结果
 *   4. 将生成的远程图片下载到 MinIO 并保存为素材记录
 */
public interface ImageService {

    /**
     * 从文案内容中提取适合 AI 绘图的英文提示词。
     *
     * 使用 LLM（Qwen）分析文案主题、情感、场景，
     * 生成一段适合传递给文生图模型的英文描述。
     *
     * @param userId 用户 ID
     * @param contentText 文案正文
     * @return 包含英文绘图提示词的 VO
     */
    ImagePromptVO extractImagePrompt(Long userId, String contentText);

    /**
     * 提交 DashScope wanx 文生图异步任务。
     *
     * 调用阿里云 DashScope 的 text2image API，提交异步生成请求。
     * 生成过程通常需要 15-60 秒，返回任务 ID 供前端轮询。
     *
     * @param userId 用户 ID
     * @param prompt 英文绘图提示词
     * @return 异步任务 ID（用于后续查询状态）
     */
    String submitGeneration(Long userId, String prompt);

    /**
     * 查询文生图异步任务的执行状态。
     *
     * 返回任务状态（PENDING/RUNNING/SUCCEEDED/FAILED），
     * 若任务成功完成则包含生成的图片 URL 列表。
     *
     * @param taskId DashScope 异步任务 ID
     * @return 任务状态 VO
     */
    ImageTaskStatusVO getTaskStatus(String taskId);

    /**
     * 下载远程图片并保存到 MinIO + 数据库。
     *
     * 将 DashScope 生成的临时图片 URL 下载到本地 MinIO 存储，
     * 并创建 MediaAsset 数据库记录，使其成为素材库中的正式素材。
     *
     * @param userId 用户 ID
     * @param imageUrl 远程图片 URL
     * @param tags 标签（如 "AI生成,wanx"）
     * @return 保存后的 MediaAsset 实体
     */
    MediaAsset downloadAndSave(Long userId, String imageUrl, String tags);

    // ==================== Wave 4.2: 去重缓存 ====================

    /**
     * 查询去重缓存：如果同一用户的同 prompt+model+size 已经生成过，直接返回缓存的 MediaAsset 列表。
     *
     * @return 命中时返回非空列表，未命中返回空列表
     */
    java.util.List<com.socialflow.model.entity.MediaAsset> lookupCache(Long userId, String prompt,
                                                                       String model, String size);

    /**
     * 写入去重缓存：在用户最终选定 1 个变体并下载完成后调用。
     */
    void saveCache(Long userId, String prompt, String model, String size, java.util.List<Long> mediaIds);
}
