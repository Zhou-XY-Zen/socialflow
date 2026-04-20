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

function typeChipClass(type: string): string {
  if (type === 'PROJECT_OVERVIEW') return 'is-brand'
  if (type === 'COMMIT_REVIEW') return 'is-info'
  return 'is-warning'
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
    <!-- Hero 欢迎横幅 -->
    <div class="ca-hero sf-scale-in">
      <div class="ca-hero-content">
        <div class="ca-hero-eyebrow">代码分析 · 实时洞察</div>
        <h1 class="ca-hero-title">Code Analysis Dashboard</h1>
        <p class="ca-hero-subtitle">本月完成 <strong>{{ stats?.monthlyCount ?? 0 }}</strong> 次分析 · 识别 <strong>{{ stats?.totalHighRisk ?? 0 }}</strong> 条高风险 · AI 消耗 <strong>{{ fmtTokens(stats?.tokensMonthly) }}</strong> tokens</p>
      </div>
      <div class="ca-hero-cta">
        <el-button size="large" class="ca-hero-btn" @click="router.push('/code-analysis/project')">
          <el-icon><Reading /></el-icon>
          开始新分析
        </el-button>
      </div>
    </div>

    <!-- 指标卡 -->
    <div class="metric-row">
      <div class="sf-metric sf-stagger">
        <div class="sf-icon-bubble is-info"><el-icon><DataLine /></el-icon></div>
        <div>
          <div class="sf-metric-value">{{ stats?.monthlyCount ?? 0 }}</div>
          <div class="sf-metric-label">本月分析次数</div>
        </div>
      </div>
      <div class="sf-metric sf-stagger">
        <div class="sf-icon-bubble is-brand"><el-icon><StarFilled /></el-icon></div>
        <div>
          <div class="sf-metric-value">{{ stats?.averageScore != null ? stats.averageScore.toFixed(1) : '--' }}</div>
          <div class="sf-metric-label">平均评分</div>
        </div>
      </div>
      <div class="sf-metric sf-stagger">
        <div class="sf-icon-bubble is-danger"><el-icon><Warning /></el-icon></div>
        <div>
          <div class="sf-metric-value">{{ stats?.totalHighRisk ?? 0 }}</div>
          <div class="sf-metric-label">高风险总数</div>
        </div>
      </div>
      <div class="sf-metric sf-stagger">
        <div class="sf-icon-bubble is-success"><el-icon><CircleCheck /></el-icon></div>
        <div>
          <div class="sf-metric-value">{{ stats?.resolvedCount ?? 0 }}</div>
          <div class="sf-metric-label">已解决风险</div>
        </div>
      </div>
      <div class="sf-metric sf-stagger" :title="`Prompt ${fmtTokens(stats?.tokensMonthlyPrompt)} · Completion ${fmtTokens(stats?.tokensMonthlyCompletion)}`">
        <div class="sf-icon-bubble is-ocean"><el-icon><Cpu /></el-icon></div>
        <div>
          <div class="sf-metric-value">{{ fmtTokens(stats?.tokensMonthly) }}</div>
          <div class="sf-metric-label">本月 Token · {{ stats?.llmCallsMonthly ?? 0 }} 次调用</div>
        </div>
      </div>
      <div class="sf-metric sf-stagger"
           :title="`累计 INVALID ${stats?.feedbackInvalidCount ?? 0} · IGNORED ${stats?.feedbackIgnoredCount ?? 0} · 屏蔽规约 ${stats?.dismissedRulesCount ?? 0} 条`">
        <div class="sf-icon-bubble is-rose"><el-icon><Remove /></el-icon></div>
        <div>
          <div class="sf-metric-value">{{ stats?.falsePositiveRate ?? 0 }}<span class="m-unit">%</span></div>
          <div class="sf-metric-label">误判率 · 屏蔽 {{ stats?.dismissedRulesCount ?? 0 }} 条规约</div>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-row">
      <!-- 趋势折线 -->
      <div class="sf-panel">
        <div class="sf-panel-title">近 30 天分析趋势</div>
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
      <div class="sf-panel">
        <div class="sf-panel-title">风险等级分布</div>
        <div v-if="riskSlices.length === 0" class="empty">暂无数据</div>
        <div v-else class="pie-wrapper">
          <div class="pie-canvas">
            <svg viewBox="0 0 100 100" class="pie-svg">
              <circle v-for="(s, i) in riskSlices" :key="i"
                      :cx="50" :cy="50" :r="30"
                      fill="transparent" :stroke="s.color" stroke-width="22"
                      :stroke-dasharray="`${s.percent * 1.88} 188`"
                      :stroke-dashoffset="-riskSlices.slice(0, i).reduce((a, b) => a + b.percent, 0) * 1.88"
                      style="transform: rotate(-90deg); transform-origin: center; transition: stroke-dasharray .6s ease" />
            </svg>
            <div class="pie-center">
              <div class="pie-total">{{ riskTotal }}</div>
              <div class="pie-total-label">总数</div>
            </div>
          </div>
          <div class="pie-legend">
            <div v-for="s in riskSlices" :key="s.label" class="legend-item">
              <span class="legend-dot" :style="{ background: s.color }" />
              <span class="legend-label">{{ s.label }}风险</span>
              <span class="legend-value">{{ s.count }}</span>
              <span class="legend-percent">{{ s.percent.toFixed(0) }}%</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底栏：最近分析 + 热度仓库 -->
    <div class="bottom-row">
      <div class="sf-panel">
        <div class="sf-panel-title">最近分析</div>
        <div v-if="recent.length === 0" class="empty">还没有任何分析记录，去<el-link type="primary" @click="router.push('/code-analysis/project')">创建一个</el-link></div>
        <div v-else class="recent-list">
          <div v-for="a in recent" :key="a.id" class="recent-item" @click="openResult(a)">
            <span class="sf-chip" :class="typeChipClass(a.analysisType)">{{ typeLabels[a.analysisType] }}</span>
            <span class="repo-name">{{ extractRepoName(a.gitUrl) }}</span>
            <span class="time">{{ formatTime(a.createTime) }}</span>
            <span class="status" :style="{ color: statusLabels[a.status].color }">
              <span class="status-dot" :style="{ background: statusLabels[a.status].color }"></span>
              {{ statusLabels[a.status].label }}
              <span v-if="a.overallScore != null" class="score">· {{ a.overallScore }} 分</span>
            </span>
          </div>
        </div>
      </div>

      <div class="sf-panel">
        <div class="sf-panel-title">
          热门仓库 Top 5
          <span v-if="(stats?.topInvalidRules?.length ?? 0) > 0" class="panel-sub">· 误报规约 Top {{ stats?.topInvalidRules?.length }}</span>
        </div>
        <div v-if="(stats?.topInvalidRules?.length ?? 0) > 0" class="invalid-list">
          <div v-for="(r, i) in stats?.topInvalidRules" :key="r.ruleRef" class="invalid-item"
               :title="`累计 ${r.count} 次被标 INVALID`">
            <span class="hot-rank" :class="{ top: i < 3 }">#{{ i + 1 }}</span>
            <span class="invalid-rule">{{ r.ruleRef }}</span>
            <span class="invalid-count">{{ r.count }} 次</span>
          </div>
          <div class="invalid-divider" />
        </div>
        <div v-if="(stats?.topRepos?.length ?? 0) === 0" class="empty">暂无热门仓库</div>
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
.ca-dashboard {
  padding: var(--sf-space-5);
  animation: sf-fade-in var(--sf-transition-base);
}

/* ========== Hero 欢迎横幅 ========== */
.ca-hero {
  position: relative;
  background: var(--sf-gradient-aurora);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-6) var(--sf-space-7);
  margin-bottom: var(--sf-space-5);
  color: #fff;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sf-space-5);
  box-shadow: var(--sf-shadow-glow-brand);
}
.ca-hero::before {
  content: '';
  position: absolute;
  top: -40%; right: -10%;
  width: 440px; height: 440px;
  background: radial-gradient(circle, rgba(255,255,255,0.2), transparent 62%);
  border-radius: 50%;
  pointer-events: none;
}
.ca-hero::after {
  content: '';
  position: absolute;
  bottom: -50%; left: 20%;
  width: 360px; height: 360px;
  background: radial-gradient(circle, rgba(255,255,255,0.08), transparent 60%);
  border-radius: 50%;
  pointer-events: none;
}
.ca-hero-content { position: relative; z-index: 1; flex: 1; min-width: 0; }
.ca-hero-eyebrow {
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  opacity: 0.9;
  margin-bottom: var(--sf-space-2);
}
.ca-hero-title {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 var(--sf-space-2);
  letter-spacing: -0.02em;
}
.ca-hero-subtitle {
  margin: 0;
  font-size: 14px;
  opacity: 0.92;
  line-height: 1.6;
}
.ca-hero-subtitle strong {
  color: #fff;
  font-weight: 700;
  padding: 0 4px;
  font-variant-numeric: tabular-nums;
}
.ca-hero-cta { position: relative; z-index: 1; }
.ca-hero-btn {
  background: rgba(255,255,255,0.15) !important;
  color: #fff !important;
  border: 1px solid rgba(255,255,255,0.3) !important;
  backdrop-filter: blur(10px);
  font-weight: 600 !important;
  padding: 12px 24px !important;
  transition: all var(--sf-transition-base) !important;
}
.ca-hero-btn:hover {
  background: rgba(255,255,255,0.25) !important;
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.15) !important;
}

