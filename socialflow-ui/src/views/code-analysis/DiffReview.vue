<!--
  DiffReview.vue —— 两个 ref 对比审查
-->
<script setup lang="ts">
import { onUnmounted, ref, computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { CodeAnalysis, FindingLevel } from '@/types/codeAnalysis'
import ScoreGauge from '@/components/code-analysis/ScoreGauge.vue'
import FindingCard from '@/components/code-analysis/FindingCard.vue'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const form = reactive({ gitUrl: '', branch: '', baseRef: '', headRef: '' })
const current = ref<CodeAnalysis>()
const loading = ref(false)
const filterLevel = ref<FindingLevel | 'ALL'>('ALL')
let poller: number | null = null

async function start() {
  if (!form.gitUrl || !form.baseRef || !form.headRef) {
    ElMessage.warning('请填写仓库 + baseRef + headRef'); return
  }
  loading.value = true
  try {
    const res = await codeAnalysisApi.triggerDiff(form)
    await poll(res.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '触发失败')
    loading.value = false
  }
}

async function poll(id: string) {
  stopPoll()
  const tick = async () => {
    try {
      const r = await codeAnalysisApi.get(id)
      current.value = r
      if (r.status === 'SUCCESS' || r.status === 'FAILED') { stopPoll(); loading.value = false }
    } catch { stopPoll(); loading.value = false }
  }
  await tick()
  poller = window.setInterval(tick, 2500)
}
function stopPoll() { if (poller != null) { window.clearInterval(poller); poller = null } }

const summaryHtml = computed(() => current.value?.summaryMd ? md.render(current.value.summaryMd) : '')
const filteredFindings = computed(() => {
  if (!current.value?.findings) return []
  return filterLevel.value === 'ALL'
    ? current.value.findings
    : current.value.findings.filter(f => f.level === filterLevel.value)
})

onUnmounted(stopPoll)
</script>

<template>
  <div class="diff-review">
    <div class="form-card">
      <div class="panel-title">🔀 对比分析</div>
      <div class="hint">比较两个 ref（分支名、tag 或 commit SHA）之间的累积 diff，适合做 PR review。</div>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="Git 仓库 URL">
          <el-input v-model="form.gitUrl" placeholder="https://github.com/user/repo.git" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="Base（基线）">
            <el-input v-model="form.baseRef" placeholder="main / v1.0.0 / abc1234" />
          </el-form-item>
          <el-form-item label="Head（对比）">
            <el-input v-model="form.headRef" placeholder="feat/xxx / v1.1.0 / def5678" />
          </el-form-item>
        </div>
        <el-form-item label="分支（Clone 时用）">
          <el-input v-model="form.branch" placeholder="可留空（默认拉默认分支）" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="start">
          🚀 开始对比审查
        </el-button>
      </el-form>
    </div>

    <div v-if="current" class="result-area">
      <div v-if="current.status !== 'SUCCESS' && current.status !== 'FAILED'" class="progress-card">
        <el-icon class="spin"><Loading /></el-icon>
        {{ current.progressMessage || '分析中...' }}
        <el-progress :percentage="current.progressPercent || 0" style="width: 100%; margin-top: 10px" />
      </div>
      <div v-else-if="current.status === 'FAILED'" class="error-card">
        ❌ {{ current.errorMsg }}
      </div>
      <div v-else>
        <div class="result-top">
          <ScoreGauge :score="current.overallScore" :size="160" />
          <div class="count-row">
            <div class="c-card c-h" @click="filterLevel = 'HIGH'">高 {{ current.highCount }}</div>
            <div class="c-card c-m" @click="filterLevel = 'MEDIUM'">中 {{ current.mediumCount }}</div>
            <div class="c-card c-l" @click="filterLevel = 'LOW'">低 {{ current.lowCount }}</div>
            <div class="c-card" @click="filterLevel = 'ALL'">全部 {{ (current.findings || []).length }}</div>
          </div>
        </div>
        <div v-if="summaryHtml" class="summary markdown-body" v-html="summaryHtml" />
        <FindingCard v-for="f in filteredFindings" :key="f.id" :finding="f" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.diff-review { padding: 20px; max-width: 1000px; margin: 0 auto; }
.form-card { background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); margin-bottom: 16px; }
.panel-title { font-size: 18px; font-weight: 600; color: #111827; margin-bottom: 6px; }
.hint { color: #6b7280; font-size: 13px; margin-bottom: 14px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.submit-btn { width: 100%; background: linear-gradient(135deg, #10b981, #059669); border: none; }

.result-area { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); }
.progress-card { text-align: center; padding: 40px; color: #6b7280; }
.spin { animation: spin 1s linear infinite; color: #3b82f6; margin-right: 8px; }
@keyframes spin { to { transform: rotate(360deg); } }
.error-card { background: #fef2f2; color: #b91c1c; padding: 20px; border-radius: 8px; }

.result-top { display: flex; gap: 20px; align-items: center; margin-bottom: 20px; padding: 20px; background: #f9fafb; border-radius: 8px; }
.count-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; flex: 1; }
.c-card { padding: 12px; background: #fff; border-radius: 8px; text-align: center; cursor: pointer; font-size: 14px; font-weight: 600; border: 2px solid #e5e7eb; }
.c-card:hover { border-color: #3b82f6; }
.c-h { color: #ef4444; } .c-m { color: #f59e0b; } .c-l { color: #3b82f6; }
.summary { background: #f9fafb; padding: 14px; border-radius: 8px; margin-bottom: 14px; line-height: 1.75; }
.markdown-body :deep(h2) { font-size: 16px; margin: 10px 0; }
</style>
