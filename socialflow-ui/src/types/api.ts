/**
 * ============================================================
 * types/api.ts —— API 数据类型定义
 * ============================================================
 * 【什么是 interface？】
 * TypeScript 中的 interface（接口）用来描述一个对象的"形状"——
 * 它有哪些属性、每个属性是什么类型。interface 本身不会产生 JS 代码，
 * 它只在编译阶段用于类型检查，帮助你在写代码时发现拼写错误或类型不匹配。
 *
 * 【什么是泛型 <T>？】
 * 泛型（Generic）是 TypeScript 中最强大的特性之一。
 * 你可以把 <T> 理解为"类型参数"——就像函数参数一样，但传的是类型。
 * 例如 R<string> 表示 data 字段是 string 类型，
 *      R<UserVO> 表示 data 字段是 UserVO 类型。
 * 这样一个 interface 就可以适配不同的 API 返回值。
 *
 * 【命名约定】
 *   - VO（View Object）：后端返回给前端的数据结构
 *   - DTO（Data Transfer Object）：前端发送给后端的请求体
 *   - 属性名后面加 ? 表示该属性是可选的（可以不存在）
 *
 * 本文件定义了项目中所有与后端 API 交互的数据类型。
 * ============================================================
 */

/**
 * R<T> —— 通用 API 响应包装类型。
 * 后端所有接口都会返回这样的 JSON 结构：
 *   { code: 200, message: "ok", data: ..., timestamp: 1700000000 }
 *
 * 泛型 T 代表 data 字段的实际类型，调用时指定。
 * 例如：R<UserVO> 表示 data 是一个 UserVO 对象。
 */
export interface R<T> {
  code: number       // 业务状态码，200 表示成功
  message: string    // 提示信息（成功时通常为 "ok"，失败时为错误描述）
  data: T            // 实际的业务数据，类型由泛型 T 决定
  timestamp: number  // 服务器时间戳
}

/**
 * PageResult<T> —— 分页查询结果。
 * 当接口返回的是分页列表时，data 字段的类型就是 PageResult<某VO>。
 *
 * 泛型 T 代表列表中每条记录的类型。
 */
export interface PageResult<T> {
  records: T[]    // 当前页的数据记录数组
  total: number   // 总记录数（用于计算总页数）
  pageNum: number  // 当前页码（从 1 开始）
  pageSize: number // 每页条数
}

/**
 * UserVO —— 用户信息（后端返回）。
 */
export interface UserVO {
  id: number           // 用户 ID（主键）
  email: string        // 邮箱
  nickname: string     // 昵称
  avatarUrl?: string   // 头像地址（可选，可能为空）
  status: number       // 账号状态（0=禁用, 1=正常 等）
}

/**
 * LoginVO —— 登录成功后的响应数据。
 * 包含 JWT Token 和用户基本信息。
 */
export interface LoginVO {
  token: string      // JWT 认证令牌，后续请求需放在 Authorization 头中
  expiresIn: number  // Token 有效期（秒）
  user: UserVO       // 当前登录用户的信息
}

/**
 * ContentGenerateDTO —— 内容生成请求体（前端 → 后端）。
 * 用户在"工作台"页面填写表单后，将此对象发送给后端来触发 AI 生成。
 *
 * 注意：'XIAOHONGSHU' | 'DOUYIN' | ... 这种写法叫做"字面量联合类型"，
 * 表示 platform 的值只能是这几个字符串之一，写错会被 TypeScript 提示报错。
 */
export interface ContentGenerateDTO {
  topic: string                                                       // 主题 / 话题描述
  keywords?: string[]                                                 // 关键词列表（可选）
  platform: 'XIAOHONGSHU' | 'DOUYIN' | 'WECHAT_MOMENT' | 'WECHAT_MP' // 目标发布平台
  templateId?: number                                                  // 使用的 Prompt 模板 ID（可选）
  tone?: 'casual' | 'professional' | 'humorous' | 'inspiring'         // 文案语气/风格（可选）
  wordCount?: number                                                   // 期望字数（可选）
  productInfo?: string                                                 // 产品信息描述（可选，用于带货文案）
  model?: string                                                       // 指定 AI 模型（可选，如 gpt-4）
  kbId?: number                                                        // 关联的知识库 ID（可选，启用 RAG）
  enableGuardrails?: boolean                                           // 是否启用内容安全护栏
  temperature?: number                                                 // 生成温度（0~1，越高越随机）
}

