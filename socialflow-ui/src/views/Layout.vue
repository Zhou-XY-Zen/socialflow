<!--
  Layout.vue —— 主布局组件（侧边栏 + 顶栏 + 内容区）
  统一的应用外壳：可折叠侧边栏（渐变背景）+ 顶栏（面包屑+用户）+ 内容区
-->

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 侧边栏是否折叠 */
const collapsed = ref(false)

/**
 * 主菜单按"工具"分组：
 *   - 一级（MenuGroup）= 工具，本身不带 path，仅用于折叠分组
 *   - 二级（MenuLeaf）= 工具的子功能，带 path 实际路由跳转
 * 未来加一个无关方向的工具：在 menuGroups 数组追加一组即可。
 */
interface MenuLeaf {
  path: string
  label: string
  icon: string
}
interface MenuGroup {
  label: string
  icon: string
  children: MenuLeaf[]
}

const menuGroups: MenuGroup[] = [
  {
    label: '文案创作',
    icon: 'EditPen',
    children: [
      { path: '/workspace', label: 'AI 创作工作台', icon: 'MagicStick' },
      { path: '/content',   label: '内容库',        icon: 'Files' },
      { path: '/media',     label: '素材库',        icon: 'Picture' },
      { path: '/calendar',  label: '日历',          icon: 'Calendar' },
      { path: '/knowledge', label: '知识库',        icon: 'Reading' },
      { path: '/template',  label: '模板',          icon: 'Collection' },
      { path: '/eval',      label: '评估',          icon: 'DataAnalysis' },
      { path: '/publish',   label: '分发',          icon: 'Promotion' },
      { path: '/dashboard', label: '看板',          icon: 'TrendCharts' },
    ],
  },
  {
    label: '知识中枢',
    icon: 'Notebook',
    children: [
      { path: '/notes',          label: '我的笔记',  icon: 'Notebook' },
      { path: '/notes/upload',   label: '导入笔记',  icon: 'Upload' },
      { path: '/notes/trash',    label: '回收站',    icon: 'Delete' },
    ],
  },
  {
    label: '代码分析',
    icon: 'Cpu',
    children: [
      { path: '/code-analysis/dashboard', label: '仪表盘',    icon: 'Odometer' },
      { path: '/code-analysis/project',   label: '项目概览',  icon: 'Reading' },
      { path: '/code-analysis/review',    label: '提交审查',  icon: 'Search' },
      { path: '/code-analysis/diff',      label: '对比分析',  icon: 'Connection' },
      { path: '/code-analysis/history',   label: '历史记录',  icon: 'Clock' },
      { path: '/code-analysis/bookmarks',   label: '仓库收藏',  icon: 'Star' },
      { path: '/code-analysis/credentials', label: '仓库凭证',  icon: 'Key' },
      { path: '/code-analysis/rules',       label: '规约库',    icon: 'DocumentCopy' },
      // 原"设置"项已并入「个人设置 → 代码分析 · 模型 / 偏好」
    ],
  },
]

/**
 * 系统级页面：不在左侧主菜单里渲染，仅用于顶栏标题/图标查找。
 * 设置入口走右上角头像下拉。
 */
const systemPages: MenuLeaf[] = [
  { path: '/settings', label: '个人设置', icon: 'Setting' },
]

/** 根据 path 在分组菜单 + 系统页面里查找对应 leaf */
function findMenuByPath(path: string): MenuLeaf | undefined {
  for (const g of menuGroups) {
    const hit = g.children.find(c => c.path === path)
    if (hit) return hit
  }
  return systemPages.find(s => s.path === path)
}

/** 当前激活的菜单路径 */
const activePath = computed(() => route.path)

/** 当前路径所在分组的 label —— 用于让 sub-menu 自动展开当前工具组 */
const defaultOpeneds = computed<string[]>(() => {
  for (const g of menuGroups) {
    if (g.children.some(c => c.path === route.path)) {
      return [g.label]
    }
  }
  return []
})

