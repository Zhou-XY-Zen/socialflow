/**
 * ============================================================
 * api/http.ts —— HTTP 请求封装层（基于 Axios）
 * ============================================================
 * 【什么是 Axios？】
 * Axios 是最流行的 HTTP 请求库，用来在浏览器中向后端发送 HTTP 请求（GET/POST/PUT/DELETE 等）。
 * 它比浏览器原生的 fetch API 提供了更丰富的功能：拦截器、超时设置、自动 JSON 转换等。
 *
 * 【本文件的作用】
 * 1. 创建一个预配置的 Axios 实例（统一 baseURL、超时时间）
 * 2. 设置"请求拦截器"——在每个请求发出前自动注入 JWT Token
 * 3. 设置"响应拦截器"——统一处理后端返回的 code 判断和错误提示
 * 4. 导出 get/post/put/del 四个快捷函数，自动解包 R<T>.data
 *
 * 请求流程：
 *   组件调用 get/post → Axios 实例发请求 → 请求拦截器注入 Token →
 *   后端处理 → 响应返回 → 响应拦截器检查 code → 返回 data 给组件
 * ============================================================
 */

import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { R } from '@/types/api'

/**
 * ---- 创建 Axios 实例 ----
 * axios.create() 会返回一个独立的 Axios 实例，拥有自己的配置和拦截器，
 * 不会影响其他地方可能使用的 axios 全局实例。
 *
 * baseURL: '/api/v1'  —— 所有请求的 URL 都会自动加上这个前缀。
 *   例如调用 http.get('/auth/me') 实际发送的是 GET /api/v1/auth/me。
 * timeout: 60_000     —— 请求超时时间 60 秒（60000 毫秒）。下划线是 JS 数字分隔符，增强可读性。
 */
const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 60_000,
})

/**
 * ---- 请求拦截器（Request Interceptor） ----
 * 在每个请求发出之前，自动执行这个回调函数。
 * 作用：如果用户已登录（有 Token），就把 Token 塞到请求头 Authorization 中，
 *       格式为 "Bearer xxxxx"，这是 JWT 认证的标准方式。
 * 这样就不需要在每个 API 调用处手动传 Token 了。
 */
http.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

/**
 * ---- 响应拦截器（Response Interceptor） ----
 * 在收到后端响应后、返回给调用者之前执行。
 *
 * 第一个回调处理"HTTP 状态码 2xx"的正常响应：
 *   - 检查业务 code 是否为 200，不是则弹出错误提示并抛出异常。
 *
 * 第二个回调处理"HTTP 状态码非 2xx"的网络错误：
 *   - 401（未认证）：Token 过期或无效，清除登录状态并跳转到登录页。
 *   - 其他错误：弹出错误消息提示。
 */
/**
 * 提取响应头中的 traceId，便于把"用户报告 + 后端日志"对齐到同一条请求。
 * 后端 MdcTraceIdFilter 会在每个响应上打 X-Trace-Id 头。
 */
function extractTraceId(headers?: Record<string, unknown>): string | undefined {
  if (!headers) return undefined
  const v = (headers['x-trace-id'] ?? headers['X-Trace-Id']) as string | undefined
  return v && v.length > 0 ? v : undefined
}

http.interceptors.response.use(
  (response) => {
    /* 将响应体断言为 R<unknown> 类型以便访问 code 字段 */
    const data = response.data as R<unknown>
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code !== 200) {
        /* 业务异常：弹出错误消息 */
        const traceId = extractTraceId(response.headers as unknown as Record<string, unknown>)
        ElMessage.error(traceId
          ? `${data.message || 'request failed'} (traceId: ${traceId})`
          : (data.message || 'request failed'))
        return Promise.reject(new Error(data.message))
      }
    }
    return response
  },
  (error) => {
    const status = error?.response?.status
    const traceId = extractTraceId(error?.response?.headers)
    if (status === 401) {
      /* Token 过期 / 未登录：清除本地会话并跳转到登录页 */
      const userStore = useUserStore()
      userStore.clear()
      window.location.href = '/login'
    } else if (status === 429) {
      /* Wave 1.4 Resilience4j 限流命中 —— 友好提示，不打"未知错误" */
      ElMessage.warning('请求过于频繁，请稍后再试')
      return Promise.reject(error)
    }
    /* 其他错误：统一弹消息，附带 traceId 便于排查 */
    const baseMsg = error?.response?.data?.message || error.message || '请求失败'
    ElMessage.error(traceId ? `${baseMsg} (traceId: ${traceId})` : baseMsg)
    /* 把 traceId 挂到 error 对象上，方便上游 catch 时记录 */
    if (traceId && error) (error as { traceId?: string }).traceId = traceId
    return Promise.reject(error)
  },
)

/**
 * ---- 以下是四个快捷请求函数 ----
 * 它们封装了 Axios 的 get/post/put/delete 方法，
 * 并自动将 R<T> 响应体中的 data 字段提取出来返回，
 * 这样调用方就不需要每次都写 res.data.data 了。
 *
 * 泛型说明：
 *   <T>       —— 返回数据的类型（即 R<T> 中的 T）
 *   <B>       —— 请求体的类型（默认 unknown）
 *   Promise<T>—— 返回值是一个 Promise，resolve 后得到 T 类型的数据
 */

/** 发送 GET 请求，返回解包后的 data */
export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.get<R<T>>(url, config)
  return res.data.data
}

/** 发送 POST 请求，返回解包后的 data */
export async function post<T, B = unknown>(url: string, body?: B, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.post<R<T>>(url, body, config)
  return res.data.data
}

/** 发送 PUT 请求，返回解包后的 data */
export async function put<T, B = unknown>(url: string, body?: B): Promise<T> {
  const res = await http.put<R<T>>(url, body)
  return res.data.data
}

/** 发送 DELETE 请求，返回解包后的 data */
export async function del<T>(url: string): Promise<T> {
  const res = await http.delete<R<T>>(url)
  return res.data.data
}

/** 导出 Axios 实例本身，供需要直接使用的场景（如 content.ts 中的 update 方法） */
export default http
