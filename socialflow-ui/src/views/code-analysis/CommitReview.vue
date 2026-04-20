<!--
  CommitReview.vue —— 提交审查三步向导
  Step 1 输入仓库 → Step 2 选 commit → Step 3 审查结果
-->
<script setup lang="ts">
import { onUnmounted, ref, computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import RepoPicker from '@/components/code-analysis/RepoPicker.vue'
import type { CodeAnalysis, RepoCommit, FindingLevel } from '@/types/codeAnalysis'
import ScoreGauge from '@/components/code-analysis/ScoreGauge.vue'
import FindingCard from '@/components/code-analysis/FindingCard.vue'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const step = ref<1 | 2 | 3>(1)
const form = reactive({ gitUrl: '', branch: 'main' })

const pickerValue = reactive<{ gitUrl: string; branch?: string; credentialId?: string }>({
  gitUrl: '', branch: 'main',
})
function onPickerChange(v: { gitUrl: string; branch?: string; credentialId?: string }) {
  form.gitUrl = v.gitUrl || ''
  form.branch = v.branch || 'main'
  pickerValue.gitUrl = v.gitUrl || ''
  pickerValue.branch = v.branch || 'main'
  pickerValue.credentialId = v.credentialId
}
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
  FILE_REVIEW: '逐文件审查', SELF_CHECK: 'AI 自检',
  FINAL: '合并总结', ANALYZING: 'LLM 审查', RENDERING: '渲染报告', DONE: '完成',
}