/** 当前页面标题（顶栏） */
const currentTitle = computed(() => {
  return (route.meta?.title as string) || findMenuByPath(route.path)?.label || ''
})

/** 当前页面对应的 menu leaf（顶栏图标） */
const currentMenu = computed(() => findMenuByPath(route.path))

async function logout() {
  try { await authApi.logout() } catch { /* ignore */ }
  userStore.clear()
  router.replace('/login')
}
</script>

<template>
  <el-container class="layout-root">
    <!-- ==================== 侧边栏 ==================== -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
      <!-- Logo 区域 -->
      <div class="logo">
        <div class="logo-icon">S</div>
        <transition name="fade-text">
          <span v-if="!collapsed" class="logo-text">SocialFlow</span>
        </transition>
      </div>

      <!-- 菜单：按工具分组的折叠菜单 -->
      <el-menu
        :default-active="activePath"
        :default-openeds="defaultOpeneds"
        :collapse="collapsed"
        :collapse-transition="false"
        class="sidebar-menu"
        background-color="transparent"
        text-color="rgba(255, 255, 255, 0.75)"
        active-text-color="#ffffff"
        :router="true"
      >
        <el-sub-menu
          v-for="group in menuGroups"
          :key="group.label"
          :index="group.label"
          class="menu-group-custom"
        >
          <template #title>
            <el-icon><component :is="group.icon" /></el-icon>
            <span>{{ group.label }}</span>
          </template>
          <el-menu-item
            v-for="child in group.children"
            :key="child.path"
            :index="child.path"
            class="menu-item-custom"
          >
            <el-icon><component :is="child.icon" /></el-icon>
            <template #title>{{ child.label }}</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>

      <!-- 折叠按钮 -->
      <div class="collapse-btn" @click="collapsed = !collapsed">
        <el-icon :size="16">
          <component :is="collapsed ? 'DArrowRight' : 'DArrowLeft'" />
        </el-icon>
      </div>
    </el-aside>

    <!-- ==================== 右侧区域 ==================== -->
    <el-container>
      <!-- 顶栏 -->
      <el-header class="header" height="60px">
        <!-- 面包屑/标题 -->
        <div class="header-title">
          <el-icon v-if="currentMenu" :size="20" class="title-icon">
            <component :is="currentMenu.icon" />
          </el-icon>
          <span class="title-text">{{ currentTitle }}</span>
        </div>

        <!-- 用户下拉 -->
        <el-dropdown trigger="click">
          <div class="user-dropdown">
            <el-avatar
              :size="36"
              :src="userStore.user?.avatarUrl || undefined"
              class="user-avatar"
            >
              {{ (userStore.user?.nickname || 'U').charAt(0).toUpperCase() }}
            </el-avatar>
            <div class="user-info">
              <div class="user-name">{{ userStore.user?.nickname || 'guest' }}</div>
              <div class="user-status">
                <span class="status-dot"></span>
                在线
              </div>
            </div>
            <el-icon :size="12" class="dropdown-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="$router.push('/settings')">
                <el-icon><User /></el-icon>
                个人设置
              </el-dropdown-item>
              <el-dropdown-item divided @click="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-root {
  height: 100vh;
  background: var(--sf-bg);
}