/* ========== 指标卡 ========== */
.metric-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: var(--sf-space-3);
  margin-bottom: var(--sf-space-5);
}
@media (max-width: 1500px) { .metric-row { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 720px)  { .metric-row { grid-template-columns: 1fr 1fr; } }
.m-unit { font-size: 18px; font-weight: 500; color: var(--sf-text-tertiary); margin-left: 2px; }

/* ========== 图表区 ========== */
.chart-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--sf-space-4);
  margin-bottom: var(--sf-space-5);
}

.empty {
  color: var(--sf-text-muted);
  font-size: 13px;
  padding: var(--sf-space-5) 0;
  text-align: center;
}

/* 趋势条形图 */
.trend {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 140px;
  padding: 0 4px;
}
.trend-bar {
  flex: 1;
  min-width: 4px;
  height: 100%;
  display: flex;
  align-items: flex-end;
  cursor: pointer;
}
.trend-fill {
  width: 100%;
  background: linear-gradient(180deg, #818cf8 0%, #667eea 70%, #5a67d8 100%);
  border-radius: 3px 3px 0 0;
  min-height: 3px;
  transition: all var(--sf-transition-fast);
  box-shadow: 0 1px 2px rgba(102, 126, 234, 0.2);
}
.trend-bar:hover .trend-fill {
  transform: scaleY(1.05);
  transform-origin: bottom;
  filter: brightness(1.1);
}
.trend-axis {
  display: flex;
  justify-content: space-between;
  color: var(--sf-text-muted);
  font-size: 11px;
  margin-top: var(--sf-space-2);
}

/* 饼图 */
.pie-wrapper {
  display: flex;
  align-items: center;
  gap: var(--sf-space-5);
}
.pie-canvas {
  position: relative;
  width: 150px;
  height: 150px;
  flex-shrink: 0;
}
.pie-svg {
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 2px 6px rgba(0,0,0,0.06));
}
.pie-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.pie-total {
  font-size: 28px;
  font-weight: 700;
  color: var(--sf-text-primary);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.pie-total-label {
  font-size: 11px;
  color: var(--sf-text-muted);
  margin-top: 2px;
}
.pie-legend {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--sf-space-2);
  font-size: 13px;
}
.legend-item {
  display: grid;
  grid-template-columns: 12px 1fr auto auto;
  align-items: center;
  gap: var(--sf-space-2);
  padding: 6px 8px;
  border-radius: var(--sf-radius-sm);
  transition: background var(--sf-transition-fast);
}
.legend-item:hover { background: var(--sf-bg-subtle); }
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(255,255,255,0.8);
}
.legend-label { color: var(--sf-text-secondary); }
.legend-value {
  font-weight: 700;
  color: var(--sf-text-primary);
  font-variant-numeric: tabular-nums;
}
.legend-percent {
  color: var(--sf-text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

/* ========== 底栏 ========== */
.bottom-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--sf-space-4);
}

