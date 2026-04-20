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

/**
 * 生成指标卡底部的 mini sparkline polyline 点。
 * 取 dailyTrend 末尾 N 个点做归一化，输出 `x,y x,y ...` 形式。
 */
function sparklinePoints(n = 14, width = 80, height = 24): string {
  const trend = stats.value?.dailyTrend || []
  const tail = trend.slice(-n).map(d => d.count)
  if (tail.length === 0) return ''
  const max = Math.max(1, ...tail)
  const step = width / Math.max(1, tail.length - 1)
  return tail.map((v, i) => `${(i * step).toFixed(1)},${(height - (v / max) * height).toFixed(1)}`).join(' ')
}

/** 构造趋势面积图的 SVG path（area + line） */
const trendChart = computed(() => {
  const trend = stats.value?.dailyTrend || []
  if (trend.length === 0) return { area: '', line: '', points: [] as Array<{x: number; y: number; count: number; date: string}>, maxY: 1, meanY: 0 }
  const W = 900 // viewBox 宽，等比缩放
  const H = 140
  const paddingY = 12
  const vals = trend.map(d => d.count)
  const max = Math.max(1, ...vals)
  const mean = vals.reduce((a, b) => a + b, 0) / vals.length
  const step = W / Math.max(1, trend.length - 1)
  const pts = trend.map((d, i) => ({
    x: i * step,
    y: H - paddingY - (d.count / max) * (H - 2 * paddingY),
    count: d.count,
    date: String(d.date),
  }))
  const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
  const area = `${line} L${W},${H} L0,${H} Z`
  const meanY = H - paddingY - (mean / max) * (H - 2 * paddingY)
  return { area, line, points: pts, maxY: max, meanY }
})

const hoverPoint = ref<{ date: string; count: number; x: number; y: number } | null>(null)

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

