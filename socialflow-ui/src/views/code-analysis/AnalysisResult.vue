<!--
  AnalysisResult.vue —— 任意分析记录的详情页（History / Dashboard 跳转到此）
  根据 analysisType 渲染不同布局
-->
<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import { useMermaid } from '@/composables/useMermaid'
import type { CodeAnalysis, FindingLevel, LlmCallLog } from '@/types/codeAnalysis'
import ScoreGauge from '@/components/code-analysis/ScoreGauge.vue'
import FindingCard from '@/components/code-analysis/FindingCard.vue'

const route = useRoute()
const router = useRouter()
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const current = ref<CodeAnalysis>()
const loading = ref(true)
const filterLevel = ref<FindingLevel | 'ALL'>('ALL')
let pollerTimer: number | null = null
// 指数退避：初始 2s，每轮 ×1.25，上限 10s —— 分析耗时长时降低后端压力
const POLL_BASE_MS = 2000
const POLL_MAX_MS = 10000
let pollDelayMs = POLL_BASE_MS
const exporting = ref(false)

// LLM 调用链路
const llmCalls = ref<LlmCallLog[]>([])
const llmCallsExpanded = ref(false)
const llmCallsLoaded = ref(false)

async function toggleLlmCalls() {
  if (llmCallsExpanded.value) {
    llmCallsExpanded.value = false
    return
  }
  llmCallsExpanded.value = true
  if (!llmCallsLoaded.value && current.value) {
    try {
      llmCalls.value = await codeAnalysisApi.llmCalls(current.value.id)
      llmCallsLoaded.value = true
    } catch (e: any) {
      ElMessage.error('加载 LLM 调用链路失败：' + (e?.message || ''))
    }
  }
}

const llmTotal = computed(() => {
  const sum = llmCalls.value.reduce((acc, c) => acc + (c.totalTokens || 0), 0)
  const prompt = llmCalls.value.reduce((acc, c) => acc + (c.promptTokens || 0), 0)
  const completion = llmCalls.value.reduce((acc, c) => acc + (c.completionTokens || 0), 0)
  return { sum, prompt, completion, count: llmCalls.value.length }
})

function fmtTokens(n?: number): string {
  if (n == null) return '-'
  if (n < 1000) return String(n)
  if (n < 1000000) return (n / 1000).toFixed(1) + 'K'
  return (n / 1000000).toFixed(2) + 'M'
}

function fmtLatency(ms?: number): string {
  if (ms == null) return '-'
  if (ms < 1000) return ms + ' ms'
  return (ms / 1000).toFixed(1) + ' s'
}

