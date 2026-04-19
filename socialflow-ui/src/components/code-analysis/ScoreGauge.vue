<!--
  ScoreGauge.vue —— 0-100 评分仪表盘
  半环形 SVG + 颜色按分段（红/橙/绿）+ 居中大字
-->
<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  score?: number | null
  size?: number
  label?: string
}>()

const size = computed(() => props.size ?? 200)
const radius = computed(() => size.value / 2 - 18)
const circumference = computed(() => Math.PI * radius.value)

const scoreValue = computed(() => (props.score == null ? 0 : Math.max(0, Math.min(100, props.score))))
const dashOffset = computed(() => circumference.value * (1 - scoreValue.value / 100))

const color = computed(() => {
  const s = scoreValue.value
  if (s >= 85) return '#10b981' // green
  if (s >= 70) return '#3b82f6' // blue
  if (s >= 50) return '#f59e0b' // orange
  return '#ef4444'              // red
})

const verdict = computed(() => {
  const s = scoreValue.value
  if (s >= 90) return '优秀'
  if (s >= 75) return '良好'
  if (s >= 60) return '合格'
  if (s >= 40) return '待改进'
  return '高风险'
})
</script>

<template>
  <div class="gauge">
    <svg :width="size" :height="size / 1.6" :viewBox="`0 0 ${size} ${size / 2 + 20}`">
      <!-- 背景半环 -->
      <path
        :d="`M 18 ${size / 2} A ${radius} ${radius} 0 0 1 ${size - 18} ${size / 2}`"
        fill="none"
        stroke="#e5e7eb"
        stroke-width="14"
        stroke-linecap="round"
      />
      <!-- 评分弧 -->
      <path
        :d="`M 18 ${size / 2} A ${radius} ${radius} 0 0 1 ${size - 18} ${size / 2}`"
        fill="none"
        :stroke="color"
        stroke-width="14"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
        style="transition: stroke-dashoffset 0.8s ease, stroke 0.3s"
      />
      <text
        :x="size / 2"
        :y="size / 2 - 4"
        text-anchor="middle"
        :fill="color"
        :font-size="size / 4"
        font-weight="700"
      >
        {{ props.score == null ? '--' : scoreValue }}
      </text>
      <text
        :x="size / 2"
        :y="size / 2 + 18"
        text-anchor="middle"
        fill="#6b7280"
        font-size="14"
      >
        {{ verdict }}
      </text>
    </svg>
    <div v-if="label" class="gauge-label">{{ label }}</div>
  </div>
</template>

<style scoped>
.gauge {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.gauge-label {
  color: #6b7280;
  font-size: 13px;
  margin-top: -6px;
}
</style>
