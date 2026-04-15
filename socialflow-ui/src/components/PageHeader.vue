<!--
  PageHeader.vue —— 统一的页面标题栏组件
  用法：
    <PageHeader title="内容库" subtitle="管理所有生成的文案" icon="Files">
      <template #actions>
        <el-button type="primary">新建</el-button>
      </template>
    </PageHeader>
-->

<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
  icon?: string
}>()
</script>

<template>
  <div class="page-header">
    <div class="header-left">
      <div v-if="icon" class="header-icon">
        <el-icon :size="22"><component :is="icon" /></el-icon>
      </div>
      <div class="header-text">
        <h1 class="header-title">{{ title }}</h1>
        <p v-if="subtitle" class="header-subtitle">{{ subtitle }}</p>
      </div>
    </div>
    <div class="header-right">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sf-space-5);
  padding: var(--sf-space-4) var(--sf-space-5);
  background: #fff;
  border-radius: var(--sf-radius-md);
  box-shadow: var(--sf-shadow-sm);
  border: 1px solid var(--sf-border-light);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--sf-space-3);
}

.header-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--sf-radius-md);
  background: var(--sf-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--sf-shadow-brand);
  flex-shrink: 0;
}

.header-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--sf-text-primary);
  letter-spacing: -0.3px;
}

.header-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--sf-text-muted);
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--sf-space-2);
}
</style>
