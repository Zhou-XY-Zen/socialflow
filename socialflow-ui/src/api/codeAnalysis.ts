/**
 * 代码分析 API 客户端 —— 对应后端 CodeAnalysisController。
 *
 * 注意：所有主键 id 是 Java Long 雪花 ID（19 位），JS Number 精度只能到 16 位，
 * 后端 Jackson 已配置 Long → String 序列化，前端也必须用 string 接，
 * 避免 `Number("2045867682091651074")` 精度丢失导致"记录不存在"。
 */
import { get, post, put, del } from './http'
import http from './http'
import type {
  AnalysisStats,
  AnalyzeRepoDTO,
  CodeAnalysis,
  FindingStatusDTO,
  LlmCallLog,
  RepoAuthCredential,
  RepoBookmark,
  RepoCommit,
  RepoCredentialProject,
  RuleLibraryItem,
  SaveBookmarkDTO,
  SaveCredentialDTO,
  SaveCredentialProjectDTO,
  SaveRuleDTO,
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

  /**
   * 导出分析结果为文件（MD / HTML / PDF）。
   * 后端返回 byte[] + Content-Disposition，用 Blob 触发浏览器下载；
   * 文件名优先从响应头的 `filename*=UTF-8''xxx` 解析，失败回退到默认名。
   */
  exportFile: async (id: Id, format: 'markdown' | 'html' | 'pdf') => {
    const res = await http.get(`/code-analysis/${id}/export`, {
      params: { format },
      responseType: 'blob',
    })
    const filename = parseFilenameFromDisposition(
      res.headers['content-disposition'] as string | undefined,
    ) || `code-analysis-${id}.${format === 'markdown' ? 'md' : format}`
    triggerDownload(res.data as Blob, filename)
  },

  // 查某次分析的 LLM 调用链路
  llmCalls: (id: Id) => get<LlmCallLog[]>(`/code-analysis/${id}/llm-calls`),

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

  // 仓库凭证
  listCredentials: () => get<RepoAuthCredential[]>('/code-analysis/credential'),
  saveCredential: (dto: SaveCredentialDTO) =>
    post<RepoAuthCredential, SaveCredentialDTO>('/code-analysis/credential', dto),
  deleteCredential: (id: Id) => del<void>(`/code-analysis/credential/${id}`),
  testCredential: (id: Id) =>
    post<RepoAuthCredential>(`/code-analysis/credential/${id}/test`),

  // 凭证下的仓库项目（一对多）
  listCredentialProjects: (credentialId: Id) =>
    get<RepoCredentialProject[]>(`/code-analysis/credential/${credentialId}/projects`),
  saveCredentialProject: (credentialId: Id, dto: SaveCredentialProjectDTO) =>
    post<RepoCredentialProject, SaveCredentialProjectDTO>(
      `/code-analysis/credential/${credentialId}/projects`, dto),
  deleteCredentialProject: (projectId: Id) =>
    del<void>(`/code-analysis/credential/project/${projectId}`),

  // 规约库（Wave 7：从 API 加载，支持启停 / 自定义）
  listRules: (params?: { topCategory?: string; level?: string; keyword?: string; enabledOnly?: boolean }) =>
    get<RuleLibraryItem[]>('/code-analysis/rules', { params }),
  toggleRuleEnabled: (id: Id, enabled: number) =>
    put<void>(`/code-analysis/rules/${id}/enabled?enabled=${enabled}`),
  saveRule: (dto: SaveRuleDTO) =>
    post<RuleLibraryItem, SaveRuleDTO>('/code-analysis/rules', dto),
  deleteRule: (id: Id) => del<void>(`/code-analysis/rules/${id}`),
}

/** 从 Content-Disposition 头解析文件名，优先 RFC 5987 的 filename*=UTF-8'' */
function parseFilenameFromDisposition(disp?: string): string | undefined {
  if (!disp) return undefined
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disp)
  if (star) {
    try { return decodeURIComponent(star[1]) } catch { /* ignore */ }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disp)
  return plain ? plain[1] : undefined
}

/** 触发浏览器下载 Blob */
function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