/* ========== 侧边栏 ========== */
.sidebar {
  background: linear-gradient(180deg, #1a2341 0%, #2d2a5a 100%);
  color: #fff;
  transition: width var(--sf-transition-base);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

/* 侧边栏装饰渐变 */
.sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 200px;
  background: linear-gradient(180deg, rgba(102, 126, 234, 0.25), transparent);
  pointer-events: none;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
  position: relative;
  z-index: 1;
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--sf-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #fff;
  white-space: nowrap;
}

.sidebar-menu {
  flex: 1;
  border: none !important;
  padding: 8px;
  position: relative;
  z-index: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

:deep(.menu-item-custom) {
  border-radius: var(--sf-radius-sm) !important;
  margin: 4px 0 !important;
  height: 44px !important;
  line-height: 44px !important;
  transition: all var(--sf-transition-fast) !important;
  position: relative;
}

:deep(.menu-item-custom:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #fff !important;
}

:deep(.menu-item-custom.is-active) {
  background: rgba(102, 126, 234, 0.2) !important;
  color: #fff !important;
}

:deep(.menu-item-custom.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 0 2px 2px 0;
  background: var(--sf-gradient);
}

:deep(.menu-item-custom .el-icon) {
  font-size: 18px !important;
}

/* ---- 一级分组（el-sub-menu）标题 ---- */
:deep(.menu-group-custom > .el-sub-menu__title) {
  border-radius: var(--sf-radius-sm) !important;
  margin: 4px 0 !important;
  height: 44px !important;
  line-height: 44px !important;
  color: rgba(255, 255, 255, 0.85) !important;
  font-weight: 500;
  transition: all var(--sf-transition-fast) !important;
}

:deep(.menu-group-custom > .el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #fff !important;
}

:deep(.menu-group-custom > .el-sub-menu__title .el-icon) {
  font-size: 18px !important;
}

/* sub-menu 展开后子项缩进显示层级 */
:deep(.menu-group-custom .el-menu-item) {
  padding-left: 44px !important;
  height: 40px !important;
  line-height: 40px !important;
  font-size: 13px;
}

:deep(.menu-group-custom .el-menu-item .el-icon) {
  font-size: 16px !important;
}

/* 折叠状态下，sub-menu 弹出的子菜单层保持深色基调 */
:deep(.el-menu--vertical .el-menu--popup) {
  background: linear-gradient(180deg, #1a2341 0%, #2d2a5a 100%) !important;
  border-radius: 8px;
  padding: 6px;
}

:deep(.el-menu--vertical .el-menu--popup .el-menu-item) {
  border-radius: var(--sf-radius-sm) !important;
  margin: 2px 0 !important;
  color: rgba(255, 255, 255, 0.75) !important;
}

:deep(.el-menu--vertical .el-menu--popup .el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #fff !important;
}

:deep(.el-menu--vertical .el-menu--popup .el-menu-item.is-active) {
  background: rgba(102, 126, 234, 0.2) !important;
  color: #fff !important;
}

/* 折叠按钮 */
.collapse-btn {
  padding: 12px;
  margin: 8px;
  border-radius: var(--sf-radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.5);
  transition: all var(--sf-transition-fast);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

/* ========== 顶栏 ========== */
.header {
  background: #fff;
  border-bottom: 1px solid var(--sf-border-light);
  box-shadow: var(--sf-shadow-xs);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--sf-space-5);
  z-index: 10;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-icon {
  color: var(--sf-primary);
}

.title-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--sf-text-primary);
}

/* 用户下拉 */
.user-dropdown {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border-radius: var(--sf-radius-md);
  cursor: pointer;
  transition: background var(--sf-transition-fast);
}

.user-dropdown:hover {
  background: var(--sf-surface-hover);
}

.user-avatar {
  background: var(--sf-gradient) !important;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sf-text-primary);
}

.user-status {
  font-size: 11px;
  color: var(--sf-text-muted);
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sf-success);
  display: inline-block;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.5);
}

.dropdown-arrow {
  color: var(--sf-text-muted);
}

/* ========== 主内容区 ========== */
.main {
  padding: var(--sf-space-5) !important;
  background: var(--sf-bg);
  overflow-y: auto;
}

/* ========== 过渡动画 ========== */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity var(--sf-transition-fast), transform var(--sf-transition-fast);
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-fade-leave-to {
  opacity: 0;
}

.fade-text-enter-active,
.fade-text-leave-active {
  transition: opacity var(--sf-transition-fast);
}

.fade-text-enter-from,
.fade-text-leave-to {
  opacity: 0;
}
</style>
