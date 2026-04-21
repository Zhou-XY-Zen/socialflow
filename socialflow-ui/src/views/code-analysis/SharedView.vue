<!--
  SharedView.vue —— 分享链接免登录落地页
  路径：/code-analysis/shared/:token
  基于 shareToken 从后端拉取只读分析结果，不需要登录
-->
<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import { useMermaid } from '@/composables/useMermaid'
import { useSummaryMarkdown } from '@/composables/useSummaryMarkdown'
import type { CodeAnalysis, FindingLevel } from '@/types/codeAnalysis'
import ScoreGauge from '@/components/code-analysis/ScoreGauge.vue'
import FindingCard from '@/components/code-analysis/FindingCard.vue'

const route = useRoute()

const current = ref<CodeAnalysis>()
const loading = ref(true)
const errorMsg = ref('')
const filterLevel = ref<FindingLevel | 'ALL'>('ALL')

async function load() {
  const token = String(route.params.token)
  try {
    current.value = await codeAnalysisApi.shared(token)
  } catch (e: any) {
    errorMsg.value = e?.message || '分享链接无效或已失效'
    ElMessage.error(errorMsg.value)
  } finally {
    loading.value = false
  }
}

onMounted(load)

const { summaryHtml, containerRef: summaryContainer } = useSummaryMarkdown(() => current.value?.summaryMd)
const filteredFindings = computed(() => {
  if (!current.value?.findings) return []
  return filterLevel.value === 'ALL'
    ? current.value.findings
    : current.value.findings.filter(f => f.level === filterLevel.value)
})

const { svg: mermaidSvg, error: mermaidError, render: renderMermaid } = useMermaid()
watch(() => current.value?.mermaidCode, async (code) => { await renderMermaid(code) }, { immediate: true })

const typeMeta: Record<string, { label: string; icon: string }> = {
  PROJECT_OVERVIEW: { label: '项目概览', icon: '📖' },
  COMMIT_REVIEW:    { label: '提交审查', icon: '🔎' },
  DIFF_REVIEW:      { label: '对比分析', icon: '🔀' },
}
</script>

<template>
  <div class="shared-page" v-loading="loading">
    <!-- 顶栏 -->
    <header class="top-bar">
      <div class="brand">
        <div class="brand-logo">S</div>
        <div>
          <div class="brand-name">SocialFlow</div>
          <div class="brand-sub">代码分析 · 共享视图</div>
        </div>
      </div>
      <div class="ribbon">🔗 只读分享</div>
    </header>

    <!-- 错误状态 -->
    <div v-if="errorMsg && !loading" class="error-state">
      <div class="err-icon">🔒</div>
      <div class="err-title">无法访问此分享</div>
      <div class="err-msg">{{ errorMsg }}</div>
      <el-button type="primary" @click="$router.push('/login')">回到登录页</el-button>
    </div>

    <!-- 正常内容 -->
    <main v-else-if="current" class="content">
      <!-- 摘要信息条 -->
      <div class="info-bar">
        <span class="type-chip">{{ typeMeta[current.analysisType]?.icon }} {{ typeMeta[current.analysisType]?.label }}</span>
        <span class="repo-url">📦 {{ current.gitUrl }}</span>
        <span v-if="current.branch" class="meta-item">🔀 {{ current.branch }}</span>
        <span v-if="current.commitSha" class="meta-item">🔢 {{ current.commitSha.slice(0, 7) }}</span>
        <span v-if="current.createTime" class="meta-item">📅 {{ new Date(current.createTime).toLocaleDateString('zh-CN') }}</span>
      </div>

      <!-- 审查类：评分 + 计数 -->
      <div v-if="current.analysisType !== 'PROJECT_OVERVIEW'" class="top-card">
        <ScoreGauge :score="current.overallScore" :size="180" />
        <div class="count-col">
          <div class="count-row">
            <div class="c-card c-h" @click="filterLevel = 'HIGH'">
              <div class="c-num">{{ current.highCount }}</div>
              <div class="c-label">🔴 高风险</div>
            </div>
            <div class="c-card c-m" @click="filterLevel = 'MEDIUM'">
              <div class="c-num">{{ current.mediumCount }}</div>
              <div class="c-label">🟡 中风险</div>
            </div>
            <div class="c-card c-l" @click="filterLevel = 'LOW'">
              <div class="c-num">{{ current.lowCount }}</div>
              <div class="c-label">🔵 低风险</div>
            </div>
            <div class="c-card" @click="filterLevel = 'ALL'">
              <div class="c-num">{{ (current.findings || []).length }}</div>
              <div class="c-label">📋 全部</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 项目概览：技术栈 -->
      <div v-else class="overview-top">
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

      <!-- Mermaid 图 -->
      <div v-if="current.mermaidCode" class="content-card">
        <div class="card-title">🧭 核心流程图</div>
        <div v-if="mermaidError" class="mermaid-error">{{ mermaidError }}</div>
        <div v-else-if="mermaidSvg" class="mermaid-svg" v-html="mermaidSvg" />
      </div>

      <!-- 摘要 -->
      <div v-if="summaryHtml" class="content-card">
        <div class="card-title">{{ current.analysisType === 'PROJECT_OVERVIEW' ? '📝 项目介绍' : '📝 审查总结' }}</div>
        <div ref="summaryContainer" class="markdown-body" v-html="summaryHtml" />
      </div>

      <!-- Findings（分享视图下不可修改状态，只展示） -->
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
        <FindingCard v-for="f in filteredFindings" :key="f.id" :finding="f" />
      </div>
    </main>

    <footer class="footer">
      由 <strong>SocialFlow</strong> 代码分析模块生成 · 基于《阿里巴巴 Java 开发手册（黄山版）》
    </footer>
  </div>
