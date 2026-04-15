/**
 * Dashboard API（Wave 3.2 后端新增）—— 聚合统计端点。
 *
 * 后端在 ai_usage_log / publish_task / content / kb / media_asset 上做 SQL GROUP BY，
 * 比前端拉 100 条内容做客户端聚合更准确（覆盖任意时间窗 + 真实成本数据）。
 */
import { get } from './http'
import type { DashboardOverviewVO } from '@/types/api'

export interface AiUsageBreakdown {
  key: string
  calls: number
  tokens: number
  cost: number
}

export interface PublishTrendPoint {
  date: string
  total: number
  success: number
  failed: number
}

export interface CostByProvider {
  provider: string
  totalTokens: number
  totalCost: number
}

export const dashboardApi = {
  /** 总览：内容/发布/AI/KB/媒体计数 + 近 7 日趋势 */
  overview(): Promise<DashboardOverviewVO> {
    return get<DashboardOverviewVO>('/dashboard/overview')
  },

  /** AI 用量按维度聚合，dim ∈ {provider|model|requestType} */
  aiUsage(dim: 'provider' | 'model' | 'requestType' = 'provider', days = 7): Promise<AiUsageBreakdown[]> {
    return get<AiUsageBreakdown[]>('/dashboard/ai-usage', { params: { dim, days } })
  },

  /** 发布趋势按天聚合 */
  publishTrends(days = 30): Promise<PublishTrendPoint[]> {
    return get<PublishTrendPoint[]>('/dashboard/publish-trends', { params: { days } })
  },

  /** 成本明细按 provider 聚合 */
  cost(days = 30): Promise<CostByProvider[]> {
    return get<CostByProvider[]>('/dashboard/cost', { params: { days } })
  },
}