async function load() {
  // route.params.id 已经是 string，切勿 Number() 转换（雪花 ID 会丢精度）
  const id = String(route.params.id)
  try {
    current.value = await codeAnalysisApi.get(id)
    if (current.value.status === 'RUNNING' || current.value.status === 'PENDING') {
      startPoll(id)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
    router.push('/code-analysis/history')
  } finally {
    loading.value = false
  }
}

function startPoll(id: string) {
  stopPoll()
  pollDelayMs = POLL_BASE_MS
  const tick = async () => {
    try {
      const r = await codeAnalysisApi.get(id)
      current.value = r
      if (r.status === 'SUCCESS' || r.status === 'FAILED') {
        stopPoll()
        return
      }
      // 每轮增加间隔，缓慢逼近上限
      pollDelayMs = Math.min(Math.ceil(pollDelayMs * 1.25), POLL_MAX_MS)
      pollerTimer = window.setTimeout(tick, pollDelayMs)
    } catch {
      stopPoll()
    }
  }
  pollerTimer = window.setTimeout(tick, pollDelayMs)
}
function stopPoll() { if (pollerTimer != null) { window.clearTimeout(pollerTimer); pollerTimer = null } }

onMounted(load)
onUnmounted(stopPoll)

const summaryHtml = computed(() => current.value?.summaryMd ? md.render(current.value.summaryMd) : '')

// Mermaid
const { svg: mermaidSvg, error: mermaidError, render: renderMermaid } = useMermaid()
watch(() => current.value?.mermaidCode, async (code) => {
  await renderMermaid(code)
}, { immediate: true })
const filteredFindings = computed(() => {
  if (!current.value?.findings) return []
  return filterLevel.value === 'ALL'
    ? current.value.findings
    : current.value.findings.filter(f => f.level === filterLevel.value)
})

async function share() {
  if (!current.value) return
  const r = await codeAnalysisApi.share(current.value.id)
  const url = `${location.origin}/code-analysis/shared/${r.shareToken}`
  await navigator.clipboard.writeText(url)
  ElMessage.success('分享链接已复制')
}

async function doExport(format: 'markdown' | 'html' | 'pdf') {
  if (!current.value) return
  exporting.value = true
  try {
    if (format === 'pdf') {
      // 调用浏览器原生打印 —— print.css 会自动隐藏侧栏/按钮/LLM 面板；
      // 用户在打印对话框里选"另存为 PDF"即可，视觉 100% 保真
      window.print()
    } else {
      await codeAnalysisApi.exportFile(current.value.id, format)
    }
  } catch (e: any) {
    ElMessage.error('导出失败：' + (e?.message || ''))
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="result-page" v-loading="loading">
    <div v-if="current" class="header-bar">
      <el-button class="back-btn" @click="router.back()"><el-icon><ArrowLeft /></el-icon>返回</el-button>
      <div class="header-info">
        <span class="type-chip">
          <el-icon v-if="current.analysisType === 'PROJECT_OVERVIEW'"><Reading /></el-icon>
          <el-icon v-else-if="current.analysisType === 'COMMIT_REVIEW'"><Search /></el-icon>
          <el-icon v-else><Connection /></el-icon>
          {{ current.analysisType === 'PROJECT_OVERVIEW' ? '项目概览' : current.analysisType === 'COMMIT_REVIEW' ? '提交审查' : '对比分析' }}
        </span>
        <span class="repo-url">{{ current.gitUrl }}</span>
      </div>
      <div class="header-actions">
        <el-button size="small" @click="share"><el-icon><Share /></el-icon>分享</el-button>
        <el-dropdown trigger="click" @command="doExport">
          <el-button size="small" :loading="exporting"><el-icon><Download /></el-icon>导出<el-icon style="margin-left:4px"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="markdown">Markdown (含 findings)</el-dropdown-item>
              <el-dropdown-item command="html">HTML (可离线浏览)</el-dropdown-item>
              <el-dropdown-item command="pdf">PDF (可打印归档)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div v-if="current?.status === 'RUNNING' || current?.status === 'PENDING'" class="progress-card">
      <el-icon class="spin"><Loading /></el-icon>
      <div class="progress-text">{{ current.progressMessage || '分析中...' }}</div>
      <el-progress :percentage="current.progressPercent || 0" style="width: 100%; margin-top: 10px"
                   :stroke-width="10"
                   :color="[{color: '#667eea', percentage: 50}, {color: '#764ba2', percentage: 100}]" />
    </div>

    <div v-else-if="current?.status === 'FAILED'" class="error-card">
      <div class="error-icon-wrap"><el-icon><CircleCloseFilled /></el-icon></div>
      <div class="error-title">分析失败</div>
      <div class="error-detail">{{ current.errorMsg }}</div>
    </div>

    <template v-else-if="current">
      <!-- 审查类：评分 Hero -->
      <div v-if="current.analysisType !== 'PROJECT_OVERVIEW'" class="score-hero">
        <div class="score-hero-left">
          <ScoreGauge :score="current.overallScore" :size="180" />
        </div>
        <div class="score-hero-right">
          <div class="count-row">
            <div class="count-card is-high" @click="filterLevel = 'HIGH'" :class="{ active: filterLevel === 'HIGH' }">
              <div class="count-icon"><el-icon><Warning /></el-icon></div>
              <div class="count-num">{{ current.highCount }}</div>
              <div class="count-label">高风险</div>
            </div>
            <div class="count-card is-medium" @click="filterLevel = 'MEDIUM'" :class="{ active: filterLevel === 'MEDIUM' }">
              <div class="count-icon"><el-icon><WarningFilled /></el-icon></div>
              <div class="count-num">{{ current.mediumCount }}</div>
              <div class="count-label">中风险</div>
            </div>
            <div class="count-card is-low" @click="filterLevel = 'LOW'" :class="{ active: filterLevel === 'LOW' }">
              <div class="count-icon"><el-icon><InfoFilled /></el-icon></div>
              <div class="count-num">{{ current.lowCount }}</div>
              <div class="count-label">低风险</div>
            </div>
            <div class="count-card is-all" @click="filterLevel = 'ALL'" :class="{ active: filterLevel === 'ALL' }">
              <div class="count-icon"><el-icon><Document /></el-icon></div>
              <div class="count-num">{{ (current.findings || []).length }}</div>
              <div class="count-label">全部</div>
            </div>
          </div>
          <div class="meta-row">
            <span v-if="current.commitSha" class="meta-pill"><el-icon><Link /></el-icon>{{ current.commitSha.slice(0, 7) }}</span>
            <span v-if="current.baseRef" class="meta-pill">Base · {{ current.baseRef }}</span>
            <span v-if="current.headRef" class="meta-pill">Head · {{ current.headRef }}</span>
            <span v-if="current.durationMs" class="meta-pill"><el-icon><Timer /></el-icon>{{ (current.durationMs / 1000).toFixed(1) }}s</span>
          </div>
        </div>
      </div>

      <!-- 项目概览：技术栈 -->
      <div v-else class="top-card">
        <div class="overview-stats">
          <div class="stats-box">
            <div class="stats-title">🛠️ 技术栈</div>
            <div class="tech-chips">
              <span v-for="t in current.techStack" :key="t" class="tech-chip">{{ t }}</span>
            </div>
          </div>
          <div class="stats-box">
            <div class="stats-title">📊 语言占比</div>
            <div class="lang-bars">
              <div v-for="l in current.languageStats" :key="l.language" class="lang-row">
                <span class="lang-name">{{ l.language }}</span>
                <div class="lang-track"><div class="lang-fill" :style="{ width: l.percent + '%' }" /></div>
                <span class="lang-percent">{{ l.percent.toFixed(1) }}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 用户当初的分析诉求（若有） -->
      <div v-if="current.userRequirements" class="content-card req-card">
        <div class="card-title">✍️ 本次分析的自定义诉求</div>
        <div class="req-body">{{ current.userRequirements }}</div>
      </div>

      <!-- Mermaid 流程图（项目概览类型显示）-->
      <div v-if="current.analysisType === 'PROJECT_OVERVIEW' && current.mermaidCode" class="content-card">
        <div class="card-title">🧭 核心流程图</div>
        <div v-if="mermaidError" class="mermaid-error">⚠️ {{ mermaidError }}</div>
        <div v-else-if="mermaidSvg" class="mermaid-svg" v-html="mermaidSvg" />
      </div>

      <!-- 摘要 -->
      <div v-if="summaryHtml" class="content-card">
        <div class="card-title">{{ current.analysisType === 'PROJECT_OVERVIEW' ? '📝 项目详细介绍' : '📝 审查总结' }}</div>
        <div class="markdown-body" v-html="summaryHtml" />
      </div>

      <!-- Findings -->
      <div v-if="current.findings && current.findings.length > 0" class="content-card">
        <div class="findings-header">
          <div class="card-title">🔍 详细发现（{{ filteredFindings.length }} 条）</div>
          <div class="filter-tags">
            <span class="ft" :class="{ on: filterLevel === 'ALL' }" @click="filterLevel = 'ALL'">全部</span>
            <span class="ft ft-h" :class="{ on: filterLevel === 'HIGH' }" @click="filterLevel = 'HIGH'">高</span>
            <span class="ft ft-m" :class="{ on: filterLevel === 'MEDIUM' }" @click="filterLevel = 'MEDIUM'">中</span>
            <span class="ft ft-l" :class="{ on: filterLevel === 'LOW' }" @click="filterLevel = 'LOW'">低</span>
          </div>
        </div>
        <FindingCard v-for="f in filteredFindings" :key="f.id" :finding="f"
                     @updated="(u) => { const i = current!.findings!.findIndex(x => x.id === u.id); if (i >= 0) current!.findings![i] = u }" />
      </div>

      <!-- LLM 调用详情 -->
      <div class="content-card">
        <div class="llm-header" @click="toggleLlmCalls">
          <div class="card-title">🧮 LLM 调用详情
            <span v-if="current.llmTokensUsed != null" class="llm-total-inline">
              · 本次共 {{ fmtTokens(current.llmTokensUsed) }} tokens
            </span>
          </div>
          <el-icon class="llm-arrow" :class="{ expanded: llmCallsExpanded }"><ArrowDown /></el-icon>
        </div>

        <div v-if="llmCallsExpanded" class="llm-body">
          <div v-if="!llmCallsLoaded" class="llm-loading">加载中...</div>
          <template v-else>
            <div class="llm-summary-row">
              <div class="llm-sum-card"><span>调用次数</span><strong>{{ llmTotal.count }}</strong></div>
              <div class="llm-sum-card"><span>输入 Tokens</span><strong>{{ fmtTokens(llmTotal.prompt) }}</strong></div>
              <div class="llm-sum-card"><span>输出 Tokens</span><strong>{{ fmtTokens(llmTotal.completion) }}</strong></div>
              <div class="llm-sum-card"><span>合计 Tokens</span><strong>{{ fmtTokens(llmTotal.sum) }}</strong></div>
            </div>

            <table class="llm-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>阶段</th>
                  <th>模型</th>
                  <th>状态</th>
                  <th>输入</th>
                  <th>输出</th>
                  <th>合计</th>
                  <th>耗时</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(l, idx) in llmCalls" :key="l.id" :class="{ 'row-fail': !l.success }">
                  <td class="c-idx">{{ idx + 1 }}</td>
                  <td class="c-stage">
                    <div class="stage-label">{{ l.stageLabel || l.stage }}</div>
                    <div class="stage-code">{{ l.stage }}</div>
                  </td>
                  <td class="c-model">{{ l.provider }} / {{ l.model }}</td>
                  <td>
                    <span v-if="l.success" class="tag tag-ok">✓</span>
                    <span v-else class="tag tag-fail" :title="l.errorMsg">✗</span>
                  </td>
                  <td class="c-num">{{ fmtTokens(l.promptTokens) }}</td>
                  <td class="c-num">{{ fmtTokens(l.completionTokens) }}</td>
                  <td class="c-num c-total">{{ fmtTokens(l.totalTokens) }}</td>
                  <td class="c-num">{{ fmtLatency(l.latencyMs) }}</td>
                </tr>
              </tbody>
            </table>
          </template>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.result-page {
  padding: var(--sf-space-5);
  max-width: 1100px;
  margin: 0 auto;
  animation: sf-fade-in var(--sf-transition-base);
}

/* ========== 顶部操作栏 ========== */
.header-bar {
  display: flex;
  align-items: center;
  gap: var(--sf-space-3);
  margin-bottom: var(--sf-space-4);
}
.back-btn {
  background: var(--sf-surface) !important;
  border: 1px solid var(--sf-border) !important;
  transition: all var(--sf-transition-fast) !important;
}
.back-btn:hover {
  border-color: var(--sf-primary) !important;
  color: var(--sf-primary) !important;
  transform: translateX(-2px);
}
.header-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--sf-space-3);
  min-width: 0;
}
.type-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  background: var(--sf-gradient-soft);
  color: var(--sf-primary-dark);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: var(--sf-radius-full);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.repo-url {
  color: var(--sf-text-tertiary);
  font-size: 13px;
  font-family: 'SF Mono', Menlo, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.header-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

/* ========== 进度/错误卡 ========== */
.progress-card {
  text-align: center;
  padding: var(--sf-space-7);
  background: var(--sf-surface-gradient);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  box-shadow: var(--sf-shadow-sm);
}
.progress-text {
  display: inline-block;
  color: var(--sf-text-secondary);
  font-weight: 500;
  margin-bottom: var(--sf-space-2);
}
.spin {
  animation: spin 1s linear infinite;
  color: var(--sf-primary);
  margin-right: var(--sf-space-2);
  font-size: 20px;
  vertical-align: middle;
}
@keyframes spin { to { transform: rotate(360deg); } }

.error-card {
  background: linear-gradient(135deg, #fff1f2 0%, #ffe4e6 100%);
  border: 1px solid #fca5a5;
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-6);
  box-shadow: var(--sf-shadow-glow-danger);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sf-space-3);
}
.error-icon-wrap {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--sf-danger-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  box-shadow: var(--sf-shadow-glow-danger);
}
.error-title {
  color: #b91c1c;
  font-size: 18px;
  font-weight: 700;
}
.error-detail {
  color: #991b1b;
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 13px;
  background: rgba(255,255,255,0.6);
  padding: var(--sf-space-3);
  border-radius: var(--sf-radius-sm);
  max-width: 600px;
  white-space: pre-wrap;
}

/* ========== 评分 Hero ========== */
.score-hero {
  background: var(--sf-surface-gradient);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-5) var(--sf-space-6);
  display: flex;
  gap: var(--sf-space-6);
  align-items: center;
  margin-bottom: var(--sf-space-4);
  box-shadow: var(--sf-shadow-sm);
  position: relative;
  overflow: hidden;
}
.score-hero::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 320px;
  height: 320px;
  background: var(--sf-gradient-soft);
  border-radius: 50%;
  pointer-events: none;
}
.score-hero-left {
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.score-hero-right {
  flex: 1;
  position: relative;
  z-index: 1;
}

.count-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sf-space-3);
  margin-bottom: var(--sf-space-4);
}
.count-card {
  position: relative;
  padding: var(--sf-space-4);
  background: var(--sf-surface);
  border: 2px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  text-align: center;
  cursor: pointer;
  transition: all var(--sf-transition-fast);
  overflow: hidden;
}
.count-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--sf-shadow-md);
}
.count-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  opacity: 0.8;
}
.count-card.is-high::before   { background: var(--sf-danger-gradient); }
.count-card.is-medium::before { background: var(--sf-warning-gradient); }
.count-card.is-low::before    { background: var(--sf-info-gradient); }
.count-card.is-all::before    { background: var(--sf-gradient); }
.count-card.is-high.active    { border-color: var(--sf-danger); background: var(--sf-danger-bg); }
.count-card.is-medium.active  { border-color: var(--sf-warning); background: var(--sf-warning-bg); }
.count-card.is-low.active     { border-color: var(--sf-info); background: var(--sf-info-bg); }
.count-card.is-all.active     { border-color: var(--sf-primary); background: var(--sf-gradient-soft); }

