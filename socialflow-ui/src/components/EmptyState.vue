<!--
  EmptyState.vue —— 精美的空状态组件
  用法：
    <EmptyState icon="Files" title="暂无数据" description="开始创建你的第一篇文案吧" actionText="立即创建" @action="handleCreate" />
-->

<script setup lang="ts">
defineProps<{
  icon?: string
  title: string
  description?: string
  actionText?: string
  size?: 'small' | 'medium' | 'large'
}>()

defineEmits<{
  (e: 'action'): void
}>()
</script>

<template>
  <div class="empty-state" :class="`empty-state--${size || 'medium'}`">
    <div class="empty-icon-wrap">
      <div class="empty-icon">
        <el-icon :size="40"><component :is="icon || 'DocumentRemove'" /></el-icon>
      </div>
      <div class="empty-icon-bg"></div>
    </div>
    <h3 class="empty-title">{{ title }}</h3>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <el-button
      v-if="actionText"
      type="primary"
      class="empty-action"
      @click="$emit('action')"
    >
      {{ actionText }}
    </el-button>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--sf-space-7) var(--sf-space-5);
  text-align: center;
}

.empty-state--small { padding: var(--sf-space-5); }
.empty-state--large { padding: var(--sf-space-8) var(--sf-space-5); }

.empty-icon-wrap {
  position: relative;
  width: 96px;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--sf-space-4);
}

.empty-icon {
  position: relative;
  z-index: 1;
  width: 72px;
  height: 72px;
  border-radius: var(--sf-radius-lg);
  background: var(--sf-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--sf-shadow-brand);
}

.empty-icon-bg {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--sf-gradient-soft);
  animation: sf-pulse 2s ease-in-out infinite;
}

.empty-title {
  margin: 0 0 var(--sf-space-2);
  font-size: 17px;
  font-weight: 600;
  color: var(--sf-text-primary);
}

.empty-desc {
  margin: 0 0 var(--sf-space-5);
  font-size: 13px;
  color: var(--sf-text-muted);
  max-width: 320px;
  line-height: 1.6;
}

.empty-action {
  min-width: 120px;
}
</style>
