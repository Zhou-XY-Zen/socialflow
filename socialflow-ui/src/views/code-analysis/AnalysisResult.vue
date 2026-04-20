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
    await codeAnalysisApi.exportFile(current.value.id, format)
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
      <el-button @click="router.back()">← 返回</el-button>
      <div class="header-info">
        <span class="type-chip">{{ current.analysisType === 'PROJECT_OVERVIEW' ? '📖 项目概览' : current.analysisType === 'COMMIT_REVIEW' ? '🔎 提交审查' : '🔀 对比分析' }}</span>
        <span class="repo-url">{{ current.gitUrl }}</span>
      </div>
      <div class="header-actions">
        <el-button size="small" @click="share">🔗 分享</el-button>
        <el-dropdown trigger="click" @command="doExport">
          <el-button size="small" :loading="exporting">📥 导出<el-icon style="margin-left:4px"><ArrowDown /></el-icon></el-button>
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
      {{ current.progressMessage || '分析中...' }}
      <el-progress :percentage="current.progressPercent || 0" style="width: 100%; margin-top: 10px" />
    </div>

    <div v-else-if="current?.status === 'FAILED'" class="error-card">
      ❌ {{ current.errorMsg }}
    </div>

    <template v-else-if="current">
      <!-- 审查类：评分 + 计数 -->
      <div v-if="current.analysisType !== 'PROJECT_OVERVIEW'" class="top-card">
        <ScoreGauge :score="current.overallScore" :size="180" />
        <div class="count-col">
          <div class="count-row">
            <div class="c-card c-h" @click="filterLevel = 'HIGH'">高 {{ current.highCount }}</div>
            <div class="c-card c-m" @click="filterLevel = 'MEDIUM'">中 {{ current.mediumCount }}</div>
            <div class="c-card c-l" @click="filterLevel = 'LOW'">低 {{ current.lowCount }}</div>
            <div class="c-card" @click="filterLevel = 'ALL'">全部 {{ (current.findings || []).length }}</div>
          </div>
          <div class="meta-row">
            <span v-if="current.commitSha">🔢 {{ current.commitSha.slice(0, 7) }}</span>
            <span v-if="current.baseRef">Base: {{ current.baseRef }}</span>
            <span v-if="current.headRef">Head: {{ current.headRef }}</span>
            <span v-if="current.durationMs">⏱️ {{ (current.durationMs / 1000).toFixed(1) }}s</span>
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
.result-page { padding: 20px; max-width: 1100px; margin: 0 auto; }
.header-bar { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.header-info { flex: 1; display: flex; align-items: center; gap: 10px; }
.type-chip { padding: 3px 10px; background: #ede9fe; color: #6d28d9; border-radius: 4px; font-size: 12px; }
.repo-url { color: #6b7280; font-size: 13px; }
.header-actions { display: flex; gap: 6px; }

.progress-card { text-align: center; padding: 40px; background: #fff; border-radius: 12px; color: #6b7280; }
.spin { animation: spin 1s linear infinite; color: #3b82f6; margin-right: 8px; }
@keyframes spin { to { transform: rotate(360deg); } }
.error-card { background: #fef2f2; color: #b91c1c; padding: 20px; border-radius: 12px; }

.top-card { background: #fff; border-radius: 12px; padding: 20px; display: flex; gap: 20px; align-items: center; margin-bottom: 14px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); }
.count-col { flex: 1; }
.count-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 10px; }
.c-card { padding: 12px; background: #f9fafb; border-radius: 8px; text-align: center; cursor: pointer; font-weight: 600; border: 2px solid transparent; }
.c-card:hover { border-color: #d1d5db; }
.c-h { color: #ef4444; } .c-m { color: #f59e0b; } .c-l { color: #3b82f6; }
.meta-row { color: #6b7280; font-size: 12px; display: flex; gap: 14px; }

.overview-stats { width: 100%; display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.stats-box { background: #f9fafb; padding: 14px; border-radius: 8px; }
.stats-title { font-weight: 600; color: #374151; margin-bottom: 10px; }
.tech-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.tech-chip { padding: 3px 10px; background: #ede9fe; color: #6d28d9; border-radius: 12px; font-size: 12px; }
.lang-row { display: grid; grid-template-columns: 80px 1fr 50px; gap: 8px; font-size: 12px; align-items: center; margin-bottom: 4px; }
.lang-track { background: #e5e7eb; height: 6px; border-radius: 3px; overflow: hidden; }
.lang-fill { height: 100%; background: linear-gradient(90deg, #3b82f6, #8b5cf6); }
.lang-percent { color: #6b7280; text-align: right; }

.content-card { background: #fff; border-radius: 12px; padding: 20px 24px; margin-bottom: 14px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); }
.card-title { font-size: 15px; font-weight: 600; margin-bottom: 12px; color: #111827; }
.findings-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.filter-tags { display: flex; gap: 6px; }
.ft { padding: 4px 12px; border-radius: 14px; font-size: 12px; cursor: pointer; background: #f3f4f6; color: #6b7280; }
.ft:hover { background: #e5e7eb; }
.ft.on { background: #3b82f6; color: #fff; }
.ft-h.on { background: #ef4444; } .ft-m.on { background: #f59e0b; } .ft-l.on { background: #3b82f6; }
.markdown-body { line-height: 1.75; color: #1f2937; }
.markdown-body :deep(h2) { font-size: 16px; margin: 12px 0 6px; }
.markdown-body :deep(p) { margin: 6px 0; }
.markdown-body :deep(code) { background: #f3f4f6; padding: 2px 6px; border-radius: 3px; font-size: 0.9em; color: #6d28d9; }
.markdown-body :deep(pre) { background: #1e293b; color: #e2e8f0; padding: 12px; border-radius: 6px; overflow-x: auto; }

.mermaid-svg { display: flex; justify-content: center; overflow-x: auto; }
.mermaid-svg :deep(svg) { max-width: 100%; height: auto; }
.mermaid-error { background: #fef2f2; color: #b91c1c; padding: 10px; border-radius: 6px; font-size: 13px; }

/* LLM 调用详情 */
.llm-header { display: flex; justify-content: space-between; align-items: center; cursor: pointer; user-select: none; }
.llm-total-inline { color: #6b7280; font-weight: 400; font-size: 13px; margin-left: 6px; }
.llm-arrow { color: #9ca3af; transition: transform 0.2s; }
.llm-arrow.expanded { transform: rotate(180deg); }
.llm-body { margin-top: 14px; }
.llm-loading { text-align: center; padding: 20px; color: #9ca3af; }

.llm-summary-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 14px; }
.llm-sum-card { background: #f9fafb; border-radius: 8px; padding: 12px 14px; }
.llm-sum-card span { display: block; font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.llm-sum-card strong { font-size: 18px; color: #111827; font-weight: 700; }

.llm-table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.llm-table thead th { text-align: left; padding: 8px 10px; background: #f3f4f6; color: #374151; font-weight: 600; border-bottom: 1px solid #e5e7eb; }
.llm-table tbody td { padding: 8px 10px; border-bottom: 1px solid #f3f4f6; vertical-align: top; }
.llm-table tbody tr:hover { background: #f9fafb; }
.llm-table .c-idx { color: #9ca3af; font-family: monospace; width: 32px; }
.llm-table .c-stage { min-width: 200px; }
.llm-table .stage-label { color: #111827; font-weight: 500; }
.llm-table .stage-code { color: #9ca3af; font-size: 11px; font-family: 'SF Mono', Menlo, monospace; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; max-width: 240px; white-space: nowrap; }
.llm-table .c-model { color: #6b7280; font-family: 'SF Mono', Menlo, monospace; font-size: 11px; }
.llm-table .c-num { text-align: right; font-variant-numeric: tabular-nums; }
.llm-table .c-total { font-weight: 600; color: #6d28d9; }
.llm-table .row-fail { background: #fef2f2; }
.tag { display: inline-block; padding: 1px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; }
.tag-ok { background: #d1fae5; color: #059669; }
.tag-fail { background: #fee2e2; color: #dc2626; cursor: help; }
</style>
