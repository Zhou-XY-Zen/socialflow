/**
 * 代码分析 API 客户端 —— 对应后端 CodeAnalysisController。
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

export const codeAnalysisApi = {
  // 仪表盘
  dashboardStats: () => get<AnalysisStats>('/code-analysis/dashboard/stats'),

  // 项目概览
  triggerProject: (dto: AnalyzeRepoDTO) =>
    post<{ id: number }, AnalyzeRepoDTO>('/code-analysis/project', dto),

  // 提交审查
  listCommits: (dto: AnalyzeRepoDTO) =>
    post<RepoCommit[], AnalyzeRepoDTO>('/code-analysis/commits', dto),
  triggerReview: (dto: AnalyzeRepoDTO) =>
    post<{ id: number }, AnalyzeRepoDTO>('/code-analysis/review', dto),

  // 对比分析
  triggerDiff: (dto: AnalyzeRepoDTO) =>
    post<{ id: number }, AnalyzeRepoDTO>('/code-analysis/diff', dto),

  // 通用：查结果
  get: (id: number) => get<CodeAnalysis>(`/code-analysis/${id}`),

  // 历史
  history: (params: { current?: number; size?: number; analysisType?: string; keyword?: string }) =>
    get<PageResult<CodeAnalysis>>('/code-analysis/history', { params }),
  delete: (id: number) => del<void>(`/code-analysis/${id}`),
  toggleFavorite: (id: number) => post<void>(`/code-analysis/${id}/favorite`),
  share: (id: number) => post<{ shareToken: string }>(`/code-analysis/${id}/share`),
  shared: (token: string) => get<CodeAnalysis>(`/code-analysis/shared/${token}`),

  // Finding 状态
  updateFindingStatus: (findingId: number, dto: FindingStatusDTO) =>
    put<void, FindingStatusDTO>(`/code-analysis/finding/${findingId}/status`, dto),

  // 书签
  listBookmarks: () => get<RepoBookmark[]>('/code-analysis/bookmark'),
  saveBookmark: (dto: SaveBookmarkDTO) =>
    post<RepoBookmark, SaveBookmarkDTO>('/code-analysis/bookmark', dto),
  deleteBookmark: (id: number) => del<void>(`/code-analysis/bookmark/${id}`),
}