.recent-list { display: flex; flex-direction: column; gap: var(--sf-space-2); }
.recent-item {
  display: grid;
  grid-template-columns: 90px 1fr 110px 140px;
  gap: var(--sf-space-3);
  padding: var(--sf-space-3);
  border-radius: var(--sf-radius-sm);
  cursor: pointer;
  transition: all var(--sf-transition-fast);
  align-items: center;
  font-size: 13px;
  border: 1px solid transparent;
}
.recent-item:hover {
  background: var(--sf-surface-hover);
  border-color: var(--sf-border-light);
  transform: translateX(2px);
}
.repo-name {
  font-weight: 500;
  color: var(--sf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.time {
  color: var(--sf-text-muted);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}
.status {
  font-weight: 500;
  text-align: right;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
  box-shadow: 0 0 6px currentColor;
}
.score { color: var(--sf-text-tertiary); font-weight: 400; }

.hot-list { display: flex; flex-direction: column; gap: var(--sf-space-2); }
.hot-item {
  display: flex;
  align-items: center;
  gap: var(--sf-space-3);
  font-size: 13px;
  padding: 6px 8px;
  border-radius: var(--sf-radius-sm);
  transition: background var(--sf-transition-fast);
}
.hot-item:hover { background: var(--sf-bg-subtle); }
.hot-rank {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--sf-bg-subtle);
  color: var(--sf-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
  border: 1px solid var(--sf-border-light);
}
.hot-rank.top {
  background: var(--sf-gradient-sunset);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 4px 10px rgba(251, 191, 36, 0.3);
}
.hot-name {
  flex: 1;
  color: var(--sf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
.hot-count {
  color: var(--sf-text-tertiary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.hot-score {
  padding: 2px 10px;
  background: var(--sf-success-bg);
  color: var(--sf-success);
  border-radius: var(--sf-radius-full);
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* Wave 8 误报 Top */
.panel-sub {
  color: var(--sf-text-muted);
  font-size: 12px;
  font-weight: 400;
  margin-left: 6px;
}
.invalid-list {
  display: flex;
  flex-direction: column;
  gap: var(--sf-space-2);
  margin-bottom: var(--sf-space-3);
}
.invalid-item {
  display: flex;
  align-items: center;
  gap: var(--sf-space-3);
  font-size: 13px;
  padding: 6px 8px;
  border-radius: var(--sf-radius-sm);
  transition: background var(--sf-transition-fast);
}
.invalid-item:hover { background: var(--sf-danger-bg); }
.invalid-rule {
  flex: 1;
  color: var(--sf-danger);
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.invalid-count {
  padding: 2px 10px;
  background: var(--sf-danger-bg);
  color: var(--sf-danger);
  border-radius: var(--sf-radius-full);
  font-size: 11px;
  font-weight: 700;
}
.invalid-divider {
  border-top: 1px dashed var(--sf-border);
  margin: var(--sf-space-2) 0;
}
</style>
