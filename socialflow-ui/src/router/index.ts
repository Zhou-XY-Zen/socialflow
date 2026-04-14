/**
 * ============================================================
 * router/index.ts —— 前端路由配置
 * ============================================================
 * Vue Router 是 Vue 3 的官方路由库。它的作用是：
 *   根据浏览器地址栏中的 URL，决定渲染哪个页面组件。
 *
 * 本文件做了三件事：
 *   1. 定义路由表（routes）——URL 路径 与 页面组件 的映射关系
 *   2. 创建路由实例（createRouter）
 *   3. 设置全局前置守卫（beforeEach）——在每次路由跳转前检查登录状态
 * ============================================================
 */

/**
 * createRouter        —— 创建路由实例的工厂函数
 * createWebHistory    —— 使用 HTML5 History API（URL 没有 # 号，如 /workspace）
 * type RouteRecordRaw —— 路由配置对象的 TypeScript 类型
 *   （type 关键字表示只导入类型，不导入运行时代码，编译后会被擦除）
 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * ---- 路由表 ----
 * RouteRecordRaw[] 是一个数组，每个元素描述一条路由规则。
 * 重要字段：
 *   path      ：URL 路径
 *   component ：对应的 Vue 页面组件
 *   meta      ：路由元信息（可存放任意自定义数据，如 title、是否公开等）
 *   children  ：子路由（会渲染在父组件的 <router-view/> 中）
 *
 * 【懒加载（Lazy Loading）】
 *   component: () => import('@/views/Login.vue')
 *   这种写法叫做"动态导入"。打包时 Vite 会把每个页面拆成独立的 JS 文件，
 *   只有用户真正访问该路由时才加载，加快首屏速度。
 */
const routes: RouteRecordRaw[] = [
  /* 根路径 '/' 自动重定向到工作台 */
  { path: '/', redirect: '/workspace' },

  /* 登录和注册页面 —— meta.public = true 表示无需登录即可访问 */
  { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/Register.vue'), meta: { public: true } },

  {
    /*
     * 这是一个"布局路由"——它本身的路径也是 '/'，
     * 但它的 component 是 Layout.vue（包含侧边栏 + 顶栏）。
     * 所有 children 子路由渲染在 Layout.vue 内部的 <router-view /> 中。
     */
    path: '/',
    component: () => import('@/views/Layout.vue'),
    children: [
      /* 工作台 —— AI 文案生成的主界面 */
      { path: 'workspace', name: 'workspace', component: () => import('@/views/Workspace.vue'),
        meta: { title: '工作台' } },
      /* 内容库 —— 查看已生成的文案列表 */
      { path: 'content', name: 'content', component: () => import('@/views/ContentLibrary.vue'),
        meta: { title: '内容库' } },
      /* 素材库 —— 图片/视频等多媒体素材管理 */
      { path: 'media', name: 'media', component: () => import('@/views/MediaLibrary.vue'),
        meta: { title: '素材库' } },
      /* 内容日历 —— 按日期排列的发布计划 */
      { path: 'calendar', name: 'calendar', component: () => import('@/views/Calendar.vue'),
        meta: { title: '内容日历' } },
      /* 知识库 —— 上传文档供 AI 检索参考（RAG） */
      { path: 'knowledge', name: 'knowledge', component: () => import('@/views/Knowledge.vue'),
        meta: { title: '知识库' } },
      /* Prompt 模板 —— 预设的提示词模板 */
      { path: 'template', name: 'template', component: () => import('@/views/Templates.vue'),
        meta: { title: 'Prompt 模板' } },
      /* 评估中心 —— 对生成内容进行质量评估 */
      { path: 'eval', name: 'eval', component: () => import('@/views/EvalCenter.vue'),
        meta: { title: '评估中心' } },
      /* 分发中心 —— 将内容发布到各社交平台 */
      { path: 'publish', name: 'publish', component: () => import('@/views/Publish.vue'),
        meta: { title: '分发中心' } },
      /* 数据看板 —— 数据统计与可视化 */
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'),
        meta: { title: '数据看板' } },
      /* 个人设置 —— 用户偏好、API Key 管理等 */
      { path: 'settings', name: 'settings', component: () => import('@/views/Settings.vue'),
        meta: { title: '个人设置' } },
    ],
  },
]

/**
 * ---- 创建路由实例 ----
 * createWebHistory() 使用 HTML5 History 模式（URL 形如 /workspace，没有 #）。
 * 另一种模式是 createWebHashHistory()（URL 形如 /#/workspace）。
 */
const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * ---- 全局前置守卫（Navigation Guard） ----
 * router.beforeEach() 会在每次路由跳转前执行回调函数。
 * 参数 `to` 是即将进入的目标路由对象。
 *
 * 逻辑：
 *   1. 如果目标路由的 meta.public 为 true（登录 / 注册页），直接放行。
 *   2. 否则检查用户是否已有 Token（是否已登录）。
 *      - 有 Token → 放行
 *      - 无 Token → 重定向到 /login，并把原始路径放在 query.redirect 中，
 *        这样登录成功后可以跳回用户原本想访问的页面。
 */
router.beforeEach((to) => {
  if (to.meta?.public) return true
  const userStore = useUserStore()
  if (!userStore.token) return { path: '/login', query: { redirect: to.fullPath } }
  return true
})

export default router
