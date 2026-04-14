/**
 * ============================================================
 * api/content.ts —— 内容管理相关 API 接口
 * ============================================================
 * 本文件封装了所有与"AI 内容生成及管理"相关的后端接口调用。
 * 包括：普通生成、批量生成、改写、标题生成、话题标签建议、相似内容检索、
 * 列表查询、详情获取、更新和删除等。
 *
 * 注意：流式生成（SSE）不在这里，而是通过 composables/useSse.ts 直接调用。
 * ============================================================
 */

import http, { get, post, del } from './http'
import type { ContentGenerateDTO, ContentVO, PageResult, R } from '@/types/api'

/**
 * contentApi —— 内容管理 API 集合对象。
 */
export const contentApi = {
  /**
   * 生成单条内容（非流式，等待完整结果返回）
   * @param dto 内容生成参数（主题、平台、语气等）
   * @returns 生成的 ContentVO
   */
  generate: (dto: ContentGenerateDTO) =>
    post<ContentVO>('/content/generate', dto),

  /**
   * 批量生成 —— 同一主题同时为多个平台生成内容。
   * @param dto 在 ContentGenerateDTO 基础上额外带一个 platforms 数组。
   *   这里的 `&` 是 TypeScript 的"交叉类型（Intersection Type）"，
   *   意思是 dto 既满足 ContentGenerateDTO 的所有字段，又额外多一个 platforms 字段。
   * @returns Record<string, ContentVO> —— 以平台名为 key、内容为 value 的字典
   */
  generateBatch: (dto: ContentGenerateDTO & { platforms: string[] }) =>
    post<Record<string, ContentVO>>('/content/generate-batch', dto),

  /**
   * 改写已有内容 —— 将一条内容转换为另一个平台或语气风格。
   * @param contentId      要改写的内容 ID
   * @param targetPlatform 目标平台（可选）
   * @param targetTone     目标语气（可选）
   */
  rewrite: (contentId: number, targetPlatform?: string, targetTone?: string) =>
    post<ContentVO>('/content/rewrite', { contentId, targetPlatform, targetTone }),

  /**
   * AI 生成标题建议
   * @param body     正文内容
   * @param platform 目标平台
   * @param count    需要几个标题（默认 3）
   * @returns 标题字符串数组
   */
  generateTitles: (body: string, platform: string, count = 3) =>
    post<string[]>('/content/generate-title', null, { params: { body, platform, count } }),

  /**
   * AI 推荐话题标签（Hashtag）
   * @param body     正文内容
   * @param platform 目标平台
   * @param count    需要几个标签（默认 10）
   * @returns 话题标签字符串数组
   */
  suggestHashtags: (body: string, platform: string, count = 10) =>
    post<string[]>('/content/suggest-hashtags', null, { params: { body, platform, count } }),

  /**
   * 语义相似内容检索 —— 根据文本查找内容库中语义相似的历史内容。
   * @param text 查询文本
   * @param topK 返回最相似的前 K 条
   */
  similar: (text: string, topK = 5) =>
    post<ContentVO[]>('/content/similar', null, { params: { text, topK } }),

  /**
   * 分页查询内容列表
   * @param params 查询参数对象（如 pageNum, pageSize, platform, status 等）
   *   Record<string, unknown> 表示一个键为字符串、值为任意类型的对象
   * @returns 分页结果 PageResult<ContentVO>
   */
  list: (params: Record<string, unknown>) =>
    get<PageResult<ContentVO>>('/content/list', { params }),

  /**
   * 获取单条内容详情
   * @param id 内容 ID
   */
  get: (id: number) => get<ContentVO>(`/content/${id}`),

  /**
   * 更新内容（标题、正文、标签）
   * 注意：这里直接使用 http 实例而非 put 快捷函数，
   * 因为参数是通过 URL query 传递的（params），而非请求体（body）。
   */
  update: async (id: number, title: string, body: string, tags?: string) => {
    const res = await http.put<R<ContentVO>>(`/content/${id}`, null, {
      params: { title, body, tags },
    })
    return res.data.data
  },

  /**
   * 删除内容
   * @param id 内容 ID
   */
  delete: (id: number) => del<void>(`/content/${id}`),

  /**
   * 获取内容的版本历史列表
   * @param id 内容 ID
   * @returns 版本记录数组
   */
  versions: (id: number | string) => get<any[]>(`/content/${id}/versions`),

  /**
   * 获取内容导出下载 URL（直接返回地址，用于触发浏览器下载）
   * @param id 内容 ID
   * @returns 导出接口的完整路径
   */
  exportUrl: (id: number | string) => `/api/v1/content/${id}/export`,

  /**
   * 批量删除内容
   * @param ids 内容 ID 数组
   */
  batchDelete: (ids: number[]) => post<void>('/content/batch-delete', ids),

  /**
   * 批量导出内容（返回下载 URL）
   * @param ids 内容 ID 数组
   * @returns 导出接口的完整路径（带查询参数）
   */
  batchExportUrl: (ids: number[]) => `/api/v1/content/batch-export?ids=${ids.join(',')}`,
}