/** 分数徽章配色：≥90 绿 · ≥70 蓝 · ≥50 橙 · 其余红 */
function scoreBadgeClass(score: number): string {
  if (score >= 90) return 'score-a'
  if (score >= 70) return 'score-b'
  if (score >= 50) return 'score-c'
  return 'score-d'
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
    <!-- Hero 欢迎横幅 —— 带水印 + 快捷入口胶囊 -->
    <div class="ca-hero sf-scale-in">
      <!-- 水印装饰：大号透明字母 -->
      <div class="ca-hero-watermark" aria-hidden="true">CA</div>
      <!-- 网格图案装饰 -->
      <div class="ca-hero-pattern" aria-hidden="true"></div>

      <div class="ca-hero-content">
        <div class="ca-hero-eyebrow">
          <span class="ca-hero-live-dot"></span>
          代码分析 · 实时洞察
        </div>
        <h1 class="ca-hero-title">Code Analysis Dashboard</h1>
        <p class="ca-hero-subtitle">
          本月完成 <strong>{{ stats?.monthlyCount ?? 0 }}</strong> 次分析 ·
          识别 <strong>{{ stats?.totalHighRisk ?? 0 }}</strong> 条高风险 ·
          AI 消耗 <strong>{{ fmtTokens(stats?.tokensMonthly) }}</strong> tokens
        </p>
        <!-- 快捷入口胶囊 -->
        <div class="ca-hero-shortcuts">
          <button class="ca-shortcut" @click="router.push('/code-analysis/project')">
            <el-icon><Reading /></el-icon>项目概览
          </button>
          <button class="ca-shortcut" @click="router.push('/code-analysis/review')">
            <el-icon><Search /></el-icon>提交审查
          </button>
          <button class="ca-shortcut" @click="router.push('/code-analysis/diff')">
            <el-icon><Connection /></el-icon>对比分析
          </button>
          <button class="ca-shortcut" @click="router.push('/code-analysis/rules')">
            <el-icon><DocumentCopy /></el-icon>规约库
          </button>
        </div>
      </div>

      <div class="ca-hero-cta">
        <el-button size="large" class="ca-hero-btn" @click="router.push('/code-analysis/project')">
          <el-icon><Promotion /></el-icon>
          开始新分析
        </el-button>
      </div>
    </div>

    <!-- 指标卡（带 sparkline + 趋势徽章） -->
    <div class="metric-row">
      <div class="metric sf-stagger">
        <div class="metric-head">
          <div class="sf-icon-bubble is-info"><el-icon><DataLine /></el-icon></div>
          <span class="metric-badge is-up">本月</span>
        </div>
        <div class="metric-value">{{ stats?.monthlyCount ?? 0 }}</div>
        <div class="metric-label">本月分析次数</div>
        <svg class="metric-sparkline" viewBox="0 0 80 24" preserveAspectRatio="none">
          <polyline :points="sparklinePoints()" fill="none" stroke="url(#spark-blue)" stroke-width="2" stroke-linejoin="round" />
          <defs>
            <linearGradient id="spark-blue" x1="0" x2="1" y1="0" y2="0">
              <stop offset="0%" stop-color="#60a5fa" />
              <stop offset="100%" stop-color="#3b82f6" />
            </linearGradient>
          </defs>
        </svg>
      </div>

      <div class="metric sf-stagger">
        <div class="metric-head">
          <div class="sf-icon-bubble is-brand"><el-icon><StarFilled /></el-icon></div>
          <span class="metric-badge is-brand">评分</span>
        </div>
        <div class="metric-value">{{ stats?.averageScore != null ? stats.averageScore.toFixed(1) : '--' }}</div>
        <div class="metric-label">平均评分 · 满分 100</div>
        <div class="metric-progress">
          <div class="metric-progress-fill is-brand"
               :style="{ width: (stats?.averageScore ?? 0) + '%' }"></div>
        </div>
      </div>

      <div class="metric sf-stagger">
        <div class="metric-head">
          <div class="sf-icon-bubble is-danger"><el-icon><Warning /></el-icon></div>
          <span class="metric-badge is-danger">需关注</span>
        </div>
        <div class="metric-value">{{ stats?.totalHighRisk ?? 0 }}</div>
        <div class="metric-label">高风险总数</div>
        <svg class="metric-sparkline" viewBox="0 0 80 24" preserveAspectRatio="none">
          <polyline :points="sparklinePoints()" fill="none" stroke="url(#spark-red)" stroke-width="2" stroke-linejoin="round" />
          <defs>
            <linearGradient id="spark-red" x1="0" x2="1" y1="0" y2="0">
              <stop offset="0%" stop-color="#f87171" />
              <stop offset="100%" stop-color="#dc2626" />
            </linearGradient>
          </defs>
        </svg>
      </div>

      <div class="metric sf-stagger">
        <div class="metric-head">
          <div class="sf-icon-bubble is-success"><el-icon><CircleCheck /></el-icon></div>
          <span class="metric-badge is-success">已处理</span>
        </div>
        <div class="metric-value">{{ stats?.resolvedCount ?? 0 }}</div>
        <div class="metric-label">已解决风险</div>
        <svg class="metric-sparkline" viewBox="0 0 80 24" preserveAspectRatio="none">
          <polyline :points="sparklinePoints()" fill="none" stroke="url(#spark-green)" stroke-width="2" stroke-linejoin="round" />
          <defs>
            <linearGradient id="spark-green" x1="0" x2="1" y1="0" y2="0">
              <stop offset="0%" stop-color="#34d399" />
              <stop offset="100%" stop-color="#059669" />
            </linearGradient>
          </defs>
        </svg>
      </div>

      <div class="metric sf-stagger" :title="`Prompt ${fmtTokens(stats?.tokensMonthlyPrompt)} · Completion ${fmtTokens(stats?.tokensMonthlyCompletion)}`">
        <div class="metric-head">
          <div class="sf-icon-bubble is-ocean"><el-icon><Cpu /></el-icon></div>
          <span class="metric-badge is-ocean">{{ stats?.llmCallsMonthly ?? 0 }} 次</span>
        </div>
        <div class="metric-value">{{ fmtTokens(stats?.tokensMonthly) }}</div>
        <div class="metric-label">本月 Token 消耗</div>
        <div class="metric-sub-stats">
          <span class="mss-item"><span class="mss-dot" style="background:#60a5fa"></span>in {{ fmtTokens(stats?.tokensMonthlyPrompt) }}</span>
          <span class="mss-item"><span class="mss-dot" style="background:#a78bfa"></span>out {{ fmtTokens(stats?.tokensMonthlyCompletion) }}</span>
        </div>
      </div>

      <div class="metric sf-stagger"
           :title="`累计 INVALID ${stats?.feedbackInvalidCount ?? 0} · IGNORED ${stats?.feedbackIgnoredCount ?? 0} · 屏蔽规约 ${stats?.dismissedRulesCount ?? 0} 条`">
        <div class="metric-head">
          <div class="sf-icon-bubble is-rose"><el-icon><Remove /></el-icon></div>
          <span class="metric-badge is-warning">反馈闭环</span>
        </div>
        <div class="metric-value">{{ stats?.falsePositiveRate ?? 0 }}<span class="m-unit">%</span></div>
        <div class="metric-label">误判率 · 屏蔽 {{ stats?.dismissedRulesCount ?? 0 }} 条规约</div>
        <div class="metric-progress">
          <div class="metric-progress-fill is-rose"
               :style="{ width: Math.min(100, (stats?.falsePositiveRate ?? 0) * 5) + '%' }"></div>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-row">
      <!-- 趋势面积图 -->
      <div class="sf-panel trend-panel">
        <div class="trend-header">
          <div class="sf-panel-title">近 30 天分析趋势</div>
          <div class="trend-legend-line">
            <span class="trend-legend-mean">— 均值线</span>
          </div>
        </div>
        <div class="trend-wrap" @mousemove="(e) => {
          const svg = (e.currentTarget as HTMLElement).querySelector('svg')
          if (!svg) return
          const rect = svg.getBoundingClientRect()
          const x = (e.clientX - rect.left) / rect.width * 900
          const pts = trendChart.points
          if (pts.length === 0) return
          let nearest = pts[0]
          let minDx = Math.abs(pts[0].x - x)
          for (const p of pts) {
            const dx = Math.abs(p.x - x)
            if (dx < minDx) { minDx = dx; nearest = p }
          }
          hoverPoint = { date: nearest.date, count: nearest.count, x: nearest.x / 900 * rect.width, y: nearest.y / 140 * rect.height }
        }"
        @mouseleave="hoverPoint = null">
          <svg viewBox="0 0 900 140" preserveAspectRatio="none" class="trend-svg">
            <defs>
              <linearGradient id="trend-area" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stop-color="#667eea" stop-opacity="0.5" />
                <stop offset="100%" stop-color="#667eea" stop-opacity="0" />
              </linearGradient>
              <linearGradient id="trend-line" x1="0" x2="1" y1="0" y2="0">
                <stop offset="0%" stop-color="#667eea" />
                <stop offset="100%" stop-color="#a855f7" />
              </linearGradient>
            </defs>
            <!-- 横向网格线 -->
            <g stroke="#e5e7eb" stroke-dasharray="3 4" stroke-width="0.8">
              <line x1="0" y1="35"  x2="900" y2="35"  />
              <line x1="0" y1="70"  x2="900" y2="70"  />
              <line x1="0" y1="105" x2="900" y2="105" />
            </g>
            <!-- 面积 -->
            <path :d="trendChart.area" fill="url(#trend-area)" />
            <!-- 线 -->
            <path :d="trendChart.line" fill="none" stroke="url(#trend-line)" stroke-width="2.5" stroke-linejoin="round" />
            <!-- 均值虚线 -->
            <line :x1="0" :x2="900" :y1="trendChart.meanY" :y2="trendChart.meanY"
                  stroke="#f59e0b" stroke-width="1" stroke-dasharray="4 4" />
            <!-- 数据点 -->
            <circle v-for="(p, i) in trendChart.points" :key="i"
                    :cx="p.x" :cy="p.y" r="2.5" fill="#667eea"
                    style="transition: r 0.2s" />
            <!-- hover 垂直辅助线 + 高亮点 -->
            <g v-if="hoverPoint">
              <line :x1="hoverPoint.x / 1 * 900 / ((trendChart.points[0]?.x ?? 0) + 1) || 0"
                    :x2="trendChart.points.find(p => p.date === hoverPoint!.date)?.x || 0"
                    :y1="0" :y2="140" stroke="#667eea" stroke-width="1" stroke-dasharray="2 2" opacity="0.4" />
            </g>
          </svg>
          <!-- hover 悬浮气泡 -->
          <div v-if="hoverPoint" class="trend-tooltip"
               :style="{ left: hoverPoint.x + 'px', top: (hoverPoint.y - 12) + 'px' }">
            <div class="tt-date">{{ hoverPoint.date }}</div>
            <div class="tt-count">{{ hoverPoint.count }} <span>次</span></div>
          </div>
        </div>
        <div class="trend-axis">
          <span>{{ stats?.dailyTrend?.[0]?.date }}</span>
          <span class="axis-mean">均值 {{ (trendChart.points.length ? (trendChart.points.reduce((a,p)=>a+p.count,0) / trendChart.points.length).toFixed(1) : '0') }}</span>
          <span>{{ stats?.dailyTrend?.[stats.dailyTrend.length - 1]?.date }}</span>
        </div>
      </div>

      <!-- 风险分布饼 -->
      <div class="sf-panel">
        <div class="sf-panel-title">风险等级分布</div>
        <div v-if="riskSlices.length === 0" class="empty">暂无数据</div>
        <div v-else class="pie-wrapper">
          <div class="pie-canvas">
            <!-- 外光晕 -->
            <div class="pie-glow"></div>
            <svg viewBox="0 0 100 100" class="pie-svg">
              <!-- 底圆（浅灰） -->
              <circle cx="50" cy="50" r="30" fill="transparent" stroke="#f3f4f6" stroke-width="22" />
              <circle v-for="(s, i) in riskSlices" :key="i"
                      :cx="50" :cy="50" :r="30"
                      fill="transparent" :stroke="s.color" stroke-width="22"
                      stroke-linecap="round"
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
          <div v-for="a in recent" :key="a.id" class="recent-item" @click="openResult(a)"
               :class="'status-' + a.status.toLowerCase()">
            <span class="recent-accent"></span>
            <span class="sf-chip" :class="typeChipClass(a.analysisType)">{{ typeLabels[a.analysisType] }}</span>
            <span class="repo-name">{{ extractRepoName(a.gitUrl) }}</span>
            <span class="time">{{ formatTime(a.createTime) }}</span>
            <span class="status" :style="{ color: statusLabels[a.status].color }">
              <span class="status-dot" :style="{ background: statusLabels[a.status].color }"></span>
              {{ statusLabels[a.status].label }}
            </span>
            <span v-if="a.overallScore != null" class="score-badge" :class="scoreBadgeClass(a.overallScore)">
              {{ a.overallScore }}
            </span>
            <span v-else class="score-badge score-empty">—</span>
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
  min-height: 180px;
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
/* Hero 背景水印：巨大半透明字母 */
.ca-hero-watermark {
  position: absolute;
  right: 7%;
  top: 50%;
  transform: translateY(-50%);
  font-size: 280px;
  font-weight: 900;
  line-height: 1;
  color: rgba(255,255,255,0.08);
  letter-spacing: -0.04em;
  font-family: 'SF Pro Display', -apple-system, sans-serif;
  pointer-events: none;
  user-select: none;
}
/* Hero 背景网格点阵 */
.ca-hero-pattern {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(255,255,255,0.12) 1px, transparent 1px);
  background-size: 24px 24px;
  mask-image: linear-gradient(90deg, #000 0%, transparent 60%);
  -webkit-mask-image: linear-gradient(90deg, #000 0%, transparent 60%);
  opacity: 0.5;
  pointer-events: none;
}

.ca-hero-content { position: relative; z-index: 1; flex: 1; min-width: 0; }
.ca-hero-eyebrow {
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  opacity: 0.92;
  margin-bottom: var(--sf-space-3);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.ca-hero-live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4ade80;
  box-shadow: 0 0 0 4px rgba(74, 222, 128, 0.3);
  animation: sf-glow-pulse 1.6s ease-in-out infinite;
}
.ca-hero-title {
  font-size: 34px;
  font-weight: 700;
  margin: 0 0 var(--sf-space-3);
  letter-spacing: -0.02em;
}
.ca-hero-subtitle {
  margin: 0 0 var(--sf-space-4);
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
/* 快捷入口胶囊 */
.ca-hero-shortcuts {
  display: flex;
  gap: var(--sf-space-2);
  flex-wrap: wrap;
}
.ca-shortcut {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  background: rgba(255,255,255,0.14);
  border: 1px solid rgba(255,255,255,0.25);
  border-radius: var(--sf-radius-full);
  color: #fff;
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  backdrop-filter: blur(8px);
  transition: all var(--sf-transition-fast);
}
.ca-shortcut:hover {
  background: rgba(255,255,255,0.25);
  transform: translateY(-1px);
  border-color: rgba(255,255,255,0.5);
}

.ca-hero-cta { position: relative; z-index: 1; flex-shrink: 0; }
.ca-hero-btn {
  background: rgba(255,255,255,0.2) !important;
  color: #fff !important;
  border: 1px solid rgba(255,255,255,0.35) !important;
  backdrop-filter: blur(10px);
  font-weight: 600 !important;
  padding: 14px 28px !important;
  transition: all var(--sf-transition-base) !important;
  font-size: 15px !important;
}
.ca-hero-btn:hover {
  background: rgba(255,255,255,0.3) !important;
  transform: translateY(-2px);
  box-shadow: 0 12px 30px rgba(0,0,0,0.2) !important;
}

/* ========== 指标卡 —— 升级为带 sparkline/徽章/进度条的高信息密度卡 ========== */
.metric-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: var(--sf-space-3);
  margin-bottom: var(--sf-space-5);
}
@media (max-width: 1500px) { .metric-row { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 720px)  { .metric-row { grid-template-columns: 1fr 1fr; } }
.m-unit { font-size: 18px; font-weight: 500; color: var(--sf-text-tertiary); margin-left: 2px; }

.metric {
  position: relative;
  background: var(--sf-surface-gradient);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-4);
  overflow: hidden;
  transition: all var(--sf-transition-base);
  cursor: default;
  min-height: 148px;
  display: flex;
  flex-direction: column;
}
.metric::after {
  content: '';
  position: absolute;
  top: -60%;
  right: -20%;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: var(--sf-gradient-soft);
  opacity: 0;
  transition: opacity var(--sf-transition-base);
  pointer-events: none;
}
.metric:hover {
  transform: translateY(-3px);
  box-shadow: var(--sf-shadow-lg);
  border-color: var(--sf-primary-light);
}
.metric:hover::after { opacity: 0.6; }
.metric > * { position: relative; z-index: 1; }

.metric-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sf-space-3);
}
.metric-badge {
  padding: 3px 10px;
  border-radius: var(--sf-radius-full);
  font-size: 10.5px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  font-variant-numeric: tabular-nums;
}
.metric-badge.is-up      { background: rgba(59,130,246,0.12); color: var(--sf-info); }
.metric-badge.is-brand   { background: rgba(102,126,234,0.12); color: var(--sf-primary-dark); }
.metric-badge.is-success { background: var(--sf-success-bg); color: var(--sf-success); }
.metric-badge.is-danger  { background: var(--sf-danger-bg); color: var(--sf-danger); }
.metric-badge.is-warning { background: var(--sf-warning-bg); color: var(--sf-warning); }
.metric-badge.is-ocean   { background: #cffafe; color: #0891b2; }

.metric-value {
  font-size: 30px;
  font-weight: 800;
  color: var(--sf-text-primary);
  line-height: 1;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
  margin-bottom: 4px;
}
.metric-label {
  color: var(--sf-text-tertiary);
  font-size: 12.5px;
  margin-bottom: auto;
  padding-bottom: var(--sf-space-2);
  line-height: 1.4;
}

/* Sparkline */
.metric-sparkline {
  width: 100%;
  height: 24px;
  margin-top: auto;
  display: block;
  overflow: visible;
}
/* 迷你进度条（评分 / 误判率等） */
.metric-progress {
  width: 100%;
  height: 6px;
  background: var(--sf-border-light);
  border-radius: var(--sf-radius-full);
  overflow: hidden;
  margin-top: auto;
}
.metric-progress-fill {
  height: 100%;
  border-radius: var(--sf-radius-full);
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}
.metric-progress-fill.is-brand { background: var(--sf-gradient); }
.metric-progress-fill.is-rose  { background: var(--sf-gradient-rose); }
/* Token 输入 / 输出子统计 */
.metric-sub-stats {
  display: flex;
  gap: 10px;
  margin-top: auto;
  font-size: 11px;
  color: var(--sf-text-tertiary);
}
.mss-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-variant-numeric: tabular-nums;
}
.mss-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

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

