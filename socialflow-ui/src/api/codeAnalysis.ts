/**
 * 代码分析 API 客户端 —— 对应后端 CodeAnalysisController。
 *
 * 注意：所有主键 id 是 Java Long 雪花 ID（19 位），JS Number 精度只能到 16 位，
 * 后端 Jackson 已配置 Long → String 序列化，前端也必须用 string 接，
 * 避免 `Number("2045867682091651074")` 精度丢失导致"记录不存在"。
 */
import { get, post, put, del } from './http'
import type {
  AnalysisStats,
  AnalyzeRepoDTO,
  CodeAnalysis,
  FindingStatusDTO,
  RepoBookmark,
  RepoCommit,
  SaveBookmarkDTO,
} from '@/types/codeAnalysis'

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

/** id 入参类型 —— 兼容 string（规范）和 number（小 id 也能用） */
type Id = string | number

export const codeAnalysisApi = {
  // 仪表盘
  dashboardStats: () => get<AnalysisStats>('/code-analysis/dashboard/stats'),

  // 项目概览
  triggerProject: (dto: AnalyzeRepoDTO) =>
    post<{ id: string }, AnalyzeRepoDTO>('/code-analysis/project', dto),

  // 提交审查
  listCommits: (dto: AnalyzeRepoDTO) =>
    post<RepoCommit[], AnalyzeRepoDTO>('/code-analysis/commits', dto),
  triggerReview: (dto: AnalyzeRepoDTO) =>
    post<{ id: string }, AnalyzeRepoDTO>('/code-analysis/review', dto),

  // 对比分析
  triggerDiff: (dto: AnalyzeRepoDTO) =>
    post<{ id: string }, AnalyzeRepoDTO>('/code-analysis/diff', dto),

  // 通用：查结果
  get: (id: Id) => get<CodeAnalysis>(`/code-analysis/${id}`),

  // 历史
  history: (params: { current?: number; size?: number; analysisType?: string; keyword?: string }) =>
    get<PageResult<CodeAnalysis>>('/code-analysis/history', { params }),
  delete: (id: Id) => del<void>(`/code-analysis/${id}`),
  toggleFavorite: (id: Id) => post<void>(`/code-analysis/${id}/favorite`),
  share: (id: Id) => post<{ shareToken: string }>(`/code-analysis/${id}/share`),
  shared: (token: string) => get<CodeAnalysis>(`/code-analysis/shared/${token}`),

  // Finding 状态
  updateFindingStatus: (findingId: Id, dto: FindingStatusDTO) =>
    put<void, FindingStatusDTO>(`/code-analysis/finding/${findingId}/status`, dto),

  // 书签
  listBookmarks: () => get<RepoBookmark[]>('/code-analysis/bookmark'),
  saveBookmark: (dto: SaveBookmarkDTO) =>
    post<RepoBookmark, SaveBookmarkDTO>('/code-analysis/bookmark', dto),
  deleteBookmark: (id: Id) => del<void>(`/code-analysis/bookmark/${id}`),
}
