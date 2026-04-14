/**
 * ============================================================
 * api/apikey.ts —— API Key 管理相关接口
 * ============================================================
 * 本文件将所有与"API Key 管理"相关的 API 调用集中在 apiKeyApi 对象中。
 * 每个方法对应后端 ApiKeyController 的一个接口。
 * ============================================================
 */

import { get, post, del } from './http'

/** API Key 列表项的类型定义 */
export interface ApiKeyItem {
  provider: string
  maskedKey: string
  isDefault: boolean
  baseUrl: string | null
}

/** 保存 API Key 的请求参数 */
export interface SaveApiKeyDTO {
  provider: string
  apiKey: string
  baseUrl?: string
  isDefault: boolean
}

/**
 * apiKeyApi —— API Key 管理接口集合对象。
 */
export const apiKeyApi = {
  /**
   * 获取当前用户的 API Key 脱敏列表
   * @returns 脱敏后的 API Key 列表
   */
  list: () => get<ApiKeyItem[]>('/api-keys/list'),

  /**
   * 保存（新增或更新）API Key
   * @param dto 包含 provider, apiKey, baseUrl, isDefault 的对象
   */
  save: (dto: SaveApiKeyDTO) => post<void>('/api-keys/save', dto),

  /**
   * 删除指定供应商的 API Key
   * @param provider 供应商名称（如 DEEPSEEK、OPENAI 等）
   */
  delete: (provider: string) => del<void>(`/api-keys/${provider}`),
}
