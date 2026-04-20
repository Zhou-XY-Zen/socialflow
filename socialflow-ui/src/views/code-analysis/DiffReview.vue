<!--
  DiffReview.vue —— 两个 ref 对比审查
-->
<script setup lang="ts">
import { onUnmounted, ref, computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import RepoPicker from '@/components/code-analysis/RepoPicker.vue'
import type { CodeAnalysis, FindingLevel } from '@/types/codeAnalysis'
import ScoreGauge from '@/components/code-analysis/ScoreGauge.vue'
import FindingCard from '@/components/code-analysis/FindingCard.vue'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const form = reactive({ gitUrl: '', branch: '', baseRef: '', headRef: '' })
const pickerValue = reactive<{ gitUrl: string; branch?: string; credentialId?: string }>({
  gitUrl: '', branch: '',
})
function onPickerChange(v: { gitUrl: string; branch?: string; credentialId?: string }) {
  form.gitUrl = v.gitUrl || ''
  form.branch = v.branch || ''
  pickerValue.gitUrl = v.gitUrl || ''
  pickerValue.branch = v.branch || ''
  pickerValue.credentialId = v.credentialId
}
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

const STAGE_LABEL: Record<string, string> = {
  INIT: '初始化', CLONING: '克隆仓库', SCANNING: '读取 Diff',
  FILE_REVIEW: '逐文件审查', SELF_CHECK: 'AI 自检',
  FINAL: '合并总结', RENDERING: '渲染报告', DONE: '完成',
}

/** 管线阶段可视化 */
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
  <div class="diff-review">
    <div class="form-card">
      <div class="panel-title"><el-icon><Connection /></el-icon>对比分析</div>
      <div class="hint">比较两个 ref（分支名、tag 或 commit SHA）之间的累积 diff，适合做 PR Review。</div>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="选择仓库">
          <RepoPicker :model-value="pickerValue" @update:model-value="onPickerChange" :show-branch="true" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="Base（基线）">
            <el-input v-model="form.baseRef" placeholder="main / v1.0.0 / abc1234">
              <template #prefix><el-icon><Flag /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="Head（对比）">
            <el-input v-model="form.headRef" placeholder="feat/xxx / v1.1.0 / def5678">
              <template #prefix><el-icon><Position /></el-icon></template>
            </el-input>
          </el-form-item>
        </div>
        <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="start">
          <el-icon v-if="!loading" style="margin-right:6px"><MagicStick /></el-icon>
          开始对比审查
        </el-button>
      </el-form>
    </div>

    <!-- 等待输入时展示的亮点和示意（current 为空时） -->
    <div v-if="!current" class="intro-area">
      <div class="highlight-row">
        <div class="hl-card">
          <div class="hl-icon is-brand"><el-icon><Connection /></el-icon></div>
          <div class="hl-title">跨分支审查</div>
          <div class="hl-desc">main...feat/xxx · tag 对比 · hotfix 验收</div>
        </div>
        <div class="hl-card">
          <div class="hl-icon is-info"><el-icon><Document /></el-icon></div>
          <div class="hl-title">按文件切片</div>
          <div class="hl-desc">大 Diff 自动拆文件 · 独立送 LLM</div>
        </div>
        <div class="hl-card">
          <div class="hl-icon is-success"><el-icon><CircleCheck /></el-icon></div>
          <div class="hl-title">阿里黄山规约</div>
          <div class="hl-desc">321 条真实规约编号 · 白名单校验</div>
        </div>
        <div class="hl-card">
          <div class="hl-icon is-sunset"><el-icon><MagicStick /></el-icon></div>
          <div class="hl-title">AI 自检</div>
          <div class="hl-desc">置信度 &lt; 60 自动滤除 · 误判率 &lt; 10%</div>
        </div>
      </div>

      <div class="flow-hint">
        <el-icon class="flow-icon"><InfoFilled /></el-icon>
        <div class="flow-text">
          <strong>使用提示：</strong>
          Base 通常填目标分支（如 <code>main</code>），Head 填改动所在分支（如 <code>feat/payment</code>）。
          tag 和 commit SHA 也都支持。
        </div>
      </div>
    </div>

    <div v-if="current" class="result-area">
      <!-- 进行中 -->
      <div v-if="current.status !== 'SUCCESS' && current.status !== 'FAILED'" class="progress-card">
        <div class="pc-bg-glow pc-bg-glow-1"></div>
        <div class="pc-bg-glow pc-bg-glow-2"></div>

        <div class="progress-hero">
          <div class="progress-icon-wrap">
            <span class="pc-ring pc-ring-1"></span>
            <span class="pc-ring pc-ring-2"></span>
            <el-icon class="spin"><Loading /></el-icon>
          </div>
          <div class="progress-stage">{{ STAGE_LABEL[current.stage || 'INIT'] || current.stage }}</div>
          <div class="progress-msg">{{ current.progressMessage || '排队中...' }}</div>
        </div>

        <el-progress :percentage="current.progressPercent || 0" :stroke-width="10"
                     :color="[{color: '#667eea', percentage: 50}, {color: '#764ba2', percentage: 100}]" />

        <div class="pipeline">
          <div v-for="st in pipelineStages" :key="st.key" class="pl-step"
               :class="{ done: stageIndex(current?.stage) > st.idx, active: stageIndex(current?.stage) === st.idx }">
            <div class="pl-dot">
              <el-icon><component :is="st.icon" /></el-icon>
            </div>
            <div class="pl-label">{{ st.label }}</div>
          </div>
        </div>

        <div class="did-you-know">
          <el-icon class="dyk-icon"><MagicStick /></el-icon>
          <div>
            <strong>你知道吗：</strong>
            对比分析会把两 ref 之间的 diff 按文件切片独立送 LLM，
            再用"质疑者"视角二次复核，确保发现真实且精准。
          </div>
        </div>
      </div>

      <!-- 失败 -->
      <div v-else-if="current.status === 'FAILED'" class="error-card">
        <div class="error-icon-wrap"><el-icon><CircleCloseFilled /></el-icon></div>
        <div class="error-title">对比审查失败</div>
        <div class="error-msg">{{ current.errorMsg }}</div>
      </div>

      <!-- 成功 -->
      <div v-else>
        <div class="result-top">
          <ScoreGauge :score="current.overallScore" :size="160" />
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
        </div>
        <div v-if="summaryHtml" class="summary markdown-body" v-html="summaryHtml" />
        <FindingCard v-for="f in filteredFindings" :key="f.id" :finding="f" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.diff-review {
  padding: var(--sf-space-5);
  max-width: 1000px;
  margin: 0 auto;
  animation: sf-fade-in var(--sf-transition-base);
}

.form-card {
  background: var(--sf-surface-gradient);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-6);
  box-shadow: var(--sf-shadow-sm);
  margin-bottom: var(--sf-space-4);
  transition: box-shadow var(--sf-transition-base);
}
.form-card:hover { box-shadow: var(--sf-shadow-md); }
.panel-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--sf-text-primary);
  margin-bottom: var(--sf-space-2);
  display: flex;
  align-items: center;
  gap: var(--sf-space-2);
}
.panel-title :deep(.el-icon) { color: var(--sf-primary); font-size: 20px; }
.hint {
  color: var(--sf-text-tertiary);
  font-size: 13px;
  margin-bottom: var(--sf-space-4);
  line-height: 1.6;
}
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sf-space-4); }
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