</template>

<style scoped>
.shared-page { min-height: 100vh; background: #f9fafb; display: flex; flex-direction: column; }

.top-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 32px; background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.brand { display: flex; align-items: center; gap: 12px; }
.brand-logo {
  width: 40px; height: 40px; background: rgba(255, 255, 255, 0.2);
  border-radius: 10px; display: flex; align-items: center; justify-content: center;
  font-size: 20px; font-weight: 700; backdrop-filter: blur(10px);
}
.brand-name { font-size: 16px; font-weight: 700; }
.brand-sub { font-size: 12px; opacity: 0.85; margin-top: 2px; }
.ribbon { background: rgba(255, 255, 255, 0.2); padding: 6px 14px; border-radius: 14px; font-size: 12px; backdrop-filter: blur(10px); }

.error-state { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; padding: 40px; }
.err-icon { font-size: 72px; opacity: 0.4; }
.err-title { font-size: 20px; font-weight: 600; color: #1f2937; }
.err-msg { color: #6b7280; font-size: 14px; }

.content { flex: 1; max-width: 1100px; width: 100%; margin: 0 auto; padding: 24px; display: flex; flex-direction: column; gap: 14px; }

.info-bar {
  background: #fff; border-radius: 10px; padding: 14px 18px;
  display: flex; flex-wrap: wrap; gap: 14px; align-items: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); font-size: 13px;
}
.type-chip { padding: 3px 10px; background: #ede9fe; color: #6d28d9; border-radius: 4px; font-weight: 500; }
.repo-url { color: #111827; font-weight: 500; }
.meta-item { color: #6b7280; }

.top-card {
  background: #fff; border-radius: 10px; padding: 20px;
  display: grid; grid-template-columns: 200px 1fr; gap: 20px; align-items: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.count-col { display: flex; flex-direction: column; gap: 10px; }
.count-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.c-card { background: #f9fafb; padding: 14px; border-radius: 8px; text-align: center; cursor: pointer; border: 2px solid transparent; transition: all 0.2s; }
.c-card:hover { transform: translateY(-2px); border-color: #d1d5db; }
.c-card.c-h:hover { border-color: #ef4444; }
.c-card.c-m:hover { border-color: #f59e0b; }
.c-card.c-l:hover { border-color: #3b82f6; }
.c-num { font-size: 24px; font-weight: 700; color: #111827; }
.c-label { color: #6b7280; font-size: 12px; margin-top: 2px; }

.overview-top { background: #fff; border-radius: 10px; padding: 20px; display: grid; grid-template-columns: 1fr 1fr; gap: 16px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); }
.stats-box { background: #f9fafb; padding: 14px; border-radius: 8px; }
.stats-title { font-weight: 600; color: #374151; margin-bottom: 10px; }
.tech-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.tech-chip { padding: 3px 10px; background: #ede9fe; color: #6d28d9; border-radius: 12px; font-size: 12px; }
.lang-row { display: grid; grid-template-columns: 80px 1fr 50px; gap: 8px; font-size: 12px; align-items: center; margin-bottom: 4px; }
.lang-track { background: #e5e7eb; height: 6px; border-radius: 3px; overflow: hidden; }
.lang-fill { height: 100%; background: linear-gradient(90deg, #3b82f6, #8b5cf6); }
.lang-percent { color: #6b7280; text-align: right; }

.content-card { background: #fff; border-radius: 10px; padding: 20px 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); }
.card-title { font-size: 15px; font-weight: 600; margin-bottom: 12px; color: #111827; }
.findings-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.filter-tags { display: flex; gap: 6px; }
.ft { padding: 4px 12px; border-radius: 14px; font-size: 12px; cursor: pointer; background: #f3f4f6; color: #6b7280; }
.ft:hover { background: #e5e7eb; }
.ft.on { background: #3b82f6; color: #fff; }
.ft-h.on { background: #ef4444; }
.ft-m.on { background: #f59e0b; }
.ft-l.on { background: #3b82f6; }
.markdown-body { line-height: 1.75; color: #1f2937; }
.markdown-body :deep(h2) { font-size: 16px; margin: 12px 0 6px; }
.markdown-body :deep(p) { margin: 6px 0; }
.markdown-body :deep(code) { background: #f3f4f6; padding: 2px 6px; border-radius: 3px; font-size: 0.9em; color: #6d28d9; }
.markdown-body :deep(pre) { background: #1e293b; color: #e2e8f0; padding: 12px; border-radius: 6px; overflow-x: auto; }

.mermaid-svg { display: flex; justify-content: center; overflow-x: auto; }
.mermaid-svg :deep(svg) { max-width: 100%; height: auto; }
.mermaid-error { background: #fef2f2; color: #b91c1c; padding: 10px; border-radius: 6px; font-size: 13px; }

.footer { text-align: center; padding: 20px; color: #9ca3af; font-size: 12px; }
</style>
