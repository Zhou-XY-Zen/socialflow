/**
 * ============================================================
 * api/eval.ts —— 评估中心相关 API 接口
 * ============================================================
 * 本文件封装了所有与"内容质量评估（A/B 测试）"相关的后端接口调用。
 * 包括：评估任务列表查询、创建任务、执行任务、获取评估报告等。
 * ============================================================
 */

import { get, post } from './http'

/**
 * EvalTaskVO —— 评估任务信息（后端返回）。
 * 每个评估任务代表一次 A/B 对比测试，
 * 包含两套生成配置（configA / configB）和若干测试主题。
 */
export interface EvalTaskVO {
  id: string | number
  name: string
  configA: string       // JSON 字符串，包含模型、温度等配置
  configB: string       // JSON 字符串，同上
  testTopics: string    // JSON 字符串，包含测试主题列表
  status: string        // 任务状态：PENDING / RUNNING / COMPLETED / FAILED
  totalCases: number    // 总测试用例数
  completedCases: number // 已完成用例数
  createTime?: string   // 创建时间
  /** Wave 4.6: 配对 t 检验 p-value，COMPLETED 后才有；< 0.05 表示显著差异 */
  pValue?: number
}

/**
 * EvalReportVO —— 评估报告（后端返回）。
 * 包含 A/B 对比的整体统计、各维度平均分、最佳/最差案例等。
 */
export interface EvalReportVO {
  taskId: string | number
  taskName: string
  totalCases: number
  winsA: number          // A 配置胜出次数
  winsB: number          // B 配置胜出次数
  ties: number           // 平局次数
  avgScoresA: Record<string, number>  // A 各维度平均分
  avgScoresB: Record<string, number>  // B 各维度平均分
  overallAvgA: number    // A 总体平均分
  overallAvgB: number    // B 总体平均分
  bestCases: { topic: string; winner: string; scoreA: number; scoreB: number }[]
  worstCases: { topic: string; winner: string; scoreA: number; scoreB: number }[]
}

/**
 * evalApi —— 评估中心 API 集合对象。
 */
export const evalApi = {
  /**
   * 查询所有评估任务列表
   * @returns EvalTaskVO 数组
   */
  listTasks: () => get<EvalTaskVO[]>('/eval/tasks'),

  /**
   * 创建评估任务
   * @param dto 包含任务名称、两套配置和测试主题列表
   * @returns 创建的 EvalTaskVO
   */
  createTask: (dto: {
    name: string
    configA: Record<string, any>
    configB: Record<string, any>
    testTopics: { topic: string; platform: string; keywords?: string[] }[]
  }) => post<EvalTaskVO>('/eval/task', dto),

  /**
   * 执行评估任务（触发后端开始 A/B 对比生成与评分）
   * @param taskId 任务 ID
   */
  runTask: (taskId: string | number) => post<void>(`/eval/task/${taskId}/run`),

  /**
   * 获取评估报告
   * @param taskId 任务 ID
   * @returns EvalReportVO 评估报告
   */
  getReport: (taskId: string | number) => get<EvalReportVO>(`/eval/task/${taskId}/report`),

  /**
   * Wave 4.6 - 导出评估报告 CSV 下载 URL（直接组装路径触发浏览器下载）。
   * 需要带 token，前端 a 标签 href 加上 ?token=xxx 参数。
   */
  exportUrl: (taskId: string | number) => `/api/v1/eval/task/${taskId}/export`,
}
