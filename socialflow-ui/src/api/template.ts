import { get, post, del } from './http'
import http from './http'
import type { R } from '@/types/api'

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
}
