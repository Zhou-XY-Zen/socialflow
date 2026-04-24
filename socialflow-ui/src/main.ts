/**
 * ============================================================
 * main.ts —— 应用入口文件（Bootstrap）
 * ============================================================
 * 这是整个 Vue 前端项目的"启动器"。浏览器加载页面后最先执行此文件。
 *
 * 它的职责：
 *   1. 创建 Vue 应用实例
 *   2. 安装必要的插件（Pinia 状态管理、Vue Router 路由、Element Plus UI 库）
 *   3. 全局注册 Element Plus 的所有图标
 *   4. 将应用挂载到 HTML 页面中 id="app" 的 DOM 节点上
 * ============================================================
 */

/**
 * createApp —— Vue 3 提供的工厂函数，用来创建一个全新的 Vue 应用实例。
 * 你可以把它想象成"应用的开关"，调用后返回一个 app 对象，
 * 后续所有插件都通过 app.use() 安装。
 */
import { createApp } from 'vue'

/**
 * createPinia —— 来自 Pinia 库。Pinia 是 Vue 3 官方推荐的"状态管理"方案。
 * 所谓状态管理，就是把多个组件需要共享的数据（例如用户信息、登录 Token）
 * 集中存放在一个"Store"里，任何组件都能读取和修改，数据变化时视图自动更新。
 */
import { createPinia } from 'pinia'

/**
 * ElementPlus —— 一套基于 Vue 3 的桌面端 UI 组件库（按钮、表格、弹窗等）。
 * 下面这行导入了它的全部 CSS 样式文件。
 */
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

/**
 * ElementPlusIconsVue —— Element Plus 的图标集合。
 * 用 `* as` 语法把所有图标组件一次性导入为一个对象，
 * 后面会用 for 循环逐个注册为全局组件，这样在模板里就可以直接写 <EditPen /> 等。
 */
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

/** 根组件 —— 整棵组件树的最顶层组件 */
import App from './App.vue'

/** 路由实例 —— 定义了 URL 路径与页面组件的对应关系 */
import router from './router'

/** 全局 CSS 变量和基础样式 */
import './assets/main.css'

/** 代码分析报告通用 Markdown 样式（三页面复用，避免 scoped 重复） */
import './assets/markdown-body.css'

/** 打印样式（window.print() 导出 PDF 时生效） */
import './assets/print.css'

/*
 * ---- 第 1 步：创建 Vue 应用实例 ----
 * createApp(App) 接收根组件 App，返回一个可配置的应用实例 `app`。
 */
const app = createApp(App)

/*
 * ---- 第 2 步：全局注册所有 Element Plus 图标组件 ----
 * Object.entries() 将对象转为 [key, value] 数组，
 * 然后通过 app.component(名称, 组件) 一一注册。
 * 注册后，你可以在任何 .vue 模板中直接使用 <EditPen />、<Setting /> 等图标。
 */
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as never)
}

/*
 * ---- 第 3 步：安装插件 ----
 * app.use() 是 Vue 3 统一的插件安装方式。
 *   - createPinia()  ：安装 Pinia 状态管理
 *   - router         ：安装 Vue Router 路由
 *   - ElementPlus    ：安装 Element Plus UI 组件库
 */
app.use(createPinia())
app.use(router)
app.use(ElementPlus)

/*
 * ---- 第 4 步：挂载应用 ----
 * 把 Vue 应用渲染到 HTML 页面中 <div id="app"></div> 这个节点内。
 * 调用 mount 后，Vue 才真正开始工作（渲染视图、监听数据变化等）。
 */
app.mount('#app')
