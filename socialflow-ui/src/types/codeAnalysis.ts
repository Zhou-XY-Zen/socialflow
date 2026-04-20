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
/** Wave 8 关闭原因：用户标 IGNORED / RESOLVED 时附带 */
export type FindingDismissedReason = 'INVALID' | 'ALREADY_FIXED' | 'NOT_APPLICABLE' | 'OTHER'

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
  dismissedReason?: FindingDismissedReason
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
  // LLM Token 消耗（Wave 5.5 新增）
  tokensMonthly?: number
  tokensMonthlyPrompt?: number
  tokensMonthlyCompletion?: number
  llmCallsMonthly?: number
  tokensPerAnalysisAvg?: number
  // Wave 8 反馈闭环
  feedbackInvalidCount?: number
  feedbackIgnoredCount?: number
  falsePositiveRate?: number
  dismissedRulesCount?: number
  topInvalidRules?: Array<{ ruleRef: string; count: number }>
}

/** LLM 调用日志（分析详情页里展开查看链路） */
export interface LlmCallLog {
  id: string
  analysisId: string
  stage: string
  stageLabel?: string
  provider: string
  model: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  latencyMs?: number
  success: number            // 1 成功 / 0 失败
  errorMsg?: string
  createTime?: string
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
  dismissedReason?: FindingDismissedReason
}

export type CredentialAuthType = 'TOKEN' | 'PASSWORD'

/** Git 仓库凭证（返回给前端时 token 永远是掩码）*/
export interface RepoAuthCredential {
  id: string
  nickname: string
  gitHost: string
  authType?: CredentialAuthType
  username: string
  tokenHint?: string        // TOKEN: ghp_****f8a；PASSWORD: ********
  defaultRepoUrl?: string   // 常用仓库 URL，测试连接 + 快速分析用
  isDefault?: number        // 0 / 1
  testStatus?: 'UNKNOWN' | 'SUCCESS' | 'FAILED'
  testMessage?: string
  lastUsedAt?: string
  createTime?: string
}

export interface SaveCredentialDTO {
  id?: string | number
  nickname: string
  gitHost?: string       // 可选，后端会从 defaultRepoUrl 自动提取
  authType?: CredentialAuthType
  username: string
  token?: string        // 编辑时留空 = 不改；新增必填（token 或 password）
  defaultRepoUrl?: string
  isDefault?: number
}

/** 凭证下的仓库项目（一对多关系的"子"）*/
export interface RepoCredentialProject {
  id: string
  credentialId: string
  nickname?: string
  gitUrl: string
  branch: string
  lastUsedAt?: string
  createTime?: string
}

export interface SaveCredentialProjectDTO {
  id?: string | number
  nickname?: string
  gitUrl: string
  branch?: string
}

/** 规约库条目（Wave 7：从静态数据迁到 API 驱动）*/
export interface RuleLibraryItem {
  id: string                  // 雪花 ID 用 string 防 JS 精度丢失
  code: string                // 1.1.1 / 5.1.5
  topCategory: string         // 编程规约/异常日志/...
  subCategory?: string        // 命名风格/集合处理/...
  level: 'MANDATORY' | 'RECOMMENDED' | 'REFERENCE'
  title: string
  body?: string
  description?: string
  exampleGood?: string
  exampleBad?: string
  enabled: number             // 1 启用 / 0 禁用
  isCustom: number            // 1 用户自定义 / 0 黄山版内置
  source?: string
  createTime?: string
  updateTime?: string
}

export interface SaveRuleDTO {
  id?: string | number
  code: string
  topCategory: string
  subCategory?: string
  level: 'MANDATORY' | 'RECOMMENDED' | 'REFERENCE'
  title: string
  body?: string
  description?: string
  exampleGood?: string
  exampleBad?: string
  enabled?: number
}

