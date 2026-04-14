<!--
  ============================================================
  Layout.vue —— 主布局组件（侧边栏 + 顶栏 + 内容区）
  ============================================================
  这个组件定义了登录后所有页面的整体布局结构：
    ┌─────────────────────────────────────────┐
    │  侧边栏（Logo + 菜单）  │   顶栏（页面标题 + 用户信息）  │
    │                         │──────────────────────────────│
    │                         │   内容区（<router-view />）     │
    │                         │   根据 URL 渲染不同的子页面     │
    └─────────────────────────────────────────┘

  使用了 Element Plus 的布局组件：
    el-container  —— 布局容器，自动根据子元素排列方向（水平/垂直）
    el-aside      —— 侧边栏区域
    el-header     —— 顶栏区域
    el-main       —— 主内容区域
  ============================================================
-->

<script setup lang="ts">
/**
 * 【本组件功能概述】
 * 1. 左侧侧边栏：显示应用 Logo 和导航菜单，点击菜单项切换页面
 * 2. 顶部栏：显示当前页面标题和用户昵称，点击昵称可退出登录
 * 3. 主内容区：通过 <router-view /> 渲染当前路由对应的子页面
 */

/**
 * computed —— Vue 3 的计算属性。
 * 它接收一个函数，返回值会被自动缓存。
 * 只有当依赖的响应式数据（如 route.path）发生变化时才会重新计算。
 * 适合用来"从现有数据派生出新数据"的场景。
 */
import { computed } from 'vue'

/**
 * useRouter —— 获取路由实例，可以进行编程式导航（如 router.push / router.replace）
 * useRoute  —— 获取当前路由信息（如当前路径 route.path、路由元信息 route.meta）
 * RouterView —— 路由出口组件，在这里渲染子路由对应的页面
 */
import { useRoute, useRouter, RouterView } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'

/** 路由实例 —— 用于编程式跳转 */
const router = useRouter()
/** 当前路由对象 —— 包含当前 URL 路径、参数、meta 等信息 */
const route = useRoute()
/** 用户 Store —— 读取用户昵称、清除登录状态 */
const userStore = useUserStore()

/**
 * menuItems —— 侧边栏菜单配置数组。
 * 每个对象包含：
 *   path  ：点击后跳转的路由路径
 *   label ：菜单显示的文字
 *   icon  ：Element Plus 图标组件名称（已在 main.ts 中全局注册）
 */
const menuItems = [
  { path: '/workspace', label: '工作台', icon: 'EditPen' },
  { path: '/content',   label: '内容库', icon: 'Files' },
  { path: '/media',     label: '素材库', icon: 'Picture' },
  { path: '/calendar',  label: '日历',   icon: 'Calendar' },
  { path: '/knowledge', label: '知识库', icon: 'Reading' },
  { path: '/template',  label: '模板',   icon: 'Collection' },
  { path: '/eval',      label: '评估',   icon: 'DataAnalysis' },
  { path: '/publish',   label: '分发',   icon: 'Promotion' },
  { path: '/dashboard', label: '看板',   icon: 'TrendCharts' },
  { path: '/settings',  label: '设置',   icon: 'Setting' },
]

/**
 * activePath —— 计算属性，返回当前路由路径。
 * 传给 el-menu 的 :default-active 属性，用于高亮当前所在的菜单项。
 */
const activePath = computed(() => route.path)

/**
 * logout —— 退出登录函数。
 * 1. 调用后端 logout 接口（使 Token 失效）——即使失败也不影响前端操作
 * 2. 清除本地存储的用户信息和 Token
 * 3. 跳转到登录页（replace 不会留下浏览器历史记录）
 */
async function logout() {
  try { await authApi.logout() } catch { /* 网络错误也不阻碍退出 */ }
  userStore.clear()
  router.replace('/login')
}
</script>

<template>
  <!-- 最外层容器，高度占满整个视口 -->
  <el-container style="height: 100vh">

    <!-- ====== 左侧侧边栏 ====== -->
    <el-aside width="200px" style="background:#001529; color:#fff">
      <!-- Logo 区域 -->
      <div style="padding:16px; font-size:18px; font-weight:600">SocialFlow</div>

      <!--
        el-menu —— Element Plus 的菜单组件。
        :default-active  绑定当前激活的菜单项（高亮显示）
        :router="true"   启用路由模式——点击菜单项时自动调用 router.push(path)
        v-for            遍历 menuItems 数组，为每个菜单项生成一个 el-menu-item
        :key             Vue 要求列表渲染时为每项提供唯一的 key
        :index           菜单项的唯一标识，在路由模式下也是跳转路径
        <component :is>  动态组件——根据 item.icon 的值渲染对应的图标组件
      -->
      <el-menu
        :default-active="activePath"
        background-color="#001529"
        text-color="#ffffffcc"
        active-text-color="#409eff"
        :router="true"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- ====== 右侧区域（顶栏 + 主内容） ====== -->
    <el-container>
      <!-- 顶栏：左侧显示页面标题，右侧显示用户下拉菜单 -->
      <el-header style="background:#fff; border-bottom:1px solid #e4e7ed; display:flex; align-items:center; justify-content:space-between">
        <!-- 页面标题 —— 从路由 meta 中读取 -->
        <div>{{ route.meta?.title || '' }}</div>
        <!-- 用户下拉菜单 —— 点击昵称展开，可退出登录 -->
        <el-dropdown>
          <span style="cursor:pointer">{{ userStore.user?.nickname || 'guest' }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- 主内容区 —— 子路由页面渲染在这里 -->
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
