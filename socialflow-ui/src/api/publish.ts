/**
 * ============================================================
 * api/publish.ts —— 发布分发相关 API 接口
 * ============================================================
 * 本文件封装了所有与"内容发布和分发"相关的后端接口调用。
 * 包括：准备辅助发布资源（格式化文案）、查询发布任务列表。
 * ============================================================
 */

import { get, post } from './http'

/**
 * 辅助发布准备结果
 */
export interface PublishResult {
  success: boolean
  status: string
  publishedUrl?: string
  errorMessage?: string
  /** 辅助发布模式下，bundleUrl 字段存放格式化后的文案文本 */
  bundleUrl?: string
}

/**
 * 发布任务
 */
export interface PublishTaskVO {
  id: number
  contentId: number
  platformAccountId?: number
  publishType: string
  status: string
  scheduledTime?: string
  executedTime?: string
  resultMsg?: string
  retryCount: number
  createTime?: string
}

/**
 * publishApi —— 发布分发 API 集合对象。
 */
export const publishApi = {
  /**
   * 准备辅助发布资源
   * @param contentId 要发布的内容 ID
   * @returns 发布准备结果，bundleUrl 中包含格式化后的文案
   */
  prepare: (contentId: number) =>
    post<PublishResult>('/publish/prepare', { contentId }),

  /**
   * 查询当前用户的发布任务列表
   * @returns 发布任务数组
   */
  tasks: () =>
    get<PublishTaskVO[]>('/publish/tasks'),

  /**
   * 自动发布到指定平台
   * @param contentId 要发布的内容 ID
   * @param platform 目标平台类型（如 WECHAT_MP）
   * @returns 发布结果
   */
  autoPublish: (contentId: string | number, platform: string) =>
    post<PublishResult>('/publish/auto', { contentId, platform }),
}
