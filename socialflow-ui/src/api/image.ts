/**
 * ============================================================
 * api/image.ts —— AI 智能配图相关 API 接口
 * ============================================================
 * 本文件封装了 AI 文生图（DashScope wanx 模型）的全部接口调用：
 *   - 从文案内容提取英文绘图提示词
 *   - 提交异步文生图任务
 *   - 轮询查询任务状态
 *   - 下载远程图片到 MinIO 保存为素材
 * ============================================================
 */

import { get, post } from './http'
import type { MediaAssetVO } from './media'

/**
 * ImagePromptVO —— AI 配图提示词（后端提取返回）。
 */
export interface ImagePromptVO {
  /** 英文 AI 绘图提示词（传给 wanx 模型） */
  imagePrompt: string
}

/**
 * ImageTaskStatusVO —— 文生图异步任务状态。
 *
 * DashScope 的文生图是异步接口，需要轮询获取结果。
 * status 字段表示任务当前阶段：
 *   - PENDING: 排队中
 *   - RUNNING: 生成中
 *   - SUCCEEDED: 生成完成，imageUrls 包含图片链接
 *   - FAILED: 生成失败，errorMessage 包含错误原因
 */
export interface ImageTaskStatusVO {
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  imageUrls?: string[]
  errorMessage?: string
}

/**
 * imageApi —— AI 智能配图 API 集合。
 */
export const imageApi = {
  /**
   * 从文案内容中提取 AI 绘图提示词。
   *
   * 使用 Qwen LLM 分析文案主题和场景，
   * 生成一段适合文生图模型的英文描述。
   *
   * @param text 文案正文
   */
  extractPrompt: (text: string) =>
    post<ImagePromptVO>('/image/extract-prompt', { text }),

  /**
   * 提交 AI 文生图异步任务。
   *
   * 调用 DashScope wanx 模型开始生成图片，
   * 返回任务 ID 供后续轮询使用。
   *
   * @param prompt 英文绘图提示词
   */
  generate: (prompt: string) =>
    post<{ taskId: string }>('/image/generate', { prompt }),

  /**
   * 查询文生图任务状态。
   *
   * 前端每隔 3 秒调用一次，直到状态变为 SUCCEEDED 或 FAILED。
   *
   * @param taskId DashScope 异步任务 ID
   */
  getStatus: (taskId: string) =>
    get<ImageTaskStatusVO>(`/image/generate/status/${taskId}`),

  /**
   * 下载远程图片到 MinIO 保存为素材。
   *
   * 将 AI 生成的临时图片 URL 下载到本地存储，
   * 创建 MediaAsset 记录使其成为正式素材。
   *
   * @param imageUrl 远程图片 URL
   * @param tags 标签（默认 "AI生成"）
   */
  download: (imageUrl: string, tags = 'AI生成') =>
    post<MediaAssetVO>('/image/download', { imageUrl, tags }),
}
