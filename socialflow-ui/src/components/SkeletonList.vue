<!--
  SkeletonList.vue —— 骨架屏组件
  用法：
    <SkeletonList v-if="loading" :rows="5" type="table" />
  type: table（表格行）| card（卡片）| grid（网格）
-->

<script setup lang="ts">
defineProps<{
  rows?: number
  type?: 'table' | 'card' | 'grid'
}>()
</script>

<template>
  <!-- 表格骨架屏 -->
  <div v-if="!type || type === 'table'" class="skeleton-table">
    <div v-for="i in (rows || 5)" :key="i" class="skeleton-row">
      <div class="sf-skeleton skeleton-cell skeleton-cell-sm"></div>
      <div class="sf-skeleton skeleton-cell skeleton-cell-lg"></div>
      <div class="sf-skeleton skeleton-cell skeleton-cell-md"></div>
      <div class="sf-skeleton skeleton-cell skeleton-cell-sm"></div>
    </div>
  </div>

  <!-- 卡片骨架屏 -->
  <div v-else-if="type === 'card'" class="skeleton-card-list">
    <div v-for="i in (rows || 3)" :key="i" class="skeleton-card">
      <div class="sf-skeleton skeleton-avatar"></div>
      <div class="skeleton-card-content">
        <div class="sf-skeleton skeleton-line skeleton-line-lg"></div>
        <div class="sf-skeleton skeleton-line skeleton-line-md"></div>
        <div class="sf-skeleton skeleton-line skeleton-line-sm"></div>
      </div>
    </div>
  </div>

  <!-- 网格骨架屏 -->
  <div v-else class="skeleton-grid">
    <div v-for="i in (rows || 8)" :key="i" class="skeleton-grid-item">
      <div class="sf-skeleton skeleton-thumb"></div>
      <div class="sf-skeleton skeleton-line skeleton-line-md"></div>
      <div class="sf-skeleton skeleton-line skeleton-line-sm"></div>
    </div>
  </div>
</template>

<style scoped>
/* 表格 */
.skeleton-table {
  padding: var(--sf-space-3);
}

.skeleton-row {
  display: flex;
  align-items: center;
  gap: var(--sf-space-4);
  padding: var(--sf-space-3) var(--sf-space-2);
  border-bottom: 1px solid var(--sf-border-light);
}

.skeleton-cell {
  height: 16px;
}

.skeleton-cell-sm { width: 80px; }
.skeleton-cell-md { width: 140px; }
.skeleton-cell-lg { flex: 1; }

/* 卡片 */
.skeleton-card-list {
  display: flex;
  flex-direction: column;
  gap: var(--sf-space-3);
}

.skeleton-card {
  display: flex;
  gap: var(--sf-space-3);
  padding: var(--sf-space-4);
  background: #fff;
  border-radius: var(--sf-radius-md);
  border: 1px solid var(--sf-border-light);
}

.skeleton-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  flex-shrink: 0;
}

.skeleton-card-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 网格 */
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--sf-space-3);
}

.skeleton-grid-item {
  background: #fff;
  border-radius: var(--sf-radius-md);
  overflow: hidden;
  border: 1px solid var(--sf-border-light);
}

.skeleton-thumb {
  aspect-ratio: 4 / 3;
  border-radius: 0;
}

.skeleton-grid-item .skeleton-line {
  margin: var(--sf-space-2) var(--sf-space-3);
}

.skeleton-grid-item .skeleton-line:last-child {
  margin-bottom: var(--sf-space-3);
}

/* 通用行 */
.skeleton-line {
  height: 14px;
}

.skeleton-line-sm { width: 40%; }
.skeleton-line-md { width: 70%; }
.skeleton-line-lg { width: 90%; }
</style>
