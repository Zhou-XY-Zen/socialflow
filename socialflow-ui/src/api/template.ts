import { get, post, del } from './http'
import http from './http'
import type { R, TemplatePreviewVO } from '@/types/api'

export interface TemplateVO {
  id: string | number
  templateName: string
  platform: string
  category: string
  systemPrompt: string
  userPromptTemplate: string
  variables?: string
  outputFormat: string
  isSystem: number
  sortOrder: number
  createTime?: string
}

export const templateApi = {
  list: (platform?: string) =>
    get<TemplateVO[]>('/template/list', { params: { platform } }),

  create: (tpl: Partial<TemplateVO>) =>
    post<TemplateVO>('/template', tpl),

  update: (id: string | number, tpl: Partial<TemplateVO>) =>
    http.put<R<TemplateVO>>(`/api/v1/template/${id}`, tpl).then(r => r.data.data),

  delete: (id: string | number) =>
    del<void>(`/template/${id}`),

  /**
   * Wave 4.4 - 模板预览：用样例变量试渲染，给出变量诊断（缺失/未使用/已用）。
   *
   * @param id 模板 ID
   * @param sampleVars 样例变量映射（如 {topic:"咖啡", tone:"活泼"}）
   */
  preview: (id: string | number, sampleVars: Record<string, unknown>) =>
    post<TemplatePreviewVO>(`/template/${id}/preview`, sampleVars),
}
