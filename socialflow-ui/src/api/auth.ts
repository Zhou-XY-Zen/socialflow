/**
 * ============================================================
 * api/auth.ts —— 认证相关 API 接口
 * ============================================================
 * 本文件将所有与"用户认证"相关的 API 调用集中在一个对象 authApi 中。
 * 每个方法对应后端的一个接口，内部使用 http.ts 中封装的 get/post 函数。
 *
 * 这种写法的好处是：
 *   - API 调用集中管理，方便查找和维护
 *   - 更换后端 URL 时只需改这里
 *   - 组件中只需 import { authApi } 即可调用所有认证接口
 * ============================================================
 */

import { get, post } from './http'
import type { LoginVO, UserVO } from '@/types/api'

/**
 * authApi —— 认证 API 集合对象。
 * 导出后在组件或 Store 中通过 authApi.login(...)、authApi.register(...) 调用。
 */
export const authApi = {
  /**
   * 用户注册
   * @param dto 包含 email、password、nickname 三个字段的对象
   * @returns 注册成功后返回新创建的用户信息 UserVO
   */
  register: (dto: { email: string; password: string; nickname: string }) =>
    post<UserVO>('/auth/register', dto),

  /**
   * 用户登录
   * @param dto 包含 email 和 password 的对象
   * @returns 登录成功返回 LoginVO（含 JWT Token 和用户信息）
   */
  login: (dto: { email: string; password: string }) =>
    post<LoginVO>('/auth/login', dto),

  /**
   * 获取当前登录用户信息（需要携带 Token）
   * @returns 当前用户的 UserVO
   */
  me: () => get<UserVO>('/auth/me'),

  /**
   * 退出登录（通知后端使 Token 失效）
   * 返回 void 表示不需要返回数据。
   */
  logout: () => post<void>('/auth/logout'),
}