/**
 * ContentVO —— 生成的内容详情（后端返回）。
 */
export interface ContentVO {
  id: number                     // 内容 ID（主键）
  title?: string                 // 标题（可选）
  body: string                   // 正文内容
  platform: string               // 目标平台
  status: string                 // 内容状态（DRAFT / PUBLISHED 等）
  model?: string                 // 使用的 AI 模型名称
  tokenUsage?: number            // 消耗的 Token 数量
  tags?: string                  // 标签（逗号分隔的字符串）
  evalScore?: number             // 质量评估分数
  scheduledTime?: string         // 计划发布时间
  publishedTime?: string         // 实际发布时间
  createTime?: string            // 创建时间
  guardrailWarnings?: string[]   // 内容安全警告列表（如果触发了护栏规则）
  ragSources?: RagSourceVO[]     // RAG 引用的知识库来源片段
  /** Wave 3.4: 实际使用的 LLM provider（fallback 时与请求时不同） */
  providerUsed?: string
  /** Wave 3.4: 是否走了 fallback 路径，true 时前端可显示"已切换到备用模型" */
  fallback?: boolean
  /** 文案版本号（Wave 4.3 乐观锁） */
  version?: number
}

/**
 * RagSourceVO —— RAG（检索增强生成）引用的来源信息。
 * 当 AI 生成内容时参考了知识库中的文档，后端会返回引用的片段信息。
 */
export interface RagSourceVO {
  chunkId: number      // 文本块 ID
  docId: number        // 文档 ID
  docName?: string     // 文档名称
  page?: number        // 所在页码
  snippet?: string     // 引用的文本片段
  score?: number       // 向量相似度分数（越高越相关）
}

/**
 * KbVO —— 知识库信息（后端返回）。
 * 知识库是 RAG 系统的核心，用户上传文档后，系统会将文档切分为小块（chunk），
 * 并通过嵌入模型（Embedding Model）转化为向量，存入向量数据库。
 */
export interface KbVO {
  id: number             // 知识库 ID（主键）
  name: string           // 知识库名称
  description?: string   // 描述信息
  category?: string      // 分类标签
  docCount: number       // 包含的文档数量
  chunkCount: number     // 包含的文本块数量
  embeddingModel: string // 使用的嵌入模型名称（如 text-embedding-3-small）
  embeddingDim: number   // 嵌入向量维度（如 1536）
  status: number         // 状态（0=创建中, 1=就绪 等）
  createTime?: string    // 创建时间
}

/**
 * KbDocVO —— 知识库文档信息（后端返回）。
 * 记录用户上传到知识库中的文档元信息和解析状态。
 */
export interface KbDocVO {
  id: number              // 文档 ID（主键）
  kbId: number            // 所属知识库 ID
  fileName: string        // 原始文件名
  fileType: string        // 文件类型（pdf / docx / txt 等）
  fileSize: number        // 文件大小（字节）
  charCount?: number      // 文档字符数
  chunkCount?: number     // 切片数量
  parseStatus: string     // 解析状态：PENDING / PARSING / COMPLETED / FAILED
  parseError?: string     // 解析失败原因
  createTime?: string     // 创建时间
}

/**
 * ChunkSearchVO —— 知识库搜索结果中的单个文本块。
 * 用户在知识库中输入查询后，系统通过向量检索返回最相关的文本块。
 */
export interface ChunkSearchVO {
  chunkId: number       // 文本块 ID
  docId: number         // 所属文档 ID
  kbId: number          // 所属知识库 ID
  chunkIndex: number    // 在文档中的块序号
  contentText: string   // 文本块的实际内容
  docName?: string      // 所属文档名称
  page?: number         // 所在页码
  score?: number        // 向量相似度分数
  /** Wave 4.1: 命中片段的 ±80 字符摘录（前端可高亮） */
  snippet?: string
}

// ====================================================================
// 以下为 Wave 3-4 后端新增 API 对应的 VO（Wave 3.2 Dashboard / Wave 4.x）
// ====================================================================