.count-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--sf-radius-sm);
  margin: 0 auto var(--sf-space-2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
}
.count-card.is-high .count-icon   { background: var(--sf-danger-gradient); }
.count-card.is-medium .count-icon { background: var(--sf-warning-gradient); }
.count-card.is-low .count-icon    { background: var(--sf-info-gradient); }
.count-card.is-all .count-icon    { background: var(--sf-gradient); }

.count-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  color: var(--sf-text-primary);
}
.count-label {
  font-size: 12px;
  color: var(--sf-text-tertiary);
  margin-top: 4px;
}

.meta-row {
  display: flex;
  gap: var(--sf-space-2);
  flex-wrap: wrap;
}
.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: var(--sf-bg-subtle);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-full);
  color: var(--sf-text-tertiary);
  font-size: 12px;
  font-family: 'SF Mono', Menlo, monospace;
}
.meta-pill :deep(.el-icon) { font-size: 12px; }

/* ========== 项目概览：技术栈 / 语言条（复用 ProjectOverview 的风格） ========== */
.top-card {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-5);
  display: flex;
  gap: var(--sf-space-5);
  align-items: center;
  margin-bottom: var(--sf-space-4);
  box-shadow: var(--sf-shadow-sm);
}
.overview-stats {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sf-space-4);
}
.stats-box {
  background: var(--sf-surface-gradient-soft);
  border: 1px solid var(--sf-border-light);
  padding: var(--sf-space-4);
  border-radius: var(--sf-radius-md);
}
.stats-title {
  font-weight: 600;
  color: var(--sf-text-secondary);
  margin-bottom: var(--sf-space-3);
  font-size: 13px;
}
.tech-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tech-chip {
  padding: 4px 12px;
  background: rgba(102, 126, 234, 0.1);
  color: var(--sf-primary-dark);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: var(--sf-radius-full);
  font-size: 12px;
  font-weight: 500;
}
.lang-row {
  display: grid;
  grid-template-columns: 80px 1fr 50px;
  gap: var(--sf-space-2);
  font-size: 12px;
  align-items: center;
  margin-bottom: 6px;
}
.lang-track {
  background: var(--sf-border-light);
  height: 8px;
  border-radius: var(--sf-radius-full);
  overflow: hidden;
}
.lang-fill {
  height: 100%;
  background: var(--sf-gradient);
  border-radius: var(--sf-radius-full);
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}
.lang-percent {
  color: var(--sf-text-tertiary);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* ========== 内容卡 ========== */
.content-card {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-5);
  margin-bottom: var(--sf-space-4);
  box-shadow: var(--sf-shadow-sm);
  transition: box-shadow var(--sf-transition-base);
}
.content-card:hover { box-shadow: var(--sf-shadow-md); }