/* ========== 亮点 + 提示（表单下方，填满空间） ========== */
.intro-area { display: flex; flex-direction: column; gap: var(--sf-space-4); }
.highlight-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sf-space-3);
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

.flow-hint {
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
.flow-text strong { color: var(--sf-text-primary); font-weight: 600; margin-right: 4px; }
.flow-text code {
  background: rgba(102,126,234,0.1);
  color: var(--sf-primary-dark);
  padding: 2px 8px;
  border-radius: var(--sf-radius-xs);
  font-size: 12px;
  font-family: 'SF Mono', Menlo, monospace;
}

/* ========== 结果区 ========== */
.result-area {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-5);
  box-shadow: var(--sf-shadow-sm);
}

/* 进行中 */
.progress-card {
  position: relative;
  padding: var(--sf-space-6);
  display: flex;
  flex-direction: column;
  gap: var(--sf-space-4);
  overflow: hidden;
  background: var(--sf-surface-gradient);
  border-radius: var(--sf-radius-md);
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

/* 错误卡 */
.error-card {
  background: linear-gradient(135deg, #fff1f2 0%, #ffe4e6 100%);
  border: 1px solid #fca5a5;
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-6);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sf-space-3);
  box-shadow: var(--sf-shadow-glow-danger);
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
.error-title { color: #b91c1c; font-size: 18px; font-weight: 700; }
.error-msg {
  color: #991b1b;
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 13px;
  white-space: pre-wrap;
  background: rgba(255,255,255,0.6);
  padding: var(--sf-space-3);
  border-radius: var(--sf-radius-sm);
  max-width: 600px;
}

/* 成功结果顶部 */
.result-top {
  display: flex;
  gap: var(--sf-space-5);
  align-items: center;
  margin-bottom: var(--sf-space-5);
  padding: var(--sf-space-5);
  background: var(--sf-surface-gradient-soft);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
}
.count-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sf-space-3);
  flex: 1;
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
  top: 0; left: 0; right: 0;
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

.summary {
  background: var(--sf-surface-gradient-soft);
  border: 1px solid var(--sf-border-light);
  padding: var(--sf-space-4);
  border-radius: var(--sf-radius-md);
  margin-bottom: var(--sf-space-4);
  line-height: 1.75;
  color: var(--sf-text-primary);
}
.markdown-body :deep(h2) { font-size: 17px; margin: 10px 0; color: var(--sf-text-primary); }

.cred-badge { margin-top: 8px; padding: 6px 12px; border-radius: var(--sf-radius-sm); font-size: 12px; line-height: 1.6; }
.cred-badge.cred-ok { background: var(--sf-success-bg); border: 1px solid #bbf7d0; color: var(--sf-success); }
.cred-badge.cred-miss { background: var(--sf-warning-bg); border: 1px solid #fde68a; color: #b45309; }
.cred-badge code { background: rgba(0,0,0,0.06); padding: 1px 5px; border-radius: 3px; font-size: 11px; color: var(--sf-primary-dark); }
.cred-type { margin-left: 6px; padding: 1px 8px; background: rgba(102,126,234,0.1); color: var(--sf-primary-dark); border-radius: var(--sf-radius-full); font-size: 11px; }
</style>
