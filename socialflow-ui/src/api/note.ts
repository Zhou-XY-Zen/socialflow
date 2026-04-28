/**
 * ============================================================
 * api/note.ts —— 知识中枢（笔记 / 分类 / 标签 / 导入流水线）
 * ============================================================
 * 后端模块：socialflow-service-note
 * 路由前缀：/api/v1/notes 与 /api/v1/notes/import
 * ============================================================
 */

import { del, get, post, put } from './http'
import type {
  NoteVO, NoteCreateDTO, NoteUpdateDTO, NoteQueryDTO,
  NoteCategoryVO, NoteCategoryUpsertDTO,
  NoteTagVO, NoteLinkVO,
  NoteImportTaskVO, NoteImportItemUpdateDTO,
  PageResult,
} from '@/types/api'

/* ====================== 笔记 CRUD ====================== */

export const noteApi = {
  list:    (q: NoteQueryDTO)         => post<PageResult<NoteVO>>('/notes/list', q),
  get:     (id: number)              => get<NoteVO>(`/notes/${id}`),
  create:  (dto: NoteCreateDTO)      => post<NoteVO>('/notes', dto),
  update:  (id: number, d: NoteUpdateDTO) => put<NoteVO>(`/notes/${id}`, d),
  trash:   (id: number)              => post<void>(`/notes/${id}/trash`),
  restore: (id: number)              => post<void>(`/notes/${id}/restore`),
  remove:  (id: number)              => del<void>(`/notes/${id}`),
  togglePin:    (id: number)         => post<void>(`/notes/${id}/pin`),
  togglePublic: (id: number)         => post<void>(`/notes/${id}/public`),
  backlinks:    (id: number)         => get<NoteLinkVO[]>(`/notes/${id}/backlinks`),
  forwardLinks: (id: number)         => get<NoteLinkVO[]>(`/notes/${id}/forward-links`),
  graphEdges:   ()                   => get<NoteLinkVO[]>('/notes/graph/edges'),
}

/* ====================== 分类 ====================== */

export const noteCategoryApi = {
  tree:   ()                                  => get<NoteCategoryVO[]>('/notes/categories'),
  create: (dto: NoteCategoryUpsertDTO)        => post<NoteCategoryVO>('/notes/categories', dto),
  update: (id: number, dto: NoteCategoryUpsertDTO) => put<NoteCategoryVO>(`/notes/categories/${id}`, dto),
  delete: (id: number)                        => del<void>(`/notes/categories/${id}`),
}

/* ====================== 标签 ====================== */

export const noteTagApi = {
  list:   ()                              => get<NoteTagVO[]>('/notes/tags'),
  rename: (id: number, name: string)      => put<void>(`/notes/tags/${id}?name=${encodeURIComponent(name)}`),
  delete: (id: number)                    => del<void>(`/notes/tags/${id}`),
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
   *  - 立即返回 taskId；前端用 useImportSse 订阅 /notes/import/tasks/:id/stream 看进度
   */
  importBatch: (files: File[], enrichEnabled = true) => {
    const fd = new FormData()
    files.forEach(f => fd.append('files', f))
    return post<number>(`/notes/import/batch?enrichEnabled=${enrichEnabled}`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  listTasks: () => get<NoteImportTaskVO[]>('/notes/import/tasks'),

  getTask:   (taskId: number) => get<NoteImportTaskVO>(`/notes/import/tasks/${taskId}`),

  updateItem: (taskId: number, itemId: number, dto: NoteImportItemUpdateDTO) =>
    put<void>(`/notes/import/tasks/${taskId}/items/${itemId}`, dto),

  commit:  (taskId: number) => post<void>(`/notes/import/tasks/${taskId}/commit`),
  cancel:  (taskId: number) => post<void>(`/notes/import/tasks/${taskId}/cancel`),
}
