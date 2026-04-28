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
  /* 根路径 '/' 自动重定向到欢迎页 —— 登录后的默认落地 */
  { path: '/', redirect: '/welcome' },

  /* 登录和注册页面 —— meta.public = true 表示无需登录即可访问 */
  { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/Register.vue'), meta: { public: true } },

  /* 代码分析 · 分享视图（免登录） */
  {
    path: '/code-analysis/shared/:token',
    name: 'ca-shared',
    component: () => import('@/views/code-analysis/SharedView.vue'),
    meta: { public: true, title: '代码分析 · 分享' },
  },

  {
    /*
     * 这是一个"布局路由"——它本身的路径也是 '/'，
     * 但它的 component 是 Layout.vue（包含侧边栏 + 顶栏）。
     * 所有 children 子路由渲染在 Layout.vue 内部的 <router-view /> 中。
     */
    path: '/',
    component: () => import('@/views/Layout.vue'),
    children: [
      /* 欢迎页 —— 登录后 / 刷新后默认落地 */
      { path: 'welcome', name: 'welcome', component: () => import('@/views/Welcome.vue'),
        meta: { title: '欢迎' } },
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

      /* ================= 知识中枢（service-note） ================= */
      { path: 'notes', name: 'notes',
        component: () => import('@/views/notes/NotesList.vue'),
        meta: { title: '我的笔记' } },
      { path: 'notes/upload', name: 'notes-upload',
        component: () => import('@/views/notes/NotesUpload.vue'),
        meta: { title: '导入笔记' } },
      { path: 'notes/graph', name: 'notes-graph',
        component: () => import('@/views/notes/NotesGraph.vue'),
        meta: { title: '知识图谱' } },
      { path: 'notes/categories', name: 'notes-categories',
        component: () => import('@/views/notes/NotesCategories.vue'),
        meta: { title: '分类与标签' } },
      { path: 'notes/trash', name: 'notes-trash',
        component: () => import('@/views/notes/NotesTrash.vue'),
        meta: { title: '回收站' } },
      { path: 'notes/edit/:id?', name: 'notes-edit',
        component: () => import('@/views/notes/NotesEditor.vue'),
        meta: { title: '编辑笔记' } },
      { path: 'notes/import/:taskId/review', name: 'notes-import-review',
        component: () => import('@/views/notes/NotesImportReview.vue'),
        meta: { title: '上传审阅' } },
      /* 个人设置 —— 嵌套路由，左栏菜单 + 右栏 <router-view /> 风格 */
      { path: 'settings', component: () => import('@/views/settings/SettingsLayout.vue'),
        meta: { title: '个人设置' },
        children: [
          { path: '', redirect: { name: 'settings-profile' } },
          { path: 'profile', name: 'settings-profile',
            component: () => import('@/views/settings/ProfilePanel.vue'),
            meta: { title: '个人设置 · 账号信息' } },
          { path: 'api-keys', name: 'settings-api-keys',
            component: () => import('@/views/settings/ApiKeysPanel.vue'),
            meta: { title: '个人设置 · API Key 管理' } },
          { path: 'code-analysis-model', name: 'settings-ca-model',
            component: () => import('@/views/settings/CodeAnalysisModelPanel.vue'),
            meta: { title: '个人设置 · 代码分析模型' } },
          { path: 'preferences', name: 'settings-preferences',
            component: () => import('@/views/settings/PreferencesPanel.vue'),
            meta: { title: '个人设置 · 偏好' } },
        ]
      },

      /* ================= 代码分析（一级菜单 + 8 个二级） ================= */
      { path: 'code-analysis', redirect: '/code-analysis/dashboard' },
      { path: 'code-analysis/dashboard', name: 'ca-dashboard',
        component: () => import('@/views/code-analysis/Dashboard.vue'),
        meta: { title: '代码分析 · 仪表盘' } },
      { path: 'code-analysis/project', name: 'ca-project',
        component: () => import('@/views/code-analysis/ProjectOverview.vue'),
        meta: { title: '代码分析 · 项目概览' } },
      { path: 'code-analysis/review', name: 'ca-review',
        component: () => import('@/views/code-analysis/CommitReview.vue'),
        meta: { title: '代码分析 · 提交审查' } },
      { path: 'code-analysis/diff', name: 'ca-diff',
        component: () => import('@/views/code-analysis/DiffReview.vue'),
        meta: { title: '代码分析 · 对比分析' } },
      { path: 'code-analysis/history', name: 'ca-history',
        component: () => import('@/views/code-analysis/History.vue'),
        meta: { title: '代码分析 · 历史记录' } },
      { path: 'code-analysis/bookmarks', name: 'ca-bookmarks',
        component: () => import('@/views/code-analysis/Bookmarks.vue'),
        meta: { title: '代码分析 · 仓库收藏' } },
      { path: 'code-analysis/credentials', name: 'ca-credentials',
        component: () => import('@/views/code-analysis/Credentials.vue'),
        meta: { title: '代码分析 · 仓库凭证' } },
      { path: 'code-analysis/rules', name: 'ca-rules',
        component: () => import('@/views/code-analysis/Rules.vue'),
        meta: { title: '代码分析 · 规约库' } },
      /* 老路由重定向到新个人设置 —— 兼容已保存的书签 */
      { path: 'code-analysis/settings',
        redirect: '/settings/preferences' },
      { path: 'code-analysis/result/:id', name: 'ca-result',
        component: () => import('@/views/code-analysis/AnalysisResult.vue'),
        meta: { title: '代码分析 · 结果详情' } },
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
 * 全局"初次导航"标记：模块加载到第一次真正跳转完成之前为 true。
 * 结合 from.name === undefined，能精确识别"浏览器刷新 / 地址栏直接输入 URL"
 * 这两种"冷启动"场景 —— 这时候把用户引回欢迎页。
 */
let isFirstNavigation = true

/**
 * ---- 全局前置守卫（Navigation Guard） ----
 *
 * 逻辑优先级：
 *   1. 目标路由 public（登录 / 注册 / 分享视图）→ 放行
 *   2. 未登录 → 跳 /login 并携带原始路径作为 redirect
 *   3. 已登录且是"冷启动"（刷新 / 首次进入）且目标不是欢迎页 → 强制跳 /welcome
 *      这实现了需求："登录后 / 浏览器刷新后都回到欢迎页"
 *   4. 其他情况 → 放行（正常的用户在站内点击跳转）
 */
router.beforeEach((to, from) => {
  if (to.meta?.public) {
    isFirstNavigation = false
    return true
  }
  const userStore = useUserStore()
  if (!userStore.token) {
    isFirstNavigation = false
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 冷启动（刷新 / 外部跳入）：from.name === undefined 且是首次进入路由系统
  const coldStart = isFirstNavigation && from.name === undefined
  isFirstNavigation = false
  if (coldStart && to.path !== '/welcome') {
    return { path: '/welcome' }
  }
  return true
})

export default router