/* ========== 趋势面积图 ========== */
.trend-panel { position: relative; }
.trend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sf-space-3);
}
.trend-header .sf-panel-title { margin: 0; }
.trend-legend-line {
  font-size: 11px;
  color: var(--sf-warning);
  font-weight: 500;
}
.trend-wrap {
  position: relative;
  height: 140px;
}
.trend-svg {
  width: 100%;
  height: 100%;
  display: block;
  overflow: visible;
}
.trend-tooltip {
  position: absolute;
  background: var(--sf-text-primary);
  color: #fff;
  padding: 8px 12px;
  border-radius: var(--sf-radius-sm);
  font-size: 11.5px;
  transform: translate(-50%, -100%);
  pointer-events: none;
  box-shadow: var(--sf-shadow-lg);
  white-space: nowrap;
}
.trend-tooltip::after {
  content: '';
  position: absolute;
  bottom: -4px;
  left: 50%;
  transform: translateX(-50%) rotate(45deg);
  width: 8px;
  height: 8px;
  background: var(--sf-text-primary);
}
.trend-tooltip .tt-date {
  color: rgba(255,255,255,0.7);
  font-size: 10.5px;
  margin-bottom: 2px;
}
.trend-tooltip .tt-count {
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.trend-tooltip .tt-count span {
  font-size: 10px;
  font-weight: 400;
  opacity: 0.8;
  margin-left: 2px;
}
.trend-axis {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--sf-text-muted);
  font-size: 11px;
  margin-top: var(--sf-space-2);
  font-variant-numeric: tabular-nums;
}
.axis-mean {
  color: var(--sf-warning);
  font-weight: 600;
}

