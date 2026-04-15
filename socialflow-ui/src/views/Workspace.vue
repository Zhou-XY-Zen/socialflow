<script setup lang="ts">
/**
 * 通用容器壳（Tool Shell）
 * -------------------------------------------------
 * 这里只做"房东"的事：把多个互相独立的工具挂在同一个 URL 入口下，提供顶部 Tab 切换。
 * 壳对工具内部一无所知；工具内部对壳也一无所知。
 *
 * 新增工具：
 *   1. 在 src/views/tools/ 下新建组件
 *   2. 在 src/views/tools/registry.ts 追加一项
 *   本文件不需要改动。
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { tools } from './tools/registry'

const route = useRoute()
const router = useRouter()

/** 当前激活的工具 key —— 优先从 URL 恢复，否则用第一个工具 */
const activeKey = ref<string>(
  (route.query.tool as string) || tools[0]?.key || '',
)

/** Tab 切换 → URL 同步（便于刷新 / 书签 / 分享） */
watch(activeKey, (k) => {
  if (k && route.query.tool !== k) {
    router.replace({ query: { ...route.query, tool: k } })
  }
})

/** URL query 变化（直链 / 浏览器后退）→ 同步到 activeKey */
watch(
  () => route.query.tool,
  (q) => {
    if (
      typeof q === 'string' &&
      q &&
      q !== activeKey.value &&
      tools.some((t) => t.key === q)
    ) {
      activeKey.value = q
    }
  },
)

/** 当前工具：找不到时 fallback 到第一个工具，避免非法 URL 导致空白 */
const currentTool = computed(
  () => tools.find((t) => t.key === activeKey.value) ?? tools[0],
)
</script>

<template>
  <div class="tool-shell">
    <!-- 顶部 Tab 导航 -->
    <el-tabs v-model="activeKey" class="tool-shell-tabs">
      <el-tab-pane
        v-for="t in tools"
        :key="t.key"
        :label="t.label"
        :name="t.key"
      />
    </el-tabs>

    <!-- 工具内容区：keep-alive 保留各工具内部状态 -->
    <div class="tool-shell-content">
      <keep-alive>
        <component
          v-if="currentTool"
          :is="currentTool.component"
          :key="currentTool.key"
        />
      </keep-alive>
    </div>
  </div>
</template>

<style scoped>
/* 壳自身只做一件事：让 tab 在顶、工具在下，撑满视口 */
.tool-shell {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}
.tool-shell-tabs {
  flex-shrink: 0;
  margin-bottom: 8px;
}
.tool-shell-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
