<!--
  AnalysisResult.vue —— 任意分析记录的详情页（History / Dashboard 跳转到此）
  根据 analysisType 渲染不同布局
-->
<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { CodeAnalysis, FindingLevel } from '@/types/codeAnalysis'
import ScoreGauge from '@/components/code-analysis/ScoreGauge.vue'
import FindingCard from '@/components/code-analysis/FindingCard.vue'

const route = useRoute()
const router = useRouter()
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const current = ref<CodeAnalysis>()
const loading = ref(true)
const filterLevel = ref<FindingLevel | 'ALL'>('ALL')
let poller: number | null = null

async function load() {
  const id = Number(route.params.id)
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

function startPoll(id: number) {
  stopPoll()
  poller = window.setInterval(async () => {
    try {
      const r = await codeAnalysisApi.get(id)
      current.value = r
      if (r.status === 'SUCCESS' || r.status === 'FAILED') stopPoll()
    } catch { stopPoll() }
  }, 2500)
}
function stopPoll() { if (poller != null) { window.clearInterval(poller); poller = null } }

onMounted(load)
onUnmounted(stopPoll)

const summaryHtml = computed(() => current.value?.summaryMd ? md.render(current.value.summaryMd) : '')
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

function exportMd() {
  if (!current.value?.summaryMd) return
  const blob = new Blob([current.value.summaryMd], { type: 'text/markdown' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `analysis-${current.value.id}.md`
  a.click()
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
        <el-button size="small" @click="exportMd">📥 导出</el-button>
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
</style>
