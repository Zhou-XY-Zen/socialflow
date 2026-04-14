/**
 * ============================================================
 * api/media.ts —— 媒体素材库相关 API 接口
 * ============================================================
 * 本文件封装了所有与"媒体素材"相关的后端接口。
 * 媒体素材库用于管理用户上传的图片、视频等媒体资源，
 * 支持上传、分页浏览、按类型筛选、关键词搜索和删除操作。
 * ============================================================
 */

import http, { get, del } from './http'
import type { PageResult, R } from '@/types/api'

/**
 * MediaAssetVO —— 媒体素材信息（后端返回）。
 * 记录用户上传的图片或视频文件的元信息和访问 URL。
 */
export interface MediaAssetVO {
  id: string | number       // 素材 ID（主键）
  fileName: string          // 原始文件名
  fileType: string          // 文件类型：IMAGE / VIDEO
  mimeType: string          // MIME 类型，如 image/jpeg、video/mp4
  fileUrl: string           // 文件访问 URL（MinIO 地址）
  thumbnailUrl?: string     // 缩略图 URL（当前与 fileUrl 相同）
  fileSize: number          // 文件大小（字节）
  tags?: string             // 标签（逗号分隔）
  createTime?: string       // 创建时间
}

/**
 * mediaApi —— 媒体素材 API 集合对象。
 */
export const mediaApi = {
  /**
   * 上传媒体文件
   * @param file 浏览器 File 对象
   * @param tags 可选标签（逗号分隔字符串）
   * @returns 上传成功后返回 MediaAssetVO
   */
  upload: (file: File, tags?: string) => {
    const form = new FormData()
    form.append('file', file)
    if (tags) form.append('tags', tags)
    return http.post<R<MediaAssetVO>>('/media/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(res => res.data.data)
  },

  /**
   * 分页查询素材列表
   * @param params 查询参数（pageNum, pageSize, fileType, keyword）
   * @returns 分页结果
   */
  list: (params: Record<string, unknown>) =>
    get<PageResult<MediaAssetVO>>('/media/list', { params }),

  /**
   * 删除素材
   * @param id 素材 ID
   */
  delete: (id: string | number) => del<void>(`/media/${id}`),
}