/* 用户诉求卡：浅紫背景 + 左侧竖条 */
.req-card {
  background: linear-gradient(135deg, rgba(102,126,234,0.05) 0%, rgba(118,75,162,0.05) 100%);
  border-color: rgba(102,126,234,0.2);
}
.req-body {
  color: var(--sf-text-primary);
  font-size: 13.5px;
  line-height: 1.8;
  white-space: pre-wrap;
  padding: var(--sf-space-3) var(--sf-space-4);
  background: rgba(255,255,255,0.6);
  border-left: 3px solid var(--sf-primary);
  border-radius: 0 var(--sf-radius-sm) var(--sf-radius-sm) 0;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: var(--sf-space-3);
  color: var(--sf-text-primary);
  display: flex;
  align-items: center;
  gap: var(--sf-space-2);
}
.card-title::before {
  content: '';
  width: 4px;
  height: 16px;
  background: var(--sf-gradient);
  border-radius: var(--sf-radius-full);
}

.findings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sf-space-4);
}
.filter-tags {
  display: flex;
  gap: 6px;
}
.ft {
  padding: 5px 14px;
  border-radius: var(--sf-radius-full);
  font-size: 12px;
  cursor: pointer;
  background: var(--sf-bg-subtle);
  color: var(--sf-text-tertiary);
  border: 1px solid var(--sf-border-light);
  font-weight: 500;
  transition: all var(--sf-transition-fast);
}
.ft:hover {
  background: var(--sf-surface-hover);
  color: var(--sf-text-secondary);
}
.ft.on {
  background: var(--sf-gradient);
  color: #fff;
  border-color: transparent;
  box-shadow: var(--sf-shadow-brand);
}
.ft-h.on { background: var(--sf-danger-gradient); box-shadow: var(--sf-shadow-glow-danger); }
.ft-m.on { background: var(--sf-warning-gradient); box-shadow: var(--sf-shadow-glow-warning); }
.ft-l.on { background: var(--sf-info-gradient); }

