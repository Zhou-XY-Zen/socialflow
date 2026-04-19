<!--
  CommitReview.vue —— 提交审查三步向导
  Step 1 输入仓库 → Step 2 选 commit → Step 3 审查结果
-->
<script setup lang="ts">
import { onUnmounted, ref, computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { CodeAnalysis, RepoCommit, FindingLevel } from '@/types/codeAnalysis'
import ScoreGauge from '@/components/code-analysis/ScoreGauge.vue'
import FindingCard from '@/components/code-analysis/FindingCard.vue'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const step = ref<1 | 2 | 3>(1)
const form = reactive({ gitUrl: '', branch: 'main' })
const commits = ref<RepoCommit[]>([])
const loadingCommits = ref(false)
const searchKw = ref('')

const current = ref<CodeAnalysis>()
const loading = ref(false)
const filterLevel = ref<FindingLevel | 'ALL'>('ALL')
let poller: number | null = null

const filteredCommits = computed(() => {
  if (!searchKw.value) return commits.value
  const q = searchKw.value.toLowerCase()
  return commits.value.filter(c =>
    c.subject.toLowerCase().includes(q) ||
    (c.author || '').toLowerCase().includes(q) ||
    c.sha.toLowerCase().includes(q))
})

async function fetchCommits() {
  if (!form.gitUrl) { ElMessage.warning('请填写 Git 地址'); return }
  loadingCommits.value = true
  try {
    commits.value = await codeAnalysisApi.listCommits(form)
    if (commits.value.length === 0) ElMessage.info('该分支没有可用提交')
    else step.value = 2
  } catch (e: any) {
    ElMessage.error('拉取 commit 失败：' + (e?.message || e))
  } finally {
    loadingCommits.value = false
  }
}

async function selectCommit(c: RepoCommit) {
  loading.value = true
  step.value = 3
  try {
    const res = await codeAnalysisApi.triggerReview({
      gitUrl: form.gitUrl,
      branch: form.branch,
      commitSha: c.sha,
    })
    await poll(res.id)
  } catch (e: any) {
    ElMessage.error('触发审查失败：' + (e?.message || e))
    loading.value = false
    step.value = 2
  }
}

async function poll(id: string) {
  stopPoll()
  const tick = async () => {
    try {
      const r = await codeAnalysisApi.get(id)
      current.value = r
      if (r.status === 'SUCCESS' || r.status === 'FAILED') {
        stopPoll()
        loading.value = false
      }
    } catch {
      stopPoll()
      loading.value = false
    }
  }
  await tick()
  poller = window.setInterval(tick, 2500)
}
function stopPoll() { if (poller != null) { window.clearInterval(poller); poller = null } }

function reset() {
  step.value = 1
  current.value = undefined
  commits.value = []
  stopPoll()
}

const summaryHtml = computed(() =>
  current.value?.summaryMd ? md.render(current.value.summaryMd) : '')

const filteredFindings = computed(() => {
  if (!current.value?.findings) return []
  if (filterLevel.value === 'ALL') return current.value.findings
  return current.value.findings.filter(f => f.level === filterLevel.value)
})

function fmtTime(s?: string) {
  if (!s) return ''
  return new Date(s).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const STAGE_LABEL: Record<string, string> = {
  INIT: '初始化', CLONING: '克隆仓库', SCANNING: '读取 diff',
  ANALYZING: 'LLM 阿里规约审查', RENDERING: '渲染报告', DONE: '完成',
}

onUnmounted(stopPoll)
</script>

<template>
  <div class="commit-review">
    <!-- 步骤条 -->
    <div class="steps">
      <div class="step-item" :class="{ active: step >= 1, done: step > 1 }">
        <div class="step-dot">1</div>
        <div class="step-text">选择仓库</div>
      </div>
      <div class="step-bar" :class="{ filled: step > 1 }" />
      <div class="step-item" :class="{ active: step >= 2, done: step > 2 }">
        <div class="step-dot">2</div>
        <div class="step-text">选择提交</div>
      </div>
      <div class="step-bar" :class="{ filled: step > 2 }" />
      <div class="step-item" :class="{ active: step >= 3 }">
        <div class="step-dot">3</div>
        <div class="step-text">审查结果</div>
      </div>
    </div>

    <!-- STEP 1 -->
    <div v-if="step === 1" class="step-panel">
      <div class="step-card">
        <div class="step-title">🔎 选择要审查的仓库</div>
        <div class="step-hint">基于阿里巴巴 Java 开发手册（嵩山版）进行代码审查。</div>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="Git 仓库 URL">
            <el-input v-model="form.gitUrl" placeholder="https://github.com/user/repo.git" size="large" clearable />
          </el-form-item>
          <el-form-item label="分支">
            <el-input v-model="form.branch" size="large" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loadingCommits" @click="fetchCommits" class="submit-btn">
            下一步：拉取提交列表 →
          </el-button>
        </el-form>
      </div>
    </div>

    <!-- STEP 2 -->
    <div v-else-if="step === 2" class="step-panel">
      <div class="step-card">
        <div class="step-header">
          <div>
            <div class="step-title">📋 选择要审查的提交</div>
            <div class="step-hint">找到 {{ commits.length }} 条最近的提交</div>
          </div>
          <div>
            <el-input v-model="searchKw" placeholder="搜索 commit message / 作者 / SHA" clearable style="width: 300px" />
            <el-button @click="reset" style="margin-left: 10px">← 返回</el-button>
          </div>
        </div>

        <div class="commit-list">
          <div v-for="c in filteredCommits" :key="c.sha" class="commit-item" @click="selectCommit(c)">
            <div class="commit-sha">{{ c.shortSha }}</div>
            <div class="commit-main">
              <div class="commit-subject">{{ c.subject }}</div>
              <div class="commit-meta">
                👤 {{ c.author }}
                <span v-if="c.commitTime">· {{ fmtTime(c.commitTime) }}</span>
              </div>
            </div>
            <el-button size="small" type="primary" plain>审查 →</el-button>
          </div>
          <div v-if="filteredCommits.length === 0" class="empty">没有匹配的提交</div>
        </div>
      </div>
    </div>

    <!-- STEP 3 -->
    <div v-else-if="step === 3" class="step-panel">
      <!-- 进行中 -->
      <div v-if="!current || (current.status !== 'SUCCESS' && current.status !== 'FAILED')" class="progress-card">
        <div class="progress-title">
          <el-icon class="spin"><Loading /></el-icon>
          {{ STAGE_LABEL[current?.stage || 'INIT'] }}
        </div>
        <el-progress :percentage="current?.progressPercent || 0" :stroke-width="12" status="primary" />
        <div class="progress-msg">{{ current?.progressMessage || '排队中...' }}</div>
      </div>

      <!-- 失败 -->
      <div v-else-if="current.status === 'FAILED'" class="error-card">
        <div class="error-title">❌ 审查失败</div>
        <div class="error-msg">{{ current.errorMsg }}</div>
        <el-button @click="step = 2">返回重试</el-button>
      </div>

      <!-- 成功：结果 -->
      <div v-else class="result-layout">
        <!-- 顶部：评分 + 计数 + 信息 -->
        <div class="result-top">
          <div class="gauge-wrap">
            <ScoreGauge :score="current.overallScore" :size="180" />
          </div>
          <div class="count-col">
            <div class="count-row">
              <div class="count-card c-high" @click="filterLevel = 'HIGH'">
                <div class="c-num">{{ current.highCount }}</div>
                <div class="c-label">🔴 高风险</div>
              </div>
              <div class="count-card c-med" @click="filterLevel = 'MEDIUM'">
                <div class="c-num">{{ current.mediumCount }}</div>
                <div class="c-label">🟡 中风险</div>
              </div>
              <div class="count-card c-low" @click="filterLevel = 'LOW'">
                <div class="c-num">{{ current.lowCount }}</div>
                <div class="c-label">🔵 低风险</div>
              </div>
              <div class="count-card c-all" @click="filterLevel = 'ALL'">
                <div class="c-num">{{ (current.findings || []).length }}</div>
                <div class="c-label">📋 全部</div>
              </div>
            </div>
            <div class="repo-info">
              <span>📦 {{ current.gitUrl }}</span>
              <span>🔀 {{ current.branch }}</span>
              <span>🔢 {{ current.commitSha?.slice(0, 7) }}</span>
              <span v-if="current.durationMs">⏱️ {{ (current.durationMs / 1000).toFixed(1) }}s</span>
            </div>
          </div>
          <div class="action-col">
            <el-button @click="step = 2">← 选其他提交</el-button>
            <el-button @click="reset">↺ 重新开始</el-button>
          </div>
        </div>

        <!-- 总结 -->
        <div v-if="summaryHtml" class="summary-box">
          <div class="box-title">📝 审查总结</div>
          <div class="markdown-body" v-html="summaryHtml" />
        </div>

        <!-- Findings -->
        <div class="findings-box">
          <div class="findings-header">
            <div class="box-title">🔍 详细发现（{{ filteredFindings.length }} 条）</div>
            <div class="filter-tags">
              <span class="ft" :class="{ on: filterLevel === 'ALL' }" @click="filterLevel = 'ALL'">全部</span>
              <span class="ft ft-h" :class="{ on: filterLevel === 'HIGH' }" @click="filterLevel = 'HIGH'">高</span>
              <span class="ft ft-m" :class="{ on: filterLevel === 'MEDIUM' }" @click="filterLevel = 'MEDIUM'">中</span>
              <span class="ft ft-l" :class="{ on: filterLevel === 'LOW' }" @click="filterLevel = 'LOW'">低</span>
            </div>
          </div>
          <div v-if="filteredFindings.length === 0" class="empty">🎉 该等级下没有发现任何问题</div>
          <FindingCard v-for="f in filteredFindings" :key="f.id" :finding="f"
                       @updated="(u) => { const i = current!.findings!.findIndex(x => x.id === u.id); if (i >= 0) current!.findings![i] = u }" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.commit-review { padding: 20px; }

/* Steps */
.steps { display: flex; align-items: center; max-width: 640px; margin: 0 auto 24px; }
.step-item { display: flex; flex-direction: column; align-items: center; flex: 0 0 auto; }
.step-dot {
  width: 36px; height: 36px; border-radius: 50%;
  background: #e5e7eb; color: #9ca3af;
  display: flex; align-items: center; justify-content: center;
  font-weight: 600; transition: all 0.3s;
}
.step-item.active .step-dot { background: #3b82f6; color: #fff; }
.step-item.done .step-dot { background: #10b981; color: #fff; }
.step-text { font-size: 12px; color: #6b7280; margin-top: 6px; }
.step-item.active .step-text { color: #3b82f6; font-weight: 500; }
.step-bar { flex: 1; height: 2px; background: #e5e7eb; margin: 0 12px; transition: background 0.3s; }
.step-bar.filled { background: #10b981; }

.step-panel { max-width: 1000px; margin: 0 auto; }
.step-card {
  background: #fff; border-radius: 12px; padding: 28px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.step-title { font-size: 18px; font-weight: 600; color: #111827; margin-bottom: 6px; }
.step-hint { color: #6b7280; font-size: 13px; margin-bottom: 18px; }
.step-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px; }
.submit-btn { width: 100%; background: linear-gradient(135deg, #667eea, #764ba2); border: none; }

/* Commit list */
.commit-list { max-height: calc(100vh - 280px); overflow-y: auto; }
.commit-item {
  display: flex; align-items: center; gap: 16px;
  padding: 14px 18px; border-radius: 8px;
  cursor: pointer; transition: background 0.2s;
  border: 1px solid transparent;
}
.commit-item:hover { background: #f9fafb; border-color: #d1d5db; }
.commit-sha {
  font-family: 'SF Mono', Menlo, monospace; font-size: 12px;
  background: #ede9fe; color: #6d28d9;
  padding: 4px 10px; border-radius: 4px; flex-shrink: 0;
}
.commit-main { flex: 1; min-width: 0; }
.commit-subject { font-size: 14px; color: #111827; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.commit-meta { color: #6b7280; font-size: 12px; margin-top: 3px; }

.empty { text-align: center; color: #9ca3af; padding: 40px 0; }

/* Progress / error */
.progress-card {
  background: #fff; border-radius: 12px; padding: 40px;
  display: flex; flex-direction: column; gap: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); max-width: 800px; margin: 40px auto;
}
.progress-title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.spin { animation: spin 1s linear infinite; color: #3b82f6; }
@keyframes spin { to { transform: rotate(360deg); } }
.progress-msg { color: #6b7280; font-size: 13px; }

.error-card {
  background: #fef2f2; border: 1px solid #fca5a5; border-radius: 12px; padding: 30px;
  display: flex; flex-direction: column; gap: 12px; max-width: 800px; margin: 40px auto;
}
.error-title { color: #b91c1c; font-size: 16px; font-weight: 600; }
.error-msg { color: #7f1d1d; font-family: monospace; font-size: 13px; white-space: pre-wrap; }

/* 结果布局 */
.result-top {
  background: #fff; border-radius: 12px; padding: 24px;
  display: grid; grid-template-columns: 200px 1fr auto; gap: 20px; align-items: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); margin-bottom: 16px;
}
.gauge-wrap { display: flex; justify-content: center; }
.count-col { display: flex; flex-direction: column; gap: 10px; }
.count-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.count-card {
  background: #f9fafb; padding: 14px; border-radius: 8px;
  text-align: center; cursor: pointer; transition: all 0.2s;
  border: 2px solid transparent;
}
.count-card:hover { transform: translateY(-2px); border-color: #d1d5db; }
.count-card.c-high:hover { border-color: #ef4444; }
.count-card.c-med:hover  { border-color: #f59e0b; }
.count-card.c-low:hover  { border-color: #3b82f6; }
.c-num { font-size: 24px; font-weight: 700; color: #111827; }
.c-label { color: #6b7280; font-size: 12px; margin-top: 2px; }
.repo-info { display: flex; gap: 14px; color: #6b7280; font-size: 12px; flex-wrap: wrap; }

.action-col { display: flex; flex-direction: column; gap: 6px; }

.summary-box, .findings-box {
  background: #fff; border-radius: 12px; padding: 20px 24px; margin-bottom: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.box-title { font-size: 15px; font-weight: 600; color: #111827; margin-bottom: 12px; }
.findings-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.filter-tags { display: flex; gap: 6px; }
.ft {
  padding: 4px 12px; border-radius: 14px; font-size: 12px; cursor: pointer;
  background: #f3f4f6; color: #6b7280; transition: all 0.2s;
}
.ft:hover { background: #e5e7eb; }
.ft.on { background: #3b82f6; color: #fff; }
.ft-h.on { background: #ef4444; }
.ft-m.on { background: #f59e0b; }
.ft-l.on { background: #3b82f6; }

.markdown-body { line-height: 1.75; color: #1f2937; }
.markdown-body :deep(h2) { font-size: 16px; margin: 12px 0 6px; color: #1f2937; }
.markdown-body :deep(p) { margin: 6px 0; }
.markdown-body :deep(code) { background: #f3f4f6; padding: 2px 6px; border-radius: 3px; font-size: 0.9em; color: #6d28d9; }
</style>