/* ========== 饼图 ========== */
.pie-wrapper {
  display: flex;
  align-items: center;
  gap: var(--sf-space-5);
}
.pie-canvas {
  position: relative;
  width: 170px;
  height: 170px;
  flex-shrink: 0;
}
.pie-glow {
  position: absolute;
  inset: -10px;
  border-radius: 50%;
  background: conic-gradient(
    from 0deg,
    rgba(239,68,68,0.12),
    rgba(245,158,11,0.12),
    rgba(59,130,246,0.12),
    rgba(239,68,68,0.12)
  );
  filter: blur(16px);
  pointer-events: none;
  animation: spin 20s linear infinite;
}
.pie-svg {
  position: relative;
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 4px 10px rgba(0,0,0,0.08));
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
  position: relative;
  display: grid;
  grid-template-columns: 6px 92px 1fr 110px 110px 48px;
  gap: var(--sf-space-3);
  padding: var(--sf-space-3) var(--sf-space-3);
  border-radius: var(--sf-radius-md);
  cursor: pointer;
  transition: all var(--sf-transition-fast);
  align-items: center;
  font-size: 13px;
  border: 1px solid var(--sf-border-light);
  background: var(--sf-surface);
  overflow: hidden;
}
.recent-item:hover {
  background: var(--sf-surface-hover);
  transform: translateX(2px);
  box-shadow: var(--sf-shadow-sm);
  border-color: var(--sf-primary-light);
}
/* 左侧状态色条 */
.recent-accent {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  border-radius: 4px 0 0 4px;
}
.recent-item.status-success .recent-accent { background: var(--sf-success-gradient); }
.recent-item.status-running .recent-accent { background: var(--sf-info-gradient); }
.recent-item.status-pending .recent-accent { background: linear-gradient(180deg, #9ca3af, #6b7280); }
.recent-item.status-failed  .recent-accent { background: var(--sf-danger-gradient); }
/* 占位第一列给 accent */
.recent-item > :first-child { visibility: hidden; }

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
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
  box-shadow: 0 0 6px currentColor;
}

/* 评分徽章 */
.score-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 42px;
  height: 28px;
  padding: 0 10px;
  border-radius: var(--sf-radius-full);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  color: #fff;
  box-shadow: var(--sf-shadow-sm);
}
.score-badge.score-a { background: var(--sf-success-gradient); box-shadow: 0 2px 8px rgba(16,185,129,0.25); }
.score-badge.score-b { background: var(--sf-info-gradient);    box-shadow: 0 2px 8px rgba(59,130,246,0.25); }
.score-badge.score-c { background: var(--sf-warning-gradient); box-shadow: 0 2px 8px rgba(245,158,11,0.25); }
.score-badge.score-d { background: var(--sf-danger-gradient);  box-shadow: 0 2px 8px rgba(239,68,68,0.25); }
.score-badge.score-empty {
  background: var(--sf-bg-subtle);
  color: var(--sf-text-muted);
  box-shadow: none;
  border: 1px solid var(--sf-border-light);
}

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