/** Wave 3.2 Dashboard 总览 VO */
export interface DashboardOverviewVO {
  contentTotal: number
  contentByStatus: Record<string, number>
  publishByStatus: Record<string, number>
  scheduledPending: number
  aiCalls7d: number
  aiTokens7d: number
  aiCost7d: number
  aiUsageTrend: Array<{ date: string; calls: number; tokens: number; cost: number }>
  kbTotal: number
  mediaTotal: number
}

/** Wave 4.4 模板预览返回 */
export interface TemplatePreviewVO {
  renderedSystemPrompt: string
  renderedUserPrompt: string
  declaredVariables: string[]
  usedVariables: string[]
  missingVariables: string[]
  unusedVariables: string[]
}

/* ====================================================================
 * 知识中枢（service-note）类型
 * ==================================================================== */

/* 注：所有 ID 字段都用 string —— 后端 Jackson 把雪花 Long 序列化为 String，
 *    避免 19 位数字超出 JS Number.MAX_SAFE_INTEGER (2^53) 时丢精度 */

export interface NoteVO {
  id: string
  title: string
  summary?: string
  contentMd?: string
  aiOutline?: string
  categoryId?: string
  categoryName?: string
  tags?: string[]
  wordCount?: number
  readScore?: number
  isPinned?: number
  isPublic?: number
  slug?: string
  status: number              // 1 normal 2 draft 3 trashed
  sourceType?: string         // manual / upload / url / clip
  publishedAt?: string
  createTime?: string
  updateTime?: string
}

export interface NoteCategoryVO {
  id: string
  parentId?: string
  name: string
  sortOrder?: number
  color?: string
  noteCount?: number
  children?: NoteCategoryVO[]
}

export interface NoteTagVO {
  id: string
  name: string
  usageCount?: number
}

export interface NoteCreateDTO {
  title: string
  contentMd: string
  summary?: string
  categoryId?: string
  tags?: string[]
  isPinned?: number
  isPublic?: number
  status?: number
}

export type NoteUpdateDTO = Partial<NoteCreateDTO>

export interface NoteQueryDTO {
  keyword?: string
  categoryId?: string
  tagIds?: string[]
  status?: number
  sortBy?: 'pinned-first' | 'updated' | 'created'
  pageNum?: number
  pageSize?: number
}

export interface NoteCategoryUpsertDTO {
  parentId?: string
  name: string
  sortOrder?: number
  color?: string
}

export interface NoteImportItemVO {
  id: string
  taskId: string
  fileName: string
  filePath: string
  fileSize: number
  parseStatus: string
  enrichStatus: string
  parsedTitle?: string
  parsedMd?: string
  aiPayload?: string
  conflictWithNoteId?: string
  conflictWithNoteTitle?: string
  resolution: string
  finalNoteId?: string
  errorMsg?: string
}

export interface NoteImportTaskVO {
  id: string
  sourceType: string
  sourceName?: string
  totalFiles: number
  processedFiles: number
  failedFiles: number
  status: string
  enrichEnabled?: number
  createTime?: string
  finishedAt?: string
  items?: NoteImportItemVO[]
}

export interface NoteLinkVO {
  srcNoteId: string
  srcTitle?: string
  dstNoteId: string
  dstTitle?: string
  linkType: 'explicit' | 'semantic'
  similarity?: number
}

export interface NoteImportItemUpdateDTO {
  parsedTitle?: string
  summary?: string
  tags?: string[]
  categoryId?: string
  isPublic?: number
  resolution?: 'skip' | 'create' | 'overwrite' | 'merge'
}

/** Wave 4.5 媒体素材增加的字段（已经在 MediaAsset 上自动返回，为前端展示加类型） */
export interface MediaAssetExt {
  id: number
  userId: number
  fileName: string
  fileType: string
  mimeType: string
  fileUrl: string
  thumbnailUrl?: string
  fileSize: number
  tags?: string
  vectorId?: string
  createTime?: string
  /** Wave 4.5: 文件 SHA-256（去重 key） */
  sha256?: string
  /** Wave 4.5: 图像宽度 */
  width?: number
  /** Wave 4.5: 图像高度 */
  height?: number
}