/** 管线阶段可视化 —— 给进行中页面用 */
const pipelineStages = [
  { key: 'CLONING',     idx: 0, label: '克隆仓库', icon: 'Download' },
  { key: 'SCANNING',    idx: 1, label: '读取 Diff', icon: 'Document' },
  { key: 'FILE_REVIEW', idx: 2, label: '逐文件审查', icon: 'Search' },
  { key: 'SELF_CHECK',  idx: 3, label: 'AI 自检', icon: 'MagicStick' },
  { key: 'FINAL',       idx: 4, label: '合并总结', icon: 'Document' },
]
function stageIndex(stage?: string): number {
  if (!stage) return -1
  if (stage.startsWith('CLONING')) return 0
  if (stage.startsWith('SCANNING')) return 1
  if (stage.startsWith('FILE_REVIEW') || stage.startsWith('MODULE_SUMMARY')) return 2
  if (stage.startsWith('SELF_CHECK')) return 3
  if (stage.startsWith('FINAL') || stage === 'RENDERING' || stage === 'DONE') return 4
  return -1
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
        <div class="step-title"><el-icon><Search /></el-icon>选择要审查的仓库</div>
        <div class="step-hint">基于阿里巴巴《Java 开发手册（黄山版）》321 条规约进行 AI 代码审查。</div>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="选择要审查的仓库">
            <RepoPicker :model-value="pickerValue" @update:model-value="onPickerChange" :show-branch="true" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loadingCommits" @click="fetchCommits" class="submit-btn"
                     :disabled="!form.gitUrl">
            <el-icon v-if="!loadingCommits" style="margin-right:6px"><Right /></el-icon>
            下一步：拉取提交列表
          </el-button>
        </el-form>
      </div>

      <!-- 审查能力亮点 -->
      <div class="highlight-row">
        <div class="hl-card">
          <div class="hl-icon is-brand"><el-icon><Reading /></el-icon></div>
          <div class="hl-title">321 条规约</div>
          <div class="hl-desc">阿里黄山版全量接入，支持启停 / 自定义</div>
        </div>
        <div class="hl-card">
          <div class="hl-icon is-info"><el-icon><Operation /></el-icon></div>
          <div class="hl-title">三层反向校验</div>
          <div class="hl-desc">规约白名单 · 行号校验 · 代码片段匹配</div>
        </div>
        <div class="hl-card">
          <div class="hl-icon is-success"><el-icon><CircleCheck /></el-icon></div>
          <div class="hl-title">AI 自检二次复核</div>
          <div class="hl-desc">置信度 &lt; 60 的发现自动过滤</div>
        </div>
        <div class="hl-card">
          <div class="hl-icon is-sunset"><el-icon><ChatLineRound /></el-icon></div>
          <div class="hl-title">用户反馈闭环</div>
          <div class="hl-desc">标记 INVALID 3 次的规约自动屏蔽</div>
        </div>
      </div>

      <!-- 流程示意 -->
      <div class="flow-hint">
        <el-icon class="flow-icon"><InfoFilled /></el-icon>
        <div class="flow-text">
          <strong>审查流程：</strong>
          克隆仓库 → 按文件切分 Diff → 注入相关规约 → LLM 审查 → 三层校验 → Self-Check 过滤 → 合并总结
        </div>
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
        <!-- 背景光斑装饰 -->
        <div class="pc-bg-glow pc-bg-glow-1"></div>
        <div class="pc-bg-glow pc-bg-glow-2"></div>

        <!-- 当前阶段 hero -->
        <div class="progress-hero">
          <div class="progress-icon-wrap">
            <span class="pc-ring pc-ring-1"></span>
            <span class="pc-ring pc-ring-2"></span>
            <el-icon class="spin"><Loading /></el-icon>
          </div>
          <div class="progress-stage">{{ STAGE_LABEL[current?.stage || 'INIT'] }}</div>
          <div class="progress-msg">{{ current?.progressMessage || '排队中...' }}</div>
        </div>

        <!-- 进度条 -->
        <el-progress :percentage="current?.progressPercent || 0" :stroke-width="10"
                     :color="[{color: '#667eea', percentage: 50}, {color: '#764ba2', percentage: 100}]" />

        <!-- 管线阶段可视化 -->
        <div class="pipeline">
          <div v-for="st in pipelineStages" :key="st.key" class="pl-step"
               :class="{ done: stageIndex(current?.stage) > st.idx, active: stageIndex(current?.stage) === st.idx }">
            <div class="pl-dot">
              <el-icon><component :is="st.icon" /></el-icon>
            </div>
            <div class="pl-label">{{ st.label }}</div>
          </div>
        </div>

        <!-- 你知道吗 -->
        <div class="did-you-know">
          <el-icon class="dyk-icon"><MagicStick /></el-icon>
          <div>
            <strong>你知道吗：</strong>
            AI 审查后会把所有 findings 以"质疑者"身份再送一次 LLM，
            置信度 &lt; 60% 的自动过滤，误判率从 70% 降到 &lt; 10%。
          </div>
        </div>
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
.commit-review {
  padding: var(--sf-space-5);
  animation: sf-fade-in var(--sf-transition-base);
}

/* ========== 步骤条 ========== */
.steps {
  display: flex;
  align-items: center;
  max-width: 640px;
  margin: 0 auto var(--sf-space-5);
}
.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 0 0 auto;
}
.step-dot {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--sf-surface);
  color: var(--sf-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  border: 2px solid var(--sf-border);
  transition: all var(--sf-transition-base);
  font-variant-numeric: tabular-nums;
}
.step-item.active .step-dot {
  background: var(--sf-gradient);
  color: #fff;
  border-color: transparent;
  box-shadow: var(--sf-shadow-brand);
  transform: scale(1.1);
}
.step-item.done .step-dot {
  background: var(--sf-success-gradient);
  color: #fff;
  border-color: transparent;
}
.step-text {
  font-size: 12px;
  color: var(--sf-text-muted);
  margin-top: 6px;
  font-weight: 500;
}
.step-item.active .step-text { color: var(--sf-primary-dark); font-weight: 600; }
.step-item.done .step-text { color: var(--sf-success); }
.step-bar {
  flex: 1;
  height: 2px;
  background: var(--sf-border-light);
  margin: 0 var(--sf-space-3);
  transition: background var(--sf-transition-base);
  border-radius: var(--sf-radius-full);
  position: relative;
  top: -10px;
}
.step-bar.filled {
  background: var(--sf-success-gradient);
}

/* ========== 步骤主卡 ========== */
.step-panel { max-width: 1000px; margin: 0 auto; }
.step-card {
  background: var(--sf-surface-gradient);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-6);
  box-shadow: var(--sf-shadow-sm);
  transition: box-shadow var(--sf-transition-base);
}
.step-card:hover { box-shadow: var(--sf-shadow-md); }
.step-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--sf-text-primary);
  margin-bottom: var(--sf-space-2);
  display: flex;
  align-items: center;
  gap: var(--sf-space-2);
}
.step-title :deep(.el-icon) { color: var(--sf-primary); font-size: 20px; }
.step-hint {
  color: var(--sf-text-tertiary);
  font-size: 13px;
  margin-bottom: var(--sf-space-5);
  line-height: 1.6;
}
.step-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--sf-space-5);
}
.submit-btn {
  width: 100%;
  background: var(--sf-gradient-aurora) !important;
  border: none !important;
  box-shadow: var(--sf-shadow-brand) !important;
  font-weight: 600 !important;
  height: 44px !important;
  transition: all var(--sf-transition-base) !important;
}
.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--sf-shadow-glow-brand) !important;
}