/* ========== Markdown（与 ProjectOverview 一致） ========== */
.markdown-body {
  line-height: 1.8;
  color: var(--sf-text-primary);
}
.markdown-body :deep(h2) {
  font-size: 17px;
  margin: var(--sf-space-4) 0 var(--sf-space-2);
  font-weight: 600;
  color: var(--sf-text-primary);
  padding-bottom: 4px;
  border-bottom: 2px solid var(--sf-border-light);
}
.markdown-body :deep(p) { margin: var(--sf-space-2) 0; }
.markdown-body :deep(code) {
  background: rgba(102, 126, 234, 0.08);
  padding: 2px 8px;
  border-radius: var(--sf-radius-xs);
  font-size: 0.9em;
  color: var(--sf-primary-dark);
  border: 1px solid rgba(102, 126, 234, 0.12);
}
.markdown-body :deep(pre) {
  background: #1e293b;
  color: #e2e8f0;
  padding: var(--sf-space-4);
  border-radius: var(--sf-radius-md);
  overflow-x: auto;
  box-shadow: var(--sf-shadow-md);
}

/* ========== Mermaid ========== */
.mermaid-svg {
  display: flex;
  justify-content: center;
  overflow-x: auto;
}
.mermaid-svg :deep(svg) {
  max-width: 100%;
  height: auto;
  filter: drop-shadow(0 4px 12px rgba(0,0,0,0.04));
}
.mermaid-error {
  background: var(--sf-danger-bg);
  color: var(--sf-danger);
  padding: var(--sf-space-3);
  border-radius: var(--sf-radius-sm);
  font-size: 13px;
}

