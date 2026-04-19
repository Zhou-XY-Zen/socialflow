/**
 * 代码分析模块类型定义。
 * 与后端 socialflow-model 中的 VO / DTO 一一对应。
 */

export type AnalysisType = 'PROJECT_OVERVIEW' | 'COMMIT_REVIEW' | 'DIFF_REVIEW'
export type AnalysisStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
export type FindingLevel = 'HIGH' | 'MEDIUM' | 'LOW'
export type FindingStatus = 'UNRESOLVED' | 'RESOLVED' | 'IGNORED'

/** 单条审查发现
 *  注意：id / analysisId 是 Java Long 雪花 ID（19 位），
 *  JS Number 精度只有 2^53-1（16 位），所以必须用 string 接。
 */
export interface CodeFinding {
  id: string
  analysisId: string
  level: FindingLevel
  category?: string
  title: string
  file?: string
  lineRange?: string
  description?: string
  suggestion?: string
  codeSnippet?: string
  ruleRef?: string
  status: FindingStatus
  resolutionNote?: string
}

/** 语言统计 */
export interface LanguageStat {
  language: string
  fileCount?: number
  totalLines: number
  percent: number
}

/** 代码分析结果 */
export interface CodeAnalysis {
  id: string
  gitUrl: string
  branch?: string
  commitSha?: string
  baseRef?: string
  headRef?: string
  analysisType: AnalysisType
  status: AnalysisStatus
  stage?: string
  progressPercent?: number
  progressMessage?: string
  overallScore?: number
  highCount?: number
  mediumCount?: number
  lowCount?: number
  summaryMd?: string
  techStack?: string[]
  languageStats?: LanguageStat[]
  mermaidCode?: string
  findings?: CodeFinding[]
  isFavorite?: number
  shareToken?: string
  tags?: string
  errorMsg?: string
  durationMs?: number
  llmTokensUsed?: number
  createTime?: string
}

/** 仓库 commit 摘要 */
export interface RepoCommit {
  sha: string
  shortSha: string
  author?: string
  email?: string
  commitTime?: string
  subject: string
  changedFiles?: number
  additions?: number
  deletions?: number
}

/** 仓库书签 */
export interface RepoBookmark {
  id: string
  nickname: string
  gitUrl: string
  branch?: string
  tags?: string
  lastAnalyzedAt?: string
  lastScore?: number
  createTime?: string
}

/** 仪表盘统计 */
export interface AnalysisStats {
  monthlyCount: number
  totalCount: number
  averageScore?: number
  totalHighRisk: number
  resolvedCount: number
  highTotal: number
  mediumTotal: number
  lowTotal: number
  dailyTrend: Array<{ date: string; count: number }>
  categoryStats: Array<{ category: string; count: number }>
  topRepos: Array<{ gitUrl: string; analyzeCount: number; lastScore?: number }>
}

/** 请求 DTO */
export interface AnalyzeRepoDTO {
  gitUrl: string
  branch?: string
  commitSha?: string
  baseRef?: string
  headRef?: string
  cloneDepth?: number
  excludeDirs?: string[]
  maxFiles?: number
}

export interface SaveBookmarkDTO {
  id?: string
  nickname: string
  gitUrl: string
  branch?: string
  tags?: string
}

export interface FindingStatusDTO {
  status: FindingStatus
  resolutionNote?: string
}

/** Git 仓库凭证（返回给前端时 token 永远是掩码）*/
export interface RepoAuthCredential {
  id: string
  nickname: string
  gitHost: string
  username: string
  tokenHint?: string        // ghp_****f8a
  isDefault?: number        // 0 / 1
  testStatus?: 'UNKNOWN' | 'SUCCESS' | 'FAILED'
  testMessage?: string
  lastUsedAt?: string
  createTime?: string
}

export interface SaveCredentialDTO {
  id?: string | number
  nickname: string
  gitHost: string
  username: string
  token?: string        // 编辑时留空 = 不改；新增必填
  isDefault?: number
}

