<!--
  SettingsLayout —— 个人设置的左右两栏布局，借鉴 VS Code Preferences 形态。
  左侧垂直菜单切换分类，右侧由 <router-view /> 渲染对应 Panel。
  子路由在 router/index.ts 下 /settings/* 注册。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

interface MenuItem { key: string; label: string; icon: string; to: string }

const menu: MenuItem[] = [
  { key: 'profile',               label: '账号信息',        icon: '👤', to: '/settings/profile' },
  { key: 'api-keys',              label: 'API Key 管理',    icon: '🔑', to: '/settings/api-keys' },
  { key: 'code-analysis-model',   label: '代码分析 · 模型', icon: '🧠', to: '/settings/code-analysis-model' },
  { key: 'preferences',           label: '代码分析 · 偏好', icon: '🎛️', to: '/settings/preferences' },
]

// 当前激活项：路径最后一段匹配 key（兼容子路由时仍能高亮）
const activeKey = computed(() => {
  const segs = route.path.split('/').filter(Boolean)
  return segs[segs.length - 1] || 'profile'
})

function go(to: string) {
  router.push(to)
}
</script>

<template>
  <div class="settings-layout">
    <aside class="settings-sidebar">
      <div class="sidebar-title">
        <span class="title-icon">⚙️</span>
        <span>设置</span>
      </div>
      <nav class="sidebar-menu">
        <a v-for="m in menu" :key="m.key"
           class="menu-item"
           :class="{ active: activeKey === m.key }"
           @click.prevent="go(m.to)">
          <span class="menu-icon">{{ m.icon }}</span>
          <span class="menu-label">{{ m.label }}</span>
        </a>
      </nav>
    </aside>
    <main class="settings-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.settings-layout {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 20px;
  padding: 20px;
  min-height: calc(100vh - 60px);
}

.settings-sidebar {
  background: #fff;
  border-radius: 12px;
  padding: 20px 0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  height: fit-content;
  position: sticky;
  top: 20px;
}

.sidebar-title {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px 16px;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  border-bottom: 1px solid #f3f4f6;
}
.title-icon { font-size: 20px; }

.sidebar-menu {
  display: flex;
  flex-direction: column;
  padding: 8px 8px 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #4b5563;
  font-size: 14px;
  text-decoration: none;
  transition: all 0.15s;
  user-select: none;
}
.menu-item:hover {
  background: #f3f4f6;
  color: #111827;
}
.menu-item.active {
  background: linear-gradient(135deg, rgba(102,126,234,0.12) 0%, rgba(118,75,162,0.12) 100%);
  color: #6d28d9;
  font-weight: 600;
}

.menu-icon { font-size: 16px; width: 20px; text-align: center; }

.settings-main {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  min-height: 500px;
}
</style>
