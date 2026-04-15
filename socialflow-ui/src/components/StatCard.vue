<!--
  StatCard.vue —— 渐变统计卡片
  用法：
    <StatCard label="总内容数" :value="123" icon="Files" color="primary" :trend="12.5" />
  color 可选：primary（紫）、success（绿）、warning（橙）、danger（红）、info（蓝）
  trend 正数显示上升绿色，负数显示下降红色
-->

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label: string
  value: number | string
  icon?: string
  color?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  trend?: number
  hint?: string
}>()

const colorStyle = computed(() => {
  const map = {
    primary: { bg: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', shadow: 'rgba(102, 126, 234, 0.35)' },
    success: { bg: 'linear-gradient(135deg, #10b981 0%, #059669 100%)', shadow: 'rgba(16, 185, 129, 0.35)' },
    warning: { bg: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)', shadow: 'rgba(245, 158, 11, 0.35)' },
    danger: { bg: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)', shadow: 'rgba(239, 68, 68, 0.35)' },
    info: { bg: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)', shadow: 'rgba(59, 130, 246, 0.35)' },
  }
  return map[props.color || 'primary']
})
</script>

<template>
  <div class="stat-card">
    <div class="stat-icon" :style="{ background: colorStyle.bg, boxShadow: `0 8px 20px ${colorStyle.shadow}` }">
      <el-icon :size="24"><component :is="icon || 'DataLine'" /></el-icon>
    </div>
    <div class="stat-content">
      <div class="stat-label">{{ label }}</div>
      <div class="stat-value">{{ value }}</div>
      <div v-if="trend !== undefined || hint" class="stat-footer">
        <span v-if="trend !== undefined" class="stat-trend" :class="{ positive: trend > 0, negative: trend < 0 }">
          <el-icon :size="12">
            <component :is="trend > 0 ? 'Top' : trend < 0 ? 'Bottom' : 'Minus'" />
          </el-icon>
          {{ Math.abs(trend) }}%
        </span>
        <span v-if="hint" class="stat-hint">{{ hint }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  background: #fff;
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-4);
  display: flex;
  align-items: center;
  gap: var(--sf-space-3);
  box-shadow: var(--sf-shadow-sm);
  border: 1px solid var(--sf-border-light);
  transition: all var(--sf-transition-base);
  cursor: default;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--sf-shadow-md);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--sf-radius-md);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 12px;
  color: var(--sf-text-muted);
  font-weight: 500;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--sf-text-primary);
  line-height: 1.2;
  letter-spacing: -0.5px;
}

.stat-footer {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.stat-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: var(--sf-radius-sm);
  background: var(--sf-bg-subtle);
  color: var(--sf-text-muted);
}

.stat-trend.positive {
  background: var(--sf-success-bg);
  color: var(--sf-success);
}

.stat-trend.negative {
  background: var(--sf-danger-bg);
  color: var(--sf-danger);
}

.stat-hint {
  color: var(--sf-text-muted);
}
</style>