/* ========== 审查能力亮点 4 连卡 ========== */
.highlight-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sf-space-3);
  margin-top: var(--sf-space-4);
}
.hl-card {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-4);
  transition: all var(--sf-transition-base);
  text-align: center;
}
.hl-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--sf-shadow-md);
  border-color: var(--sf-primary-light);
}
.hl-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--sf-radius-md);
  margin: 0 auto var(--sf-space-2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  box-shadow: var(--sf-shadow-sm);
}
.hl-icon.is-brand   { background: var(--sf-gradient-aurora); }
.hl-icon.is-info    { background: var(--sf-info-gradient); }
.hl-icon.is-success { background: var(--sf-success-gradient); }
.hl-icon.is-sunset  { background: var(--sf-gradient-sunset); }
.hl-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--sf-text-primary);
  margin-bottom: 4px;
}
.hl-desc {
  font-size: 12px;
  color: var(--sf-text-tertiary);
  line-height: 1.5;
}

/* ========== 流程说明提示 ========== */
.flow-hint {
  margin-top: var(--sf-space-4);
  background: linear-gradient(135deg, rgba(102,126,234,0.06) 0%, rgba(118,75,162,0.06) 100%);
  border: 1px solid rgba(102,126,234,0.15);
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-3) var(--sf-space-4);
  display: flex;
  align-items: flex-start;
  gap: var(--sf-space-3);
}
.flow-icon {
  color: var(--sf-primary);
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 2px;
}
.flow-text {
  color: var(--sf-text-secondary);
  font-size: 12.5px;
  line-height: 1.7;
}
.flow-text strong {
  color: var(--sf-text-primary);
  font-weight: 600;
  margin-right: 4px;
}

