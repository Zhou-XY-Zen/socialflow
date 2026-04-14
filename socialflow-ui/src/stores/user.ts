/**
 * ============================================================
 * stores/user.ts —— 用户状态管理（Pinia Store）
 * ============================================================
 * 【什么是 Pinia？】
 * Pinia 是 Vue 3 官方推荐的"全局状态管理"库（Vue 2 时代的 Vuex 的替代品）。
 * 它让你把多个组件需要共享的数据（如登录用户信息、JWT Token）
 * 集中存放在一个 Store 里，任何组件都能读取和修改。
 *
 * 【什么是 defineStore？】
 * defineStore() 是 Pinia 创建 Store 的函数。
 * 第一个参数 'user' 是 Store 的唯一标识（ID），
 * 第二个参数是一个"setup 函数"，在里面用 ref / reactive 定义状态，
 * 用普通函数定义操作（Actions），最后 return 暴露给组件使用。
 *
 * 本 Store 的职责：
 *   - 保存当前用户的登录 Token 和用户信息
 *   - 将上述数据持久化到 localStorage，刷新页面后不丢失
 *   - 提供 setSession / clear 两个方法来更新或清除登录状态
 * ============================================================
 */

import { defineStore } from 'pinia'

/**
 * ref —— Vue 3 Composition API 中最核心的响应式 API。
 * ref(初始值) 返回一个"响应式引用"对象，访问值要用 .value。
 * 当 .value 发生变化时，所有使用了这个 ref 的模板 / computed 都会自动更新。
 *
 * 泛型写法：ref<string | null>(...)
 *   <string | null> 是 TypeScript 泛型（Generic），告诉 TS 这个 ref 里面的值
 *   可能是 string 类型或 null 类型。
 */
import { ref } from 'vue'

/**
 * type 导入 —— 仅导入类型，编译后不产生任何 JS 代码。
 * UserVO 描述了后端返回的用户信息结构（id、email、nickname 等字段）。
 */
import type { UserVO } from '@/types/api'

/** localStorage 中保存 Token 使用的键名 */
const LS_TOKEN = 'sf_token'
/** localStorage 中保存用户信息 JSON 使用的键名 */
const LS_USER = 'sf_user'

/**
 * useUserStore —— 调用后返回 Store 实例，供组件使用。
 * 命名约定：use + 名称 + Store，如 useUserStore。
 */
export const useUserStore = defineStore('user', () => {
  /*
   * ---- 响应式状态 ----
   * 初始化时从 localStorage 读取，如果之前已登录过，刷新页面后数据不会丢失。
   */

  /** 当前用户的 JWT Token，未登录时为 null */
  const token = ref<string | null>(localStorage.getItem(LS_TOKEN))

  /**
   * 当前用户信息对象，未登录时为 null。
   * 从 localStorage 取出的是 JSON 字符串，需要用 JSON.parse() 转换回对象。
   * `as string` 是 TypeScript 的类型断言，告诉编译器"我确定这个值是 string"。
   */
  const user = ref<UserVO | null>(
    localStorage.getItem(LS_USER) ? JSON.parse(localStorage.getItem(LS_USER) as string) : null,
  )

  /**
   * setSession —— 登录成功后调用，保存 Token 和用户信息。
   * @param t  JWT Token 字符串
   * @param u  用户信息对象
   */
  function setSession(t: string, u: UserVO) {
    token.value = t
    user.value = u
    /* 同步写入 localStorage，实现持久化 */
    localStorage.setItem(LS_TOKEN, t)
    localStorage.setItem(LS_USER, JSON.stringify(u))
  }

  /**
   * clear —— 退出登录时调用，清除所有登录状态。
   */
  function clear() {
    token.value = null
    user.value = null
    localStorage.removeItem(LS_TOKEN)
    localStorage.removeItem(LS_USER)
  }

  /* 将状态和方法暴露给外部组件使用 */
  return { token, user, setSession, clear }
})
