/**
 * ============================================================
 * api/knowledge.ts —— 知识库相关 API 接口
 * ============================================================
 * 本文件封装了所有与"知识库（Knowledge Base）"相关的后端接口。
 * 知识库是 RAG（检索增强生成）系统的数据源——
 * 用户上传文档（PDF/Word 等），后端将文档切分为小块并生成向量嵌入，
 * AI 生成内容时可以检索这些文档片段作为参考，提高内容的准确性和专业性。
 * ============================================================
 */

import { get, post, del } from './http'
import type { ChunkSearchVO, KbDocVO, KbVO } from '@/types/api'

/**
 * kbApi —— 知识库 API 集合对象。
 */
export const kbApi = {
  /**
   * 创建新的知识库
   * @param dto 包含名称、描述、分类的对象
   * @returns 创建成功后返回 KbVO
   */
  create: (dto: { name: string; description?: string; category?: string }) =>
    post<KbVO>('/kb', dto),

  /**
   * 获取当前用户的所有知识库列表
   * @returns KbVO 数组
   */
  list: () => get<KbVO[]>('/kb/list'),

  /**
   * 获取单个知识库的详细信息
   * @param id 知识库 ID
   */
  get: (id: number) => get<KbVO>(`/kb/${id}`),

  /**
   * 删除知识库
   * @param id 知识库 ID
   */
  delete: (id: number) => del<void>(`/kb/${id}`),

  /**
   * 向知识库上传文档
   * @param kbId 目标知识库 ID
   * @param file 要上传的文件（浏览器 File 对象）
   * @returns 返回新建文档的 ID
   *
   * 使用 FormData 构建 multipart/form-data 格式的请求体，
   * 这是浏览器上传文件的标准方式。
   */
  upload: (kbId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return post<number>(`/kb/${kbId}/upload`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /**
   * 知识库语义搜索 —— 根据查询文本检索最相关的文档片段。
   * @param kbId        知识库 ID
   * @param query       查询文本
   * @param topK        返回最相关的前 K 条结果（默认 5）
   * @param enableRerank 是否启用重排序（Rerank）来提高结果精度（默认 true）
   * @returns ChunkSearchVO 数组（按相关度排序）
   */
  search: (kbId: number, query: string, topK = 5, enableRerank = true) =>
    post<ChunkSearchVO[]>(`/kb/${kbId}/search`, { query, topK, enableRerank }),

  /**
   * 获取知识库中的文档列表
   * @param kbId 知识库 ID
   * @returns KbDocVO 数组
   */
  listDocs: (kbId: number) => get<KbDocVO[]>(`/kb/${kbId}/docs`),

  /**
   * 删除知识库中的某个文档
   * @param kbId  知识库 ID
   * @param docId 文档 ID
   */
  deleteDoc: (kbId: number, docId: number) => del<void>(`/kb/${kbId}/doc/${docId}`),
}