/* ========== Commit 列表 ========== */
.commit-list {
  max-height: calc(100vh - 320px);
  overflow-y: auto;
  padding: 4px;
}
.commit-item {
  display: flex;
  align-items: center;
  gap: var(--sf-space-4);
  padding: var(--sf-space-3) var(--sf-space-4);
  border-radius: var(--sf-radius-md);
  cursor: pointer;
  transition: all var(--sf-transition-fast);
  border: 1px solid transparent;
  margin-bottom: 4px;
}
.commit-item:hover {
  background: var(--sf-surface-hover);
  border-color: var(--sf-primary-light);
  transform: translateX(2px);
  box-shadow: var(--sf-shadow-xs);
}
.commit-sha {
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 12px;
  background: rgba(102,126,234,0.1);
  color: var(--sf-primary-dark);
  padding: 4px 10px;
  border-radius: var(--sf-radius-sm);
  flex-shrink: 0;
  border: 1px solid rgba(102,126,234,0.15);
  font-weight: 600;
}
.commit-main { flex: 1; min-width: 0; }
.commit-subject {
  font-size: 14px;
  color: var(--sf-text-primary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.commit-meta {
  color: var(--sf-text-muted);
  font-size: 12px;
  margin-top: 3px;
}

.empty {
  text-align: center;
  color: var(--sf-text-muted);
  padding: var(--sf-space-7) 0;
}

/* ========== 进行中卡片（丰富版） ========== */
.progress-card {
  position: relative;
  background: var(--sf-surface-gradient);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-6);
  box-shadow: var(--sf-shadow-sm);
  max-width: 800px;
  margin: var(--sf-space-5) auto;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: var(--sf-space-4);
}
.pc-bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(50px);
  opacity: 0.4;
  pointer-events: none;
}
.pc-bg-glow-1 {
  width: 280px; height: 280px;
  top: -100px; right: -50px;
  background: radial-gradient(circle, #a5b4fc 0%, transparent 70%);
  animation: sf-float 8s ease-in-out infinite;
}
.pc-bg-glow-2 {
  width: 260px; height: 260px;
  bottom: -100px; left: -40px;
  background: radial-gradient(circle, #f9a8d4 0%, transparent 70%);
  animation: sf-float 10s ease-in-out infinite reverse;
}

/* Hero */
.progress-hero {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sf-space-2);
  padding: var(--sf-space-3) 0;
}
.progress-icon-wrap {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--sf-gradient-aurora);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--sf-shadow-glow-brand);
  margin-bottom: var(--sf-space-2);
}
.progress-icon-wrap .spin {
  animation: spin 1s linear infinite;
  color: #fff;
  font-size: 32px;
  z-index: 2;
}
.pc-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid rgba(102, 126, 234, 0.4);
  animation: pc-ring-pulse 2.2s cubic-bezier(0.4, 0, 0.2, 1) infinite;
  pointer-events: none;
}
.pc-ring-1 { animation-delay: 0s; }
.pc-ring-2 { animation-delay: 0.8s; }
@keyframes pc-ring-pulse {
  0%   { transform: scale(1);   opacity: 0.6; }
  100% { transform: scale(1.8); opacity: 0;   }
}
@keyframes spin { to { transform: rotate(360deg); } }
.progress-stage {
  font-size: 20px;
  font-weight: 700;
  color: var(--sf-text-primary);
  background: var(--sf-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.progress-msg {
  color: var(--sf-text-tertiary);
  font-size: 13px;
  text-align: center;
}

/* 管线可视化 */
.pipeline {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sf-space-3) 0;
}
.pl-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  position: relative;
  flex: 1;
}
.pl-step:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 16px;
  left: calc(50% + 18px);
  right: calc(-50% + 18px);
  height: 2px;
  background: var(--sf-border);
  z-index: 0;
  transition: background var(--sf-transition-base);
}
.pl-step.done::after { background: var(--sf-success); }
.pl-step.active::after { background: linear-gradient(90deg, var(--sf-primary), var(--sf-border)); }
.pl-dot {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--sf-surface);
  border: 2px solid var(--sf-border);
  color: var(--sf-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  z-index: 1;
  transition: all var(--sf-transition-base);
}
.pl-step.done .pl-dot {
  background: var(--sf-success-gradient);
  border-color: transparent;
  color: #fff;
}
.pl-step.active .pl-dot {
  background: var(--sf-gradient);
  border-color: transparent;
  color: #fff;
  box-shadow: var(--sf-shadow-brand);
  animation: sf-glow-pulse 1.8s ease-in-out infinite;
}
.pl-label {
  font-size: 11px;
  color: var(--sf-text-tertiary);
  font-weight: 500;
}
.pl-step.active .pl-label { color: var(--sf-primary-dark); font-weight: 600; }
.pl-step.done .pl-label { color: var(--sf-success); }

/* 你知道吗 */
.did-you-know {
  position: relative;
  z-index: 1;
  background: rgba(255,255,255,0.7);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(102,126,234,0.15);
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-3) var(--sf-space-4);
  display: flex;
  align-items: flex-start;
  gap: var(--sf-space-3);
  color: var(--sf-text-secondary);
  font-size: 12.5px;
  line-height: 1.7;
}
.dyk-icon {
  color: var(--sf-primary);
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 2px;
}
.did-you-know strong {
  color: var(--sf-text-primary);
  font-weight: 600;
  margin-right: 4px;
}

/* ========== 错误卡 ========== */
.error-card {
  background: linear-gradient(135deg, #fff1f2 0%, #ffe4e6 100%);
  border: 1px solid #fca5a5;
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-6);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: var(--sf-space-3);
  max-width: 800px;
  margin: var(--sf-space-5) auto;
  box-shadow: var(--sf-shadow-glow-danger);
}
.error-title {
  color: #b91c1c;
  font-size: 18px;
  font-weight: 700;
}
.error-msg {
  color: #991b1b;
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 13px;
  white-space: pre-wrap;
  background: rgba(255,255,255,0.5);
  padding: var(--sf-space-3);
  border-radius: var(--sf-radius-sm);
  width: 100%;
  max-width: 600px;
}

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

.cred-badge { margin-top: 8px; padding: 6px 12px; border-radius: 6px; font-size: 12px; line-height: 1.6; }
.cred-badge.cred-ok { background: #f0fdf4; border: 1px solid #bbf7d0; color: #059669; }
.cred-badge.cred-miss { background: #fffbeb; border: 1px solid #fde68a; color: #b45309; }
.cred-badge code { background: rgba(0,0,0,0.06); padding: 1px 5px; border-radius: 3px; font-size: 11px; color: #6d28d9; }
.cred-type { margin-left: 6px; padding: 1px 8px; background: #ede9fe; color: #6d28d9; border-radius: 10px; font-size: 11px; }
</style>
