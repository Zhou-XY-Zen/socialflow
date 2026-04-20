<!--
  Dashboard.vue —— 代码分析仪表盘
  4 个指标卡 + 近 30 天趋势折线 + 风险等级饼 + Top 仓库 + 最近分析
-->
<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { AnalysisStats, CodeAnalysis } from '@/types/codeAnalysis'

const router = useRouter()
const stats = ref<AnalysisStats>()
const recent = ref<CodeAnalysis[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const [s, h] = await Promise.all([
      codeAnalysisApi.dashboardStats(),
      codeAnalysisApi.history({ current: 1, size: 8 }),
    ])
    stats.value = s
    recent.value = h.records
  } catch (e: any) {
    ElMessage.error('加载仪表盘失败: ' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

onMounted(load)

// 折线最大值归一化
const trendMax = computed(() => Math.max(1, ...(stats.value?.dailyTrend?.map(d => d.count) || [1])))

// 风险分布计算
const riskTotal = computed(() =>
  (stats.value?.highTotal || 0) + (stats.value?.mediumTotal || 0) + (stats.value?.lowTotal || 0))

const riskSlices = computed(() => {
  if (!stats.value || riskTotal.value === 0) return []
  const total = riskTotal.value
  return [
    { label: '高', count: stats.value.highTotal, color: '#ef4444', percent: stats.value.highTotal / total * 100 },
    { label: '中', count: stats.value.mediumTotal, color: '#f59e0b', percent: stats.value.mediumTotal / total * 100 },
    { label: '低', count: stats.value.lowTotal, color: '#3b82f6', percent: stats.value.lowTotal / total * 100 },
  ]
})

const typeLabels: Record<string, string> = {
  PROJECT_OVERVIEW: '项目概览',
  COMMIT_REVIEW: '提交审查',
  DIFF_REVIEW: '对比分析',
}

const statusLabels: Record<string, { label: string; color: string }> = {
  PENDING: { label: '排队中', color: '#9ca3af' },
  RUNNING: { label: '分析中', color: '#3b82f6' },
  SUCCESS: { label: '已完成', color: '#10b981' },
  FAILED:  { label: '失败',   color: '#ef4444' },
}

function openResult(a: CodeAnalysis) {
  router.push(`/code-analysis/result/${a.id}`)
}

function formatTime(s?: string) {
  if (!s) return ''
  return new Date(s).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function extractRepoName(url: string) {
  if (!url) return ''
  const u = url.endsWith('.git') ? url.slice(0, -4) : url
  const i = u.lastIndexOf('/')
  return i >= 0 ? u.slice(i + 1) : u
}

// 黄山版 1.2.1：避免魔法值。Token 量级阈值集中常量化。
const TOKEN_K = 1000
const TOKEN_M = 1_000_000

/** 把 Token 数字格式化为 K / M 展示 */
function fmtTokens(n?: number) {
  if (n == null || n === 0) return '0'
  if (n < TOKEN_K) return String(n)
  if (n < TOKEN_M) return (n / TOKEN_K).toFixed(1) + 'K'
  return (n / TOKEN_M).toFixed(2) + 'M'
}
</script>

<template>
  <div class="ca-dashboard" v-loading="loading">
    <!-- 指标卡 -->
    <div class="metric-row">
      <div class="metric-card m-blue">
        <div class="m-icon">📊</div>
        <div class="m-body">
          <div class="m-value">{{ stats?.monthlyCount ?? 0 }}</div>
          <div class="m-label">本月分析次数</div>
        </div>
      </div>
      <div class="metric-card m-purple">
        <div class="m-icon">⭐</div>
        <div class="m-body">
          <div class="m-value">{{ stats?.averageScore != null ? stats.averageScore.toFixed(1) : '--' }}</div>
          <div class="m-label">平均评分</div>
        </div>
      </div>
      <div class="metric-card m-red">
        <div class="m-icon">⚠️</div>
        <div class="m-body">
          <div class="m-value">{{ stats?.totalHighRisk ?? 0 }}</div>
          <div class="m-label">高风险总数</div>
        </div>
      </div>
      <div class="metric-card m-green">
        <div class="m-icon">✅</div>
        <div class="m-body">
          <div class="m-value">{{ stats?.resolvedCount ?? 0 }}</div>
          <div class="m-label">已解决风险</div>
        </div>
      </div>
      <div class="metric-card m-cyan" :title="`Prompt ${fmtTokens(stats?.tokensMonthlyPrompt)} · Completion ${fmtTokens(stats?.tokensMonthlyCompletion)}`">
        <div class="m-icon">🧮</div>
        <div class="m-body">
          <div class="m-value">{{ fmtTokens(stats?.tokensMonthly) }}</div>
          <div class="m-label">本月 Token 消耗（{{ stats?.llmCallsMonthly ?? 0 }} 次调用）</div>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-row">
      <!-- 趋势折线 -->
      <div class="chart-card">
        <div class="chart-title">近 30 天分析趋势</div>
        <div class="trend">
          <div v-for="(d, i) in stats?.dailyTrend || []" :key="i" class="trend-bar"
               :title="`${d.date}: ${d.count} 次`">
            <div class="trend-fill" :style="{ height: (d.count / trendMax * 100) + '%' }" />
          </div>
        </div>
        <div class="trend-axis">
          <span>{{ stats?.dailyTrend?.[0]?.date }}</span>
          <span>{{ stats?.dailyTrend?.[stats.dailyTrend.length - 1]?.date }}</span>
        </div>
      </div>

      <!-- 风险分布饼 -->
      <div class="chart-card">
        <div class="chart-title">风险等级分布</div>
        <div v-if="riskSlices.length === 0" class="empty">暂无数据</div>
        <div v-else class="pie-wrapper">
          <svg viewBox="0 0 100 100" class="pie-svg">
            <circle v-for="(s, i) in riskSlices" :key="i"
                    :cx="50" :cy="50" :r="30"
                    fill="transparent" :stroke="s.color" stroke-width="26"
                    :stroke-dasharray="`${s.percent * 1.88} 188`"
                    :stroke-dashoffset="-riskSlices.slice(0, i).reduce((a, b) => a + b.percent, 0) * 1.88"
                    style="transform: rotate(-90deg); transform-origin: center" />
          </svg>
          <div class="pie-legend">
            <div v-for="s in riskSlices" :key="s.label" class="legend-item">
              <span class="legend-dot" :style="{ background: s.color }" />
              {{ s.label }}风险 {{ s.count }}（{{ s.percent.toFixed(0) }}%）
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底栏：最近分析 + 热度仓库 -->
    <div class="bottom-row">
      <div class="panel">
        <div class="panel-title">最近分析</div>
        <div v-if="recent.length === 0" class="empty">还没有任何分析记录，去<el-link type="primary" @click="router.push('/code-analysis/project')">创建一个</el-link></div>
        <div v-else class="recent-list">
          <div v-for="a in recent" :key="a.id" class="recent-item" @click="openResult(a)">
            <span class="type-chip">{{ typeLabels[a.analysisType] }}</span>
            <span class="repo-name">{{ extractRepoName(a.gitUrl) }}</span>
            <span class="time">{{ formatTime(a.createTime) }}</span>
            <span class="status" :style="{ color: statusLabels[a.status].color }">
              {{ statusLabels[a.status].label }}
              <span v-if="a.overallScore != null" class="score">· {{ a.overallScore }} 分</span>
            </span>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-title">热门仓库 Top 5</div>
        <div v-if="(stats?.topRepos?.length ?? 0) === 0" class="empty">暂无</div>
        <div v-else class="hot-list">
          <div v-for="(r, i) in stats?.topRepos" :key="r.gitUrl" class="hot-item">
            <span class="hot-rank" :class="{ top: i < 3 }">#{{ i + 1 }}</span>
            <span class="hot-name">{{ extractRepoName(r.gitUrl) }}</span>
            <span class="hot-count">{{ r.analyzeCount }} 次</span>
            <span v-if="r.lastScore != null" class="hot-score">{{ r.lastScore }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ca-dashboard { padding: 20px; }

/* 指标卡 */
.metric-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; margin-bottom: 20px; }
@media (max-width: 1280px) {
  .metric-row { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 720px) {
  .metric-row { grid-template-columns: 1fr 1fr; }
}
.metric-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  position: relative;
  overflow: hidden;
}
.metric-card::before {
  content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 4px;
}
.metric-card.m-blue::before   { background: linear-gradient(180deg, #3b82f6, #2563eb); }
.metric-card.m-purple::before { background: linear-gradient(180deg, #8b5cf6, #6d28d9); }
.metric-card.m-red::before    { background: linear-gradient(180deg, #ef4444, #b91c1c); }
.metric-card.m-green::before  { background: linear-gradient(180deg, #10b981, #059669); }
.metric-card.m-cyan::before   { background: linear-gradient(180deg, #06b6d4, #0891b2); }
.m-icon { font-size: 36px; }
.m-value { font-size: 28px; font-weight: 700; color: #111827; line-height: 1.2; }
.m-label { color: #6b7280; font-size: 13px; margin-top: 4px; }

/* 图表区 */
.chart-row { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; margin-bottom: 20px; }
.chart-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.chart-title { font-size: 14px; font-weight: 600; color: #374151; margin-bottom: 16px; }
.empty { color: #9ca3af; font-size: 13px; padding: 20px 0; text-align: center; }

/* 趋势条形图 */
.trend { display: flex; align-items: flex-end; gap: 2px; height: 120px; }
.trend-bar { flex: 1; min-width: 4px; height: 100%; display: flex; align-items: flex-end; cursor: pointer; }
.trend-fill {
  width: 100%;
  background: linear-gradient(180deg, #60a5fa, #3b82f6);
  border-radius: 2px 2px 0 0;
  min-height: 2px;
  transition: opacity 0.2s;
}
.trend-bar:hover .trend-fill { opacity: 0.7; }
.trend-axis { display: flex; justify-content: space-between; color: #9ca3af; font-size: 11px; margin-top: 4px; }

/* 饼图 */
.pie-wrapper { display: flex; align-items: center; gap: 18px; }
.pie-svg { width: 130px; height: 130px; flex-shrink: 0; }
.pie-legend { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: #4b5563; }
.legend-item { display: flex; align-items: center; gap: 6px; }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; }

/* 底栏 */
.bottom-row { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; }
.panel {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.panel-title { font-size: 14px; font-weight: 600; color: #374151; margin-bottom: 14px; }

.recent-list { display: flex; flex-direction: column; gap: 8px; }
.recent-item {
  display: grid;
  grid-template-columns: 80px 1fr 110px 130px;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  align-items: center;
  font-size: 13px;
}
.recent-item:hover { background: #f9fafb; }
.type-chip {
  padding: 2px 8px;
  background: #ede9fe;
  color: #6d28d9;
  border-radius: 4px;
  font-size: 11px;
  text-align: center;
}
.repo-name { font-weight: 500; color: #111827; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.time { color: #9ca3af; }
.status { font-weight: 500; text-align: right; }
.score { color: #6b7280; font-weight: 400; }

.hot-list { display: flex; flex-direction: column; gap: 10px; }
.hot-item { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.hot-rank {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f3f4f6;
  color: #9ca3af;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.hot-rank.top { background: linear-gradient(135deg, #fbbf24, #f59e0b); color: #fff; }
.hot-name { flex: 1; color: #111827; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hot-count { color: #6b7280; font-size: 12px; }
.hot-score {
  padding: 2px 8px;
  background: #d1fae5;
  color: #059669;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
</style>