/* ========== LLM 调用详情 ========== */
.llm-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  user-select: none;
  padding: var(--sf-space-2) 0;
}
.llm-total-inline {
  color: var(--sf-text-tertiary);
  font-weight: 400;
  font-size: 13px;
  margin-left: var(--sf-space-2);
}
.llm-arrow {
  color: var(--sf-text-muted);
  transition: transform var(--sf-transition-fast);
  font-size: 16px;
}
.llm-arrow.expanded { transform: rotate(180deg); }
.llm-body { margin-top: var(--sf-space-4); }
.llm-loading {
  text-align: center;
  padding: var(--sf-space-5);
  color: var(--sf-text-muted);
}

.llm-summary-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sf-space-3);
  margin-bottom: var(--sf-space-4);
}
.llm-sum-card {
  background: var(--sf-surface-gradient-soft);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-3) var(--sf-space-4);
  transition: all var(--sf-transition-fast);
}
.llm-sum-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--sf-shadow-sm);
  border-color: var(--sf-primary-light);
}
.llm-sum-card span {
  display: block;
  font-size: 12px;
  color: var(--sf-text-tertiary);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.llm-sum-card strong {
  font-size: 20px;
  color: var(--sf-text-primary);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.llm-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  overflow: hidden;
}
.llm-table thead th {
  text-align: left;
  padding: var(--sf-space-3) var(--sf-space-3);
  background: var(--sf-bg-subtle);
  color: var(--sf-text-secondary);
  font-weight: 600;
  border-bottom: 1px solid var(--sf-border-light);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-size: 11px;
}
.llm-table tbody td {
  padding: var(--sf-space-3) var(--sf-space-3);
  border-bottom: 1px solid var(--sf-border-light);
  vertical-align: top;
}
.llm-table tbody tr:last-child td { border-bottom: none; }
.llm-table tbody tr { transition: background var(--sf-transition-fast); }
.llm-table tbody tr:hover { background: var(--sf-surface-hover); }
.llm-table .c-idx {
  color: var(--sf-text-muted);
  font-family: 'SF Mono', Menlo, monospace;
  width: 40px;
}
.llm-table .c-stage { min-width: 200px; }
.llm-table .stage-label {
  color: var(--sf-text-primary);
  font-weight: 500;
}
.llm-table .stage-code {
  color: var(--sf-text-muted);
  font-size: 11px;
  font-family: 'SF Mono', Menlo, monospace;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
  white-space: nowrap;
}
.llm-table .c-model {
  color: var(--sf-text-tertiary);
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 11px;
}
.llm-table .c-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--sf-text-secondary);
}
.llm-table .c-total {
  font-weight: 700;
  color: var(--sf-primary-dark);
}
.llm-table .row-fail { background: rgba(239, 68, 68, 0.04); }

.tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
}
.tag-ok {
  background: var(--sf-success-gradient);
  color: #fff;
  box-shadow: 0 2px 6px rgba(16, 185, 129, 0.25);
}
.tag-fail {
  background: var(--sf-danger-gradient);
  color: #fff;
  box-shadow: 0 2px 6px rgba(239, 68, 68, 0.25);
  cursor: help;
}
</style>
