/**
 * ============================================================
 * api/note.ts —— 知识中枢（笔记 / 分类 / 导入流水线）
 * ============================================================
 * 后端模块：socialflow-service-note
 * 路由前缀：/api/v1/notes 与 /api/v1/notes/import
 *
 * ID 用 string —— 后端雪花 Long 通过 JacksonConfig.longToStringCustomizer
 * 序列化为字符串，避免 19 位精度丢失。
 * ============================================================
 */

import { del, get, post, put } from './http'
import type {
  NoteVO, NoteCreateDTO, NoteUpdateDTO, NoteQueryDTO,
  NoteCategoryVO, NoteCategoryUpsertDTO,
  NoteImportTaskVO, NoteImportItemUpdateDTO, NoteImportCommitVO,
  PageResult,
} from '@/types/api'

/* ====================== 笔记 CRUD ====================== */

export const noteApi = {
  list:    (q: NoteQueryDTO)            => post<PageResult<NoteVO>>('/notes/list', q),
  get:     (id: string)                  => get<NoteVO>(`/notes/${id}`),
  create:  (dto: NoteCreateDTO)         => post<NoteVO>('/notes', dto),
  update:  (id: string, d: NoteUpdateDTO) => put<NoteVO>(`/notes/${id}`, d),
  trash:   (id: string)                  => post<void>(`/notes/${id}/trash`),
  restore: (id: string)                  => post<void>(`/notes/${id}/restore`),
  remove:  (id: string)                  => del<void>(`/notes/${id}`),
  togglePin:    (id: string)             => post<void>(`/notes/${id}/pin`),
  togglePublic: (id: string)             => post<void>(`/notes/${id}/public`),
}

/* ====================== 分类 ====================== */

export const noteCategoryApi = {
  tree:   ()                                  => get<NoteCategoryVO[]>('/notes/categories'),
  create: (dto: NoteCategoryUpsertDTO)        => post<NoteCategoryVO>('/notes/categories', dto),
  update: (id: string, dto: NoteCategoryUpsertDTO) => put<NoteCategoryVO>(`/notes/categories/${id}`, dto),
  delete: (id: string)                        => del<void>(`/notes/categories/${id}`),
}

/* ====================== 导入流水线 ====================== */

export const noteImportApi = {
  /** P0 单文件同步导入：直接返回入库后的 NoteVO */
  importSingle: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return post<NoteVO>('/notes/import/single', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /**
   * P1 批量异步导入
   *  - 单 ZIP 自动解包（Notion / Obsidian / 语雀）
   *  - 文件夹：浏览器拆成 MultipartFile[]，path 在 originalFilename 里
   *  - 立即返回 taskId（字符串）；前端用 useImportSse 订阅进度
   */
  importBatch: (files: File[]) => {
    const fd = new FormData()
    files.forEach(f => fd.append('files', f))
    return post<string>(`/notes/import/batch?enrichEnabled=false`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  listTasks: () => get<NoteImportTaskVO[]>('/notes/import/tasks'),

  getTask:   (taskId: string) => get<NoteImportTaskVO>(`/notes/import/tasks/${taskId}`),

  updateItem: (taskId: string, itemId: string, dto: NoteImportItemUpdateDTO) =>
    put<void>(`/notes/import/tasks/${taskId}/items/${itemId}`, dto),

  commit:  (taskId: string) => post<NoteImportCommitVO>(`/notes/import/tasks/${taskId}/commit`),
  cancel:  (taskId: string) => post<void>(`/notes/import/tasks/${taskId}/cancel`),
}
