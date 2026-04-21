<!--
  ProjectOverview.vue —— 根据 Git URL 生成项目介绍
-->
<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed, reactive, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import { useMermaid } from '@/composables/useMermaid'
import RepoPicker from '@/components/code-analysis/RepoPicker.vue'
import type { CodeAnalysis, LlmCallLog, RepoBookmark } from '@/types/codeAnalysis'

const router = useRouter()
const route = useRoute()
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const form = reactive({
  gitUrl: '',
  branch: 'main',
  userRequirements: '',
})

/** 预置示例诉求，点一下填入 textarea，引导用户快速上手 */
const REQ_EXAMPLES: { label: string; text: string }[] = [
  { label: '架构与扩展点', text: '请详细说明项目的分层架构、模块之间的调用关系，以及二次开发时可以介入的扩展点（接口、回调、配置项、SPI 等）。最好用 Mermaid 画出关键调用链。' },
  { label: '关键算法原理', text: '我想彻底弄懂以下关键算法/流程的实现原理（按代码逐层展开）：(1) RAG 检索+重排 (2) 代码审查的规约注入与反向校验 (3) Finding 反馈闭环的去重与屏蔽机制。最好配合伪代码或流程图说明。' },
  { label: '缓存与容灾', text: '请详细讲清楚项目的缓存策略、限流熔断、降级回退、重试机制各自如何实现的。分别列出：涉及的类/配置/注解、触发条件、失败后的行为、监控指标。' },
  { label: '接入与上手', text: '假设我是新入职的工程师，请帮我整理一份"第一周上手手册"：环境准备、启动流程、最常见的 10 个开发任务怎么做（比如加一个 API、加一个 LLM Provider、加一个定时任务）、踩坑经验。' },
  { label: '性能与并发', text: '请详细分析项目的性能关键点：数据库索引使用、慢查询、线程池配置、异步任务调度、高并发场景的竞态保护。每一项都要具体到代码位置。' },
]
function fillExample(i: number) {
  form.userRequirements = REQ_EXAMPLES[i].text
}

// 用于 v-model 给 RepoPicker
const pickerValue = reactive<{ gitUrl: string; branch?: string; credentialId?: string }>({
  gitUrl: '',
  branch: 'main',
  credentialId: undefined,
})
// picker 变化时同步到 form
function onPickerChange(v: { gitUrl: string; branch?: string; credentialId?: string }) {
  form.gitUrl = v.gitUrl || ''
  form.branch = v.branch || 'main'
  pickerValue.gitUrl = v.gitUrl || ''
  pickerValue.branch = v.branch || 'main'
  pickerValue.credentialId = v.credentialId
}
const loading = ref(false)
const current = ref<CodeAnalysis>()
const bookmarks = ref<RepoBookmark[]>([])
const exporting = ref(false)
let pollerTimer: number | null = null
// 指数退避：初始 2s，每轮 ×1.25，上限 10s（大仓库分析耗时长，缓解后端压力）
const POLL_BASE_MS = 2000
const POLL_MAX_MS = 10000
let pollDelayMs = POLL_BASE_MS

const STAGE_LABEL: Record<string, string> = {
  INIT: '初始化', CLONING: '克隆仓库', SCANNING: '扫描结构',
  MODULE_SUMMARY: '逐模块摘要', FINAL: '汇总全景',
  ANALYZING: 'LLM 分析', RENDERING: '渲染报告', DONE: '完成',
}

/* ====== 进行中页的实时遥测：计时器 + Token + 日志 ====== */
const analysisStartAt = ref(0)            // Date.now() 记录开始时间
const elapsedSec = ref(0)                 // 已耗时（秒），每秒 tick
const liveCalls = ref<LlmCallLog[]>([])   // 实时 LLM 调用记录
let elapsedTimer: number | null = null

/** mm:ss 格式的耗时字符串 */
const elapsedStr = computed(() => {
  const s = elapsedSec.value
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
})

/** 聚合当前实时 Token 消耗 */
const liveTokens = computed(() => {
  const sum = liveCalls.value.reduce((a, c) => a + (c.totalTokens || 0), 0)
  const prompt = liveCalls.value.reduce((a, c) => a + (c.promptTokens || 0), 0)
  const completion = liveCalls.value.reduce((a, c) => a + (c.completionTokens || 0), 0)
  return { sum, prompt, completion, count: liveCalls.value.length }
})

/** 按当前进度 + 已耗时线性外推剩余时间 */
const estimatedRemainStr = computed(() => {
  const p = current.value?.progressPercent || 0
  if (p <= 5 || p >= 100 || elapsedSec.value < 3) return '估算中...'
  const remain = Math.ceil(elapsedSec.value / p * (100 - p))
  if (remain < 60) return `约 ${remain} 秒`
  return `约 ${Math.floor(remain / 60)} 分 ${remain % 60} 秒`
})

/** 项目概览的阶段管线 */
const pipelineStages = [
  { key: 'CLONING',        idx: 0, label: '克隆仓库',   icon: 'Download' },
  { key: 'SCANNING',       idx: 1, label: '扫描源文件',  icon: 'Search' },
  { key: 'MODULE_SUMMARY', idx: 2, label: '逐模块摘要',  icon: 'Document' },
  { key: 'FINAL',          idx: 3, label: '汇总全景',   icon: 'MagicStick' },
  { key: 'RENDERING',      idx: 4, label: '渲染结果',   icon: 'Reading' },
]
function stageIndex(stage?: string): number {
  if (!stage) return -1
  if (stage.startsWith('CLONING')) return 0
  if (stage.startsWith('SCANNING')) return 1
  if (stage.startsWith('MODULE_SUMMARY')) return 2
  if (stage === 'FINAL' || stage.startsWith('FINAL')) return 3
  if (stage === 'RENDERING' || stage === 'DONE') return 4
  return -1
}

/** 每秒自增 elapsedSec */
function startElapsedTimer() {
  if (elapsedTimer != null) return
  analysisStartAt.value = Date.now()
  elapsedSec.value = 0
  elapsedTimer = window.setInterval(() => {
    if (analysisStartAt.value > 0) {
      elapsedSec.value = Math.floor((Date.now() - analysisStartAt.value) / 1000)
    }
  }, 1000)
}
function stopElapsedTimer() {
  if (elapsedTimer != null) { window.clearInterval(elapsedTimer); elapsedTimer = null }
}

/** Token 数字格式化（K / M） */
function fmtTokens(n?: number | null) {
  if (n == null || n === 0) return '0'
  if (n < 1000) return String(n)
  if (n < 1_000_000) return (n / 1000).toFixed(1) + 'K'
  return (n / 1_000_000).toFixed(2) + 'M'
}
function fmtLatency(ms?: number | null) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

/** "你知道吗" 卡片轮播 */
const FACTS = [
  'Map-Reduce 扫描会把每个模块独立送 LLM 读全部源码，最后再汇总成项目全景',
  '大模块会自动分批喂，避免单次 prompt 超过 token 上限',
  '生成的 Mermaid 会经过 3 重清洗：去重声明 / 修复畸形节点 / 标签自动加引号',
  '每次 LLM 调用都会落入 llm_call_log 表，代码分析仪表盘可实时查询',
  '分析过程加了 Resilience4j 熔断 + retry，一两次网络抖动不会导致任务失败',
  '项目概览默认走 DeepSeek V3；生成中可随时在历史记录里看到正在进行的分析',
]
const factIndex = ref(0)
let factTimer: number | null = null
function startFactRotate() {
  if (factTimer != null) return
  factTimer = window.setInterval(() => {
    factIndex.value = (factIndex.value + 1) % FACTS.length
  }, 5500)
}
function stopFactRotate() {
  if (factTimer != null) { window.clearInterval(factTimer); factTimer = null }
}

const summaryHtml = computed(() =>
  current.value?.summaryMd ? md.render(current.value.summaryMd) : '')

// Mermaid 流程图渲染
const { svg: mermaidSvg, error: mermaidError, render: renderMermaid } = useMermaid()
watch(() => current.value?.mermaidCode, async (code) => {
  await renderMermaid(code)
}, { immediate: true })

async function loadBookmarks() {
  try { bookmarks.value = await codeAnalysisApi.listBookmarks() } catch { /* ignore */ }
}

function fillFromBookmark(b: RepoBookmark) {
  form.gitUrl = b.gitUrl
  form.branch = b.branch || 'main'
}

async function start() {
  if (!form.gitUrl) {
    ElMessage.warning('请填写 Git 仓库地址')
    return
  }
  loading.value = true
  try {
    const res = await codeAnalysisApi.triggerProject(form)
    await poll(res.id)
  } catch (e: any) {
    ElMessage.error('触发失败：' + (e?.message || e))
    loading.value = false
  }
}

async function poll(id: string) {
  stopPoll()
  pollDelayMs = POLL_BASE_MS
  // 启动实时遥测：计时器 + 你知道吗轮播 + 重置 LLM 调用列表
  startElapsedTimer()
  startFactRotate()
  liveCalls.value = []
  const tick = async () => {
    try {
      const r = await codeAnalysisApi.get(id)
      current.value = r
      // 并行拉 LLM 调用详情（出错忽略，不影响主轮询）
      try { liveCalls.value = await codeAnalysisApi.llmCalls(id) } catch { /* ignore */ }
      if (r.status === 'SUCCESS' || r.status === 'FAILED') {
        stopPoll()
        stopElapsedTimer()
        stopFactRotate()
        loading.value = false
        return
      }
      pollDelayMs = Math.min(Math.ceil(pollDelayMs * 1.25), POLL_MAX_MS)
      pollerTimer = window.setTimeout(tick, pollDelayMs)
    } catch {
      stopPoll()
      stopElapsedTimer()
      stopFactRotate()
      loading.value = false
    }
  }
  await tick()
}

function stopPoll() {
  if (pollerTimer != null) { window.clearTimeout(pollerTimer); pollerTimer = null }
}

async function share() {
  if (!current.value) return
  try {
    const r = await codeAnalysisApi.share(current.value.id)
    const url = `${location.origin}/code-analysis/shared/${r.shareToken}`
    await navigator.clipboard.writeText(url)
    ElMessage.success('分享链接已复制到剪贴板')
  } catch (e: any) { ElMessage.error('生成分享失败：' + e?.message) }
}

async function favorite() {
  if (!current.value) return
  await codeAnalysisApi.toggleFavorite(current.value.id)
  current.value.isFavorite = current.value.isFavorite ? 0 : 1
  ElMessage.success(current.value.isFavorite ? '已收藏' : '已取消收藏')
}

async function doExport(format: 'markdown' | 'html' | 'pdf') {
  if (!current.value) return
  exporting.value = true
  try {
    if (format === 'pdf') {
      // 调用浏览器原生打印 —— print.css 自动隐藏侧栏/左侧表单/按钮；
      // 用户在打印对话框里选"另存为 PDF"即可，Mermaid/样式 100% 保真
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

const topTechs = computed(() => current.value?.techStack?.slice(0, 8) || [])
const totalLines = computed(() =>
  (current.value?.languageStats || []).reduce((a, b) => a + (b.totalLines || 0), 0))

/** 技术栈 chip 颜色循环：6 种渐变轮换 */
const CHIP_PALETTE = ['is-brand', 'is-info', 'is-success', 'is-warning', 'is-danger', 'is-ocean']
function techChipColor(i: number): string {
  return CHIP_PALETTE[i % CHIP_PALETTE.length]
}
/** 语言占比条颜色循环 */
const LANG_PALETTE = ['is-c-brand', 'is-c-sky', 'is-c-sunset', 'is-c-forest', 'is-c-rose', 'is-c-ocean']
function langFillColor(i: number): string {
  return LANG_PALETTE[i % LANG_PALETTE.length]
}

onMounted(() => {
  loadBookmarks()
  // 从 URL 查询参数预填（来自凭证页的"用此凭证/仓库分析"跳转）
  const q = route.query
  if (typeof q.git === 'string' && q.git) {
    form.gitUrl = q.git
    pickerValue.gitUrl = q.git
  }
  if (typeof q.branch === 'string' && q.branch) {
    form.branch = q.branch
    pickerValue.branch = q.branch
  }
})
onUnmounted(() => { stopPoll(); stopElapsedTimer(); stopFactRotate() })
</script>

<template>
  <div class="project-overview">
    <!-- 左栏：表单 + 收藏 -->
    <div class="left-panel">
      <div class="panel-card">
        <div class="panel-title"><el-icon><Reading /></el-icon>项目概览分析</div>
        <div class="hint">输入 Git 仓库地址，AI 自动生成项目介绍、技术栈、模块分层与关键文件导读。</div>

        <el-form label-position="top" @submit.prevent>
          <el-form-item label="选择要分析的仓库">
            <RepoPicker :model-value="pickerValue" @update:model-value="onPickerChange" :show-branch="true" />
          </el-form-item>

          <!-- 自定义分析诉求 -->
          <el-form-item>
            <template #label>
              <div class="req-label">
                <span class="req-label-main">
                  <el-icon><ChatLineRound /></el-icon>
                  自定义分析诉求
                </span>
                <span class="req-label-hint">可选 · 越详细 AI 写得越深入</span>
              </div>
            </template>
            <el-input
              v-model="form.userRequirements"
              type="textarea"
              :rows="4"
              resize="vertical"
              maxlength="8000"
              show-word-limit
              placeholder="例：请详细说明评估中心的打分算法；重点分析多 Agent 协作流程；列出所有二次开发扩展点 …&#10;写得越具体，AI 的分析就越有针对性、越深入。"
            />
            <div class="req-examples">
              <span class="req-examples-label">💡 快速填入示例：</span>
              <button v-for="(ex, i) in REQ_EXAMPLES" :key="ex.label"
                      type="button" class="req-example-chip" @click="fillExample(i)">
                {{ ex.label }}
              </button>
            </div>
          </el-form-item>

          <div class="sub-hint" style="margin-bottom: 12px;">
            Map-Reduce 全量扫描：逐模块读全部源码 → 汇总项目全景
          </div>
          <el-button type="primary" :loading="loading" size="large" class="submit-btn" @click="start">
            <el-icon v-if="!loading" style="margin-right:6px"><MagicStick /></el-icon>
            开始分析
          </el-button>
        </el-form>
      </div>

      <div v-if="bookmarks.length" class="panel-card">
        <div class="panel-title"><el-icon><StarFilled /></el-icon>我的收藏</div>
        <div v-for="b in bookmarks" :key="b.id" class="bookmark-item" @click="fillFromBookmark(b)">
          <div class="bm-name">{{ b.nickname }}</div>
          <div class="bm-url">{{ b.gitUrl }}</div>
        </div>
      </div>

      <!-- 能力概览（固定展示，填充左栏空间） -->
      <div class="capability-card">
        <div class="capability-title">
          <span class="cap-title-icon"><el-icon><MagicStick /></el-icon></span>
          平台能力
        </div>
        <div class="capability-list">
          <div class="cap-item">
            <span class="cap-dot is-brand"></span>
            <div class="cap-item-main">
              <div class="cap-item-name">项目概览</div>
              <div class="cap-item-desc">全量扫描 → 技术栈与架构图</div>
            </div>
          </div>
          <div class="cap-item">
            <span class="cap-dot is-info"></span>
            <div class="cap-item-main">
              <div class="cap-item-name">提交审查</div>
              <div class="cap-item-desc">单次 commit 深度审查</div>
            </div>
          </div>
          <div class="cap-item">
            <span class="cap-dot is-sunset"></span>
            <div class="cap-item-main">
              <div class="cap-item-name">对比分析</div>
              <div class="cap-item-desc">任意两 ref 之间 Diff 审查</div>
            </div>
          </div>
          <div class="cap-item">
            <span class="cap-dot is-success"></span>
            <div class="cap-item-main">
              <div class="cap-item-name">规约库</div>
              <div class="cap-item-desc">321 条阿里黄山版可启停</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 小贴士 -->
      <div class="tip-card">
        <el-icon class="tip-icon"><InfoFilled /></el-icon>
        <div class="tip-text">
          <strong>首次使用？</strong>
          先去<el-link type="primary" @click="router.push('/code-analysis/credentials')">管理凭证</el-link>添加 Git 访问令牌，即可分析私有仓库。
        </div>
      </div>
    </div>

    <!-- 右栏：进度 + 结果 -->
    <div class="right-panel">
      <div v-if="!current" class="placeholder">
        <!-- 背景装饰 -->
        <div class="ph-bg-glow ph-bg-glow-1"></div>
        <div class="ph-bg-glow ph-bg-glow-2"></div>
        <div class="ph-bg-grid"></div>

        <!-- 核心内容 -->
        <div class="ph-hero">
          <div class="ph-icon-wrap">
            <span class="ph-ring ph-ring-1"></span>
            <span class="ph-ring ph-ring-2"></span>
            <span class="ph-ring ph-ring-3"></span>
            <el-icon class="ph-icon"><Reading /></el-icon>
          </div>
          <div class="ph-title">等待 AI 开始分析</div>
          <div class="ph-desc">选择仓库凭证 → 点击「开始分析」，片刻即得完整项目画像</div>
        </div>

        <!-- 功能特性 -->
        <div class="ph-features">
          <div class="ph-feature">
            <div class="ph-feature-icon is-brand"><el-icon><MagicStick /></el-icon></div>
            <div class="ph-feature-title">AI 智能识别</div>
            <div class="ph-feature-desc">技术栈 · 模块分层 · 语言占比</div>
          </div>
          <div class="ph-feature">
            <div class="ph-feature-icon is-info"><el-icon><Connection /></el-icon></div>
            <div class="ph-feature-title">架构可视化</div>
            <div class="ph-feature-desc">自动生成核心流程图</div>
          </div>
          <div class="ph-feature">
            <div class="ph-feature-icon is-success"><el-icon><Reading /></el-icon></div>
            <div class="ph-feature-title">深度解读</div>
            <div class="ph-feature-desc">Map-Reduce 全量扫描</div>
          </div>
        </div>

        <!-- 快速开始 3 步 -->
        <div class="ph-steps">
          <div class="ph-step">
            <div class="ph-step-num">1</div>
            <div class="ph-step-text">选凭证</div>
          </div>
          <div class="ph-step-line"></div>
          <div class="ph-step">
            <div class="ph-step-num">2</div>
            <div class="ph-step-text">开始分析</div>
          </div>
          <div class="ph-step-line"></div>
          <div class="ph-step">
            <div class="ph-step-num">3</div>
            <div class="ph-step-text">查看结果</div>
          </div>
        </div>
      </div>

      <!-- 进度卡（丰富版：计时器 + Token 实时统计 + Pipeline + 日志 + 轮播提示） -->
      <div v-else-if="current.status !== 'SUCCESS' && current.status !== 'FAILED'" class="progress-card">
        <!-- 背景光斑 -->
        <div class="pc-bg-glow pc-bg-glow-1"></div>
        <div class="pc-bg-glow pc-bg-glow-2"></div>

        <!-- Hero：大号阶段名 + 脉动 loading -->
        <div class="pc-hero">
          <div class="pc-icon-wrap">
            <span class="pc-ring pc-ring-1"></span>
            <span class="pc-ring pc-ring-2"></span>
            <el-icon class="spin"><Loading /></el-icon>
          </div>
          <div class="pc-stage-name">{{ STAGE_LABEL[current.stage || 'INIT'] || current.stage }}</div>
          <div class="pc-stage-msg">{{ current.progressMessage || '正在处理...' }}</div>
        </div>

        <!-- 渐变进度条 + 百分比 -->
        <div class="pc-progress-wrap">
          <el-progress :percentage="current.progressPercent || 0"
                       :stroke-width="10" :show-text="false"
                       :color="[{color: '#667eea', percentage: 50}, {color: '#a855f7', percentage: 100}]" />
          <div class="pc-progress-meta">
            <span class="pc-progress-pct">{{ current.progressPercent || 0 }}%</span>
            <span class="pc-progress-stage">阶段 {{ Math.max(1, stageIndex(current.stage) + 1) }} / 5</span>
          </div>
        </div>

        <!-- 4 个实时指标卡 -->
        <div class="pc-stats">
          <div class="pc-stat">
            <div class="pc-stat-icon is-info"><el-icon><Timer /></el-icon></div>
            <div class="pc-stat-body">
              <div class="pc-stat-value pc-stat-time">{{ elapsedStr }}</div>
              <div class="pc-stat-label">已耗时</div>
            </div>
          </div>
          <div class="pc-stat">
            <div class="pc-stat-icon is-brand"><el-icon><Cpu /></el-icon></div>
            <div class="pc-stat-body">
              <div class="pc-stat-value">{{ fmtTokens(liveTokens.sum) }}</div>
              <div class="pc-stat-label">Token · 进 {{ fmtTokens(liveTokens.prompt) }} / 出 {{ fmtTokens(liveTokens.completion) }}</div>
            </div>
          </div>
          <div class="pc-stat">
            <div class="pc-stat-icon is-success"><el-icon><ChatLineRound /></el-icon></div>
            <div class="pc-stat-body">
              <div class="pc-stat-value">{{ liveTokens.count }}</div>
              <div class="pc-stat-label">LLM 调用次数</div>
            </div>
          </div>
          <div class="pc-stat">
            <div class="pc-stat-icon is-sunset"><el-icon><PieChart /></el-icon></div>
            <div class="pc-stat-body">
              <div class="pc-stat-value pc-stat-estimate">{{ estimatedRemainStr }}</div>
              <div class="pc-stat-label">预计剩余</div>
            </div>
          </div>
        </div>

        <!-- 阶段管线可视化 -->
        <div class="pc-pipeline-wrap">
          <div class="pc-pipeline-title">
            <el-icon><Position /></el-icon>
            分析管线
          </div>
          <div class="pc-pipeline">
            <div v-for="st in pipelineStages" :key="st.key" class="pl-step"
                 :class="{ done: stageIndex(current.stage) > st.idx, active: stageIndex(current.stage) === st.idx }">
              <div class="pl-dot"><el-icon><component :is="st.icon" /></el-icon></div>
              <div class="pl-label">{{ st.label }}</div>
            </div>
          </div>
        </div>

        <!-- 实时 LLM 调用日志 -->
        <div v-if="liveCalls.length > 0" class="pc-live-log">
          <div class="pc-live-log-title">
            <el-icon><Monitor /></el-icon>
            实时调用日志
            <span class="pc-live-log-badge">{{ liveCalls.length }} 条</span>
          </div>
          <div class="pc-live-list">
            <div v-for="l in liveCalls.slice().reverse().slice(0, 6)" :key="l.id" class="pc-live-item"
                 :class="{ 'is-fail': !l.success }">
              <span class="pc-live-status">
                <el-icon v-if="l.success"><CircleCheck /></el-icon>
                <el-icon v-else><CircleCloseFilled /></el-icon>
              </span>
              <span class="pc-live-stage" :title="l.stageLabel || l.stage">{{ l.stageLabel || l.stage }}</span>
              <span class="pc-live-model">{{ l.model }}</span>
              <span class="pc-live-tokens">{{ fmtTokens(l.totalTokens) }} tk</span>
              <span class="pc-live-latency">{{ fmtLatency(l.latencyMs) }}</span>
            </div>
          </div>
        </div>

        <!-- 你知道吗：每 5.5s 轮播 -->
        <div class="pc-fact">
          <el-icon class="pc-fact-icon"><MagicStick /></el-icon>
          <div class="pc-fact-text">
            <strong>💡 你知道吗：</strong>
            <transition name="fact-fade" mode="out-in">
              <span :key="factIndex">{{ FACTS[factIndex] }}</span>
            </transition>
          </div>
        </div>
      </div>

      <!-- 失败 -->
      <div v-else-if="current.status === 'FAILED'" class="error-card">
        <div class="error-icon-wrap"><el-icon><CircleCloseFilled /></el-icon></div>
        <div class="error-title">分析失败</div>
        <div class="error-msg">{{ current.errorMsg }}</div>
        <el-button type="primary" @click="current = undefined">重试</el-button>
      </div>

      <!-- 成功 -->
      <div v-else class="result-card">
        <!-- Hero 头部：渐变背景 + 仓库信息 + 操作 -->
        <div class="result-hero">
          <div class="hero-left">
            <div class="hero-type-chip"><el-icon><Reading /></el-icon>项目概览</div>
            <div class="hero-repo">{{ current.gitUrl }}</div>
            <div class="hero-stats">
              <span class="hero-stat"><el-icon><Connection /></el-icon>{{ current.branch || 'main' }}</span>
              <span class="hero-stat"><el-icon><Timer /></el-icon>{{ ((current.durationMs || 0) / 1000).toFixed(1) }}s</span>
              <span class="hero-stat" v-if="current.llmTokensUsed"><el-icon><Cpu /></el-icon>{{ current.llmTokensUsed.toLocaleString() }} tokens</span>
            </div>
          </div>
          <div class="actions">
            <el-button size="small" :class="{ 'is-favorite': current.isFavorite }" @click="favorite">
              <el-icon><StarFilled v-if="current.isFavorite" /><Star v-else /></el-icon>
              {{ current.isFavorite ? '已收藏' : '收藏' }}
            </el-button>
            <el-button size="small" @click="share"><el-icon><Share /></el-icon>分享</el-button>
            <el-dropdown trigger="click" @command="doExport">
              <el-button size="small" :loading="exporting">
                <el-icon><Download /></el-icon>导出
                <el-icon style="margin-left:4px"><ArrowDown /></el-icon>
              </el-button>
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

        <!-- 技术栈 / 语言占比 -->
        <div class="stats-row">
          <div class="stats-box">
            <div class="stats-title"><span class="stats-icon is-brand"><el-icon><Tools /></el-icon></span>技术栈</div>
            <div class="tech-chips">
              <span v-for="(t, i) in topTechs" :key="t" class="tech-chip" :class="techChipColor(i)">{{ t }}</span>
              <span v-if="!topTechs.length" class="muted">（无）</span>
            </div>
          </div>
          <div class="stats-box">
            <div class="stats-title"><span class="stats-icon is-info"><el-icon><DataLine /></el-icon></span>语言占比 <small>· {{ totalLines.toLocaleString() }} 行</small></div>
            <div class="lang-bars">
              <div v-for="(l, i) in (current.languageStats || []).slice(0, 6)" :key="l.language" class="lang-row">
                <span class="lang-name">{{ l.language }}</span>
                <div class="lang-track">
                  <div class="lang-fill" :class="langFillColor(i)" :style="{ width: l.percent + '%' }" />
                </div>
                <span class="lang-percent">{{ l.percent.toFixed(1) }}%</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 用户当初的分析诉求 -->
        <div v-if="current.userRequirements" class="req-display">
          <div class="req-display-title"><el-icon><ChatLineRound /></el-icon>本次分析的自定义诉求</div>
          <div class="req-display-body">{{ current.userRequirements }}</div>
        </div>

        <!-- Mermaid 核心流程图（SVG 渲染） -->
        <div v-if="current.mermaidCode" class="mermaid-box">
          <div class="mermaid-title">🧭 核心流程图</div>
          <div v-if="mermaidError" class="mermaid-error">
            ⚠️ Mermaid 语法解析失败：{{ mermaidError }}
            <details style="margin-top: 8px">
              <summary>查看原始代码</summary>
              <pre class="mermaid-code">{{ current.mermaidCode }}</pre>
            </details>
          </div>
          <div v-else-if="mermaidSvg" class="mermaid-svg" v-html="mermaidSvg" />
          <div v-else class="mermaid-loading">渲染中...</div>
        </div>

        <!-- Markdown 长文 -->
        <div class="markdown-body" v-html="summaryHtml" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.project-overview {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: var(--sf-space-4);
  padding: var(--sf-space-5);
  height: 100%;
  animation: sf-fade-in var(--sf-transition-base);
}
.left-panel { display: flex; flex-direction: column; gap: var(--sf-space-4); }
.right-panel { min-width: 0; }

/* ========== 左栏表单卡 ========== */
.panel-card {
  background: var(--sf-surface-gradient);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-5);
  box-shadow: var(--sf-shadow-sm);
  transition: box-shadow var(--sf-transition-base);
}
.panel-card:hover { box-shadow: var(--sf-shadow-md); }
.panel-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: var(--sf-space-2);
  color: var(--sf-text-primary);
  display: flex;
  align-items: center;
  gap: var(--sf-space-2);
}
.panel-title :deep(.el-icon) {
  color: var(--sf-primary);
  font-size: 18px;
}
.hint {
  color: var(--sf-text-tertiary);
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: var(--sf-space-4);
}
.sub-hint {
  margin-left: var(--sf-space-3);
  color: var(--sf-text-muted);
  font-size: 12px;
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

/* ========== 自定义分析诉求输入 ========== */
.req-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sf-space-2);
  width: 100%;
}
.req-label-main {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--sf-text-primary);
  font-size: 14px;
}
.req-label-main :deep(.el-icon) { color: var(--sf-primary); }
.req-label-hint {
  color: var(--sf-text-muted);
  font-size: 12px;
  font-weight: 400;
}
.req-examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: var(--sf-space-2);
}
.req-examples-label {
  color: var(--sf-text-tertiary);
  font-size: 12px;
}
.req-example-chip {
  padding: 4px 10px;
  background: rgba(102, 126, 234, 0.08);
  border: 1px solid rgba(102, 126, 234, 0.2);
  color: var(--sf-primary-dark);
  border-radius: var(--sf-radius-full);
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--sf-transition-fast);
  font-family: inherit;
}
.req-example-chip:hover {
  background: rgba(102, 126, 234, 0.18);
  border-color: var(--sf-primary);
  transform: translateY(-1px);
}

.bookmark-item {
  padding: var(--sf-space-3);
  border-radius: var(--sf-radius-sm);
  cursor: pointer;
  transition: all var(--sf-transition-fast);
  margin-bottom: var(--sf-space-1);
  border: 1px solid transparent;
}
.bookmark-item:hover {
  background: var(--sf-surface-hover);
  border-color: var(--sf-border-light);
  transform: translateX(2px);
}
.bm-name {
  font-weight: 500;
  color: var(--sf-text-primary);
  font-size: 13px;
}
.bm-url {
  color: var(--sf-text-muted);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'SF Mono', Menlo, monospace;
}

/* ========== 能力概览卡（左栏填充） ========== */
.capability-card {
  position: relative;
  background: var(--sf-surface-gradient);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-4) var(--sf-space-5);
  box-shadow: var(--sf-shadow-sm);
  overflow: hidden;
}
.capability-card::before {
  content: '';
  position: absolute;
  top: -40%;
  right: -20%;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(102,126,234,0.08), transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}
.capability-title {
  position: relative;
  font-size: 14px;
  font-weight: 600;
  color: var(--sf-text-primary);
  margin-bottom: var(--sf-space-3);
  display: flex;
  align-items: center;
  gap: var(--sf-space-2);
}
.cap-title-icon {
  width: 24px;
  height: 24px;
  border-radius: var(--sf-radius-sm);
  background: var(--sf-gradient-aurora);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  box-shadow: var(--sf-shadow-brand);
}
.capability-list {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--sf-space-2);
}
.cap-item {
  display: flex;
  align-items: center;
  gap: var(--sf-space-3);
  padding: var(--sf-space-2);
  border-radius: var(--sf-radius-sm);
  transition: all var(--sf-transition-fast);
}
.cap-item:hover {
  background: var(--sf-surface-hover);
  transform: translateX(2px);
}
.cap-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 0 3px rgba(255,255,255,0.8);
}
.cap-dot.is-brand   { background: var(--sf-primary);   box-shadow: 0 0 0 3px rgba(102,126,234,0.15); }
.cap-dot.is-info    { background: var(--sf-info);      box-shadow: 0 0 0 3px rgba(59,130,246,0.15); }
.cap-dot.is-sunset  { background: #f59e0b;             box-shadow: 0 0 0 3px rgba(245,158,11,0.15); }
.cap-dot.is-success { background: var(--sf-success);   box-shadow: 0 0 0 3px rgba(16,185,129,0.15); }
.cap-item-main { min-width: 0; flex: 1; }
.cap-item-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--sf-text-primary);
  line-height: 1.3;
}
.cap-item-desc {
  font-size: 11px;
  color: var(--sf-text-tertiary);
  line-height: 1.4;
  margin-top: 2px;
}

/* ========== 小贴士条 ========== */
.tip-card {
  background: linear-gradient(135deg, rgba(102,126,234,0.05) 0%, rgba(118,75,162,0.05) 100%);
  border: 1px solid rgba(102,126,234,0.18);
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-3) var(--sf-space-4);
  display: flex;
  align-items: flex-start;
  gap: var(--sf-space-3);
}
.tip-icon {
  color: var(--sf-primary);
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 2px;
}
.tip-text {
  color: var(--sf-text-secondary);
  font-size: 12.5px;
  line-height: 1.6;
}
.tip-text strong {
  color: var(--sf-text-primary);
  font-weight: 600;
  margin-right: 2px;
}
.tip-text :deep(.el-link) {
  font-size: 12.5px !important;
  margin: 0 2px;
  vertical-align: baseline;
}

/* ========== 右栏占位（丰富版 empty state） ========== */
.placeholder {
  position: relative;
  background: linear-gradient(155deg, #fafbff 0%, #f3f4ff 100%);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sf-space-5);
  box-shadow: var(--sf-shadow-sm);
  overflow: hidden;
  padding: var(--sf-space-6);
}

/* 背景装饰：两个渐变光斑 + 点阵网格 */
.ph-bg-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(60px);
  opacity: 0.5;
}
.ph-bg-glow-1 {
  width: 420px; height: 420px;
  top: -120px; right: -80px;
  background: radial-gradient(circle, #a5b4fc 0%, transparent 70%);
  animation: sf-float 8s ease-in-out infinite;
}
.ph-bg-glow-2 {
  width: 340px; height: 340px;
  bottom: -100px; left: -60px;
  background: radial-gradient(circle, #f9a8d4 0%, transparent 70%);
  animation: sf-float 10s ease-in-out infinite reverse;
}
.ph-bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle at 1px 1px, rgba(102, 126, 234, 0.15) 1px, transparent 0);
  background-size: 20px 20px;
  mask-image: radial-gradient(ellipse at center, #000 30%, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse at center, #000 30%, transparent 70%);
  pointer-events: none;
  opacity: 0.4;
}

/* Hero 区 */
.ph-hero {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sf-space-3);
}
.ph-icon-wrap {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: var(--sf-gradient-aurora);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--sf-shadow-glow-brand), 0 0 0 8px rgba(255,255,255,0.8);
  animation: sf-float 3.5s ease-in-out infinite;
}
.ph-icon {
  font-size: 48px;
  color: #fff;
  z-index: 2;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,0.15));
}
/* 多层脉动光环 */
.ph-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid rgba(102, 126, 234, 0.35);
  animation: ph-ring-pulse 2.4s cubic-bezier(0.4, 0, 0.2, 1) infinite;
  pointer-events: none;
}
.ph-ring-1 { animation-delay: 0s; }
.ph-ring-2 { animation-delay: 0.8s; }
.ph-ring-3 { animation-delay: 1.6s; }
@keyframes ph-ring-pulse {
  0%   { transform: scale(1);    opacity: 0.7; }
  100% { transform: scale(1.8);  opacity: 0;   }
}

.ph-title {
  font-size: 22px;
  font-weight: 700;
  background: var(--sf-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  letter-spacing: -0.02em;
}
.ph-desc {
  color: var(--sf-text-tertiary);
  font-size: 13px;
  text-align: center;
  max-width: 340px;
  line-height: 1.7;
}

/* 功能特性三连卡 */
.ph-features {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sf-space-3);
  width: 100%;
  max-width: 560px;
}
.ph-feature {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-4) var(--sf-space-3);
  text-align: center;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.08);
  transition: all var(--sf-transition-base);
}
.ph-feature:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 24px rgba(102, 126, 234, 0.15);
  border-color: rgba(102, 126, 234, 0.3);
}
.ph-feature-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--sf-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto var(--sf-space-2);
  color: #fff;
  font-size: 20px;
  box-shadow: var(--sf-shadow-sm);
}
.ph-feature-icon.is-brand   { background: var(--sf-gradient-aurora); }
.ph-feature-icon.is-info    { background: var(--sf-info-gradient); }
.ph-feature-icon.is-success { background: var(--sf-success-gradient); }
.ph-feature-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sf-text-primary);
  margin-bottom: 4px;
}
.ph-feature-desc {
  font-size: 11px;
  color: var(--sf-text-tertiary);
  line-height: 1.5;
}

/* 快速开始 3 步 */
.ph-steps {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: var(--sf-space-3);
}
.ph-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.ph-step-num {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  border: 1.5px solid rgba(102, 126, 234, 0.3);
  color: var(--sf-primary-dark);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.12);
}
.ph-step:first-child .ph-step-num {
  background: var(--sf-gradient);
  color: #fff;
  border-color: transparent;
  box-shadow: var(--sf-shadow-brand);
  animation: sf-glow-pulse 2s ease-in-out infinite;
}
.ph-step-text {
  font-size: 11px;
  color: var(--sf-text-tertiary);
  font-weight: 500;
}
.ph-step-line {
  flex: 0 0 32px;
  height: 1.5px;
  background: linear-gradient(90deg, rgba(102, 126, 234, 0.1), rgba(102, 126, 234, 0.3), rgba(102, 126, 234, 0.1));
  position: relative;
  align-self: center;
  margin-bottom: 16px;
}

/* ========== 进度卡（丰富版） ========== */
.progress-card {
  position: relative;
  background: var(--sf-surface-gradient);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-6);
  box-shadow: var(--sf-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: var(--sf-space-5);
  overflow: hidden;
}
/* 背景光斑 */
.pc-bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.4;
  pointer-events: none;
}
.pc-bg-glow-1 {
  width: 320px; height: 320px;
  top: -120px; right: -40px;
  background: radial-gradient(circle, rgba(165, 180, 252, 0.8) 0%, transparent 70%);
  animation: sf-float 9s ease-in-out infinite;
}
.pc-bg-glow-2 {
  width: 280px; height: 280px;
  bottom: -100px; left: 30%;
  background: radial-gradient(circle, rgba(249, 168, 212, 0.7) 0%, transparent 70%);
  animation: sf-float 11s ease-in-out infinite reverse;
}

/* Hero 区 */
.pc-hero {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sf-space-2);
}
.pc-icon-wrap {
  position: relative;
  width: 76px;
  height: 76px;
  border-radius: 50%;
  background: var(--sf-gradient-aurora);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--sf-shadow-glow-brand);
  margin-bottom: var(--sf-space-2);
}
.pc-icon-wrap .spin {
  color: #fff;
  font-size: 32px;
  animation: spin 1s linear infinite;
  z-index: 2;
}
.pc-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid rgba(102, 126, 234, 0.45);
  animation: pc-ring-pulse 2.2s cubic-bezier(0.4, 0, 0.2, 1) infinite;
  pointer-events: none;
}
.pc-ring-1 { animation-delay: 0s; }
.pc-ring-2 { animation-delay: 0.8s; }
@keyframes pc-ring-pulse {
  0%   { transform: scale(1);   opacity: 0.7; }
  100% { transform: scale(1.8); opacity: 0;   }
}
@keyframes spin { to { transform: rotate(360deg); } }

.pc-stage-name {
  font-size: 22px;
  font-weight: 700;
  background: var(--sf-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  letter-spacing: -0.01em;
}
.pc-stage-msg {
  color: var(--sf-text-tertiary);
  font-size: 13px;
  text-align: center;
  max-width: 80%;
}

/* 渐变进度条 */
.pc-progress-wrap {
  position: relative;
  z-index: 1;
}
.pc-progress-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 12px;
  color: var(--sf-text-tertiary);
}
.pc-progress-pct {
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  background: var(--sf-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.pc-progress-stage {
  font-variant-numeric: tabular-nums;
}

/* 4 个实时指标 */
.pc-stats {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sf-space-3);
}
@media (max-width: 900px) { .pc-stats { grid-template-columns: 1fr 1fr; } }
.pc-stat {
  display: flex;
  align-items: center;
  gap: var(--sf-space-3);
  padding: var(--sf-space-3);
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-md);
  transition: all var(--sf-transition-base);
}
.pc-stat:hover {
  transform: translateY(-2px);
  box-shadow: var(--sf-shadow-md);
  border-color: var(--sf-primary-light);
}
.pc-stat-icon {
  width: 38px;
  height: 38px;
  border-radius: var(--sf-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  flex-shrink: 0;
  box-shadow: var(--sf-shadow-sm);
}
.pc-stat-icon.is-info    { background: var(--sf-info-gradient); }
.pc-stat-icon.is-brand   { background: var(--sf-gradient-aurora); }
.pc-stat-icon.is-success { background: var(--sf-success-gradient); }
.pc-stat-icon.is-sunset  { background: var(--sf-gradient-sunset); }
.pc-stat-body { min-width: 0; flex: 1; }
.pc-stat-value {
  font-size: 20px;
  font-weight: 800;
  color: var(--sf-text-primary);
  line-height: 1;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.pc-stat-time {
  font-family: 'SF Mono', 'Menlo', monospace;
  color: var(--sf-primary-dark);
}
.pc-stat-estimate { font-size: 14px; }
.pc-stat-label {
  color: var(--sf-text-tertiary);
  font-size: 11.5px;
  margin-top: 3px;
  line-height: 1.4;
}

/* 阶段管线 */
.pc-pipeline-wrap {
  position: relative;
  z-index: 1;
}
.pc-pipeline-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sf-text-secondary);
  margin-bottom: var(--sf-space-3);
  display: flex;
  align-items: center;
  gap: 6px;
}
.pc-pipeline-title :deep(.el-icon) { color: var(--sf-primary); }
.pc-pipeline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sf-space-2) 0;
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
  top: 17px;
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
  width: 34px;
  height: 34px;
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

/* 实时 LLM 调用日志 */
.pc-live-log {
  position: relative;
  z-index: 1;
  background: #0f172a;
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-3) var(--sf-space-4);
  color: #cbd5e1;
}
.pc-live-log-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: var(--sf-space-2);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.pc-live-log-title :deep(.el-icon) { color: #60a5fa; }
.pc-live-log-badge {
  margin-left: auto;
  padding: 2px 8px;
  background: rgba(96, 165, 250, 0.15);
  color: #60a5fa;
  border-radius: var(--sf-radius-full);
  font-size: 10.5px;
  font-weight: 700;
  text-transform: none;
  letter-spacing: 0;
}
.pc-live-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 12px;
}
.pc-live-item {
  display: grid;
  grid-template-columns: 20px 1fr 90px 70px 60px;
  gap: var(--sf-space-2);
  padding: 4px 6px;
  align-items: center;
  border-radius: var(--sf-radius-xs);
  transition: background var(--sf-transition-fast);
}
.pc-live-item:hover { background: rgba(255, 255, 255, 0.05); }
.pc-live-item.is-fail .pc-live-status { color: #f87171; }
.pc-live-status {
  color: #34d399;
  display: flex;
  align-items: center;
}
.pc-live-stage {
  color: #e2e8f0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pc-live-model {
  color: #a78bfa;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pc-live-tokens {
  color: #fbbf24;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.pc-live-latency {
  color: #64748b;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* 你知道吗轮播 */
.pc-fact {
  position: relative;
  z-index: 1;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.06) 0%, rgba(118, 75, 162, 0.06) 100%);
  border: 1px solid rgba(102, 126, 234, 0.15);
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-3) var(--sf-space-4);
  display: flex;
  align-items: flex-start;
  gap: var(--sf-space-3);
}
.pc-fact-icon {
  color: var(--sf-primary);
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 2px;
  animation: sf-float 3s ease-in-out infinite;
}
.pc-fact-text {
  color: var(--sf-text-secondary);
  font-size: 13px;
  line-height: 1.7;
  min-height: 22px;
}
.pc-fact-text strong {
  color: var(--sf-text-primary);
  font-weight: 600;
  margin-right: 4px;
}
/* "你知道吗" 文字轮播淡入淡出 */
.fact-fade-enter-active, .fact-fade-leave-active { transition: opacity 0.4s; }
.fact-fade-enter-from, .fact-fade-leave-to { opacity: 0; }

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
  box-shadow: var(--sf-shadow-glow-danger);
}
.error-icon-wrap {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--sf-danger-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 32px;
  box-shadow: var(--sf-shadow-glow-danger);
  margin-bottom: var(--sf-space-2);
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
  max-width: 520px;
}

/* ========== 结果卡 ========== */
.result-card {
  background: var(--sf-surface);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  box-shadow: var(--sf-shadow-sm);
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  overflow-x: hidden;
}

/* Hero：渐变顶栏 */
.result-hero {
  position: relative;
  background: var(--sf-gradient-aurora);
  padding: var(--sf-space-5) var(--sf-space-6);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--sf-space-4);
  overflow: hidden;
  color: #fff;
}
.result-hero::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(255,255,255,0.2), transparent 60%);
  border-radius: 50%;
  pointer-events: none;
}
.hero-left {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
}
.hero-type-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: rgba(255,255,255,0.2);
  border: 1px solid rgba(255,255,255,0.3);
  border-radius: var(--sf-radius-full);
  font-size: 12px;
  font-weight: 600;
  backdrop-filter: blur(6px);
  margin-bottom: var(--sf-space-3);
}
.hero-repo {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: var(--sf-space-2);
  word-break: break-all;
  font-family: 'SF Mono', Menlo, monospace;
}
.hero-stats {
  display: flex;
  gap: var(--sf-space-4);
  font-size: 13px;
  opacity: 0.95;
  flex-wrap: wrap;
}
.hero-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-variant-numeric: tabular-nums;
}
.hero-stat :deep(.el-icon) {
  font-size: 14px;
}

.actions {
  position: relative;
  z-index: 1;
  display: flex;
  gap: var(--sf-space-2);
  flex-shrink: 0;
}
.actions :deep(.el-button) {
  background: rgba(255,255,255,0.2) !important;
  color: #fff !important;
  border: 1px solid rgba(255,255,255,0.3) !important;
  backdrop-filter: blur(8px);
  font-weight: 500 !important;
}
.actions :deep(.el-button:hover) {
  background: rgba(255,255,255,0.3) !important;
  transform: translateY(-1px);
}
.actions :deep(.el-button.is-favorite) {
  background: rgba(251, 191, 36, 0.3) !important;
  border-color: rgba(251, 191, 36, 0.5) !important;
}

/* ========== 统计行（技术栈 / 语言占比） ========== */
.stats-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sf-space-4);
  padding: var(--sf-space-5);
}
.stats-box {
  background: var(--sf-surface-gradient-soft);
  border: 1px solid var(--sf-border-light);
  padding: var(--sf-space-4);
  border-radius: var(--sf-radius-md);
  transition: box-shadow var(--sf-transition-base);
}
.stats-box:hover { box-shadow: var(--sf-shadow-sm); }
.stats-title {
  font-size: 13px;
  color: var(--sf-text-secondary);
  font-weight: 600;
  margin-bottom: var(--sf-space-3);
  display: flex;
  align-items: center;
  gap: var(--sf-space-2);
}
.stats-title small { color: var(--sf-text-muted); font-weight: 400; font-size: 12px; }
.stats-icon {
  width: 28px;
  height: 28px;
  border-radius: var(--sf-radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  flex-shrink: 0;
}
.stats-icon.is-brand { background: var(--sf-gradient-aurora); }
.stats-icon.is-info  { background: var(--sf-info-gradient); }

.tech-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tech-chip {
  padding: 4px 12px;
  border-radius: var(--sf-radius-full);
  font-size: 12px;
  font-weight: 500;
  transition: all var(--sf-transition-fast);
  border: 1px solid transparent;
}
.tech-chip:hover { transform: translateY(-1px); box-shadow: var(--sf-shadow-xs); }
.tech-chip.is-brand   { background: rgba(102, 126, 234, 0.1); color: var(--sf-primary-dark); border-color: rgba(102, 126, 234, 0.2); }
.tech-chip.is-info    { background: var(--sf-info-bg); color: var(--sf-info); border-color: rgba(59, 130, 246, 0.2); }
.tech-chip.is-success { background: var(--sf-success-bg); color: var(--sf-success); border-color: rgba(16, 185, 129, 0.2); }
.tech-chip.is-warning { background: var(--sf-warning-bg); color: var(--sf-warning); border-color: rgba(245, 158, 11, 0.2); }
.tech-chip.is-danger  { background: var(--sf-danger-bg); color: var(--sf-danger); border-color: rgba(239, 68, 68, 0.2); }
.tech-chip.is-ocean   { background: #cffafe; color: #0891b2; border-color: #a5f3fc; }

.muted { color: var(--sf-text-muted); font-size: 13px; }

.lang-bars {
  display: flex;
  flex-direction: column;
  gap: var(--sf-space-2);
}
.lang-row {
  display: grid;
  grid-template-columns: 80px 1fr 50px;
  gap: var(--sf-space-2);
  align-items: center;
  font-size: 12px;
}
.lang-name { color: var(--sf-text-secondary); font-weight: 500; }
.lang-track {
  background: var(--sf-border-light);
  height: 8px;
  border-radius: var(--sf-radius-full);
  overflow: hidden;
}
.lang-fill {
  height: 100%;
  border-radius: var(--sf-radius-full);
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 8px rgba(102, 126, 234, 0.2);
}
.lang-fill.is-c-brand  { background: var(--sf-gradient); }
.lang-fill.is-c-sky    { background: linear-gradient(90deg, #60a5fa, #3b82f6); }
.lang-fill.is-c-sunset { background: var(--sf-gradient-sunset); }
.lang-fill.is-c-forest { background: linear-gradient(90deg, #34d399, #059669); }
.lang-fill.is-c-rose   { background: var(--sf-gradient-rose); }
.lang-fill.is-c-ocean  { background: var(--sf-gradient-ocean); }
.lang-percent {
  color: var(--sf-text-tertiary);
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}

/* 结果卡：用户诉求回显 */
.req-display {
  margin: 0 var(--sf-space-5) var(--sf-space-4);
  background: linear-gradient(135deg, rgba(102,126,234,0.05) 0%, rgba(118,75,162,0.05) 100%);
  border: 1px solid rgba(102,126,234,0.2);
  border-radius: var(--sf-radius-md);
  padding: var(--sf-space-3) var(--sf-space-4);
}
.req-display-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--sf-primary-dark);
  margin-bottom: var(--sf-space-2);
}
.req-display-body {
  color: var(--sf-text-primary);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  padding: var(--sf-space-2) var(--sf-space-3);
  background: rgba(255,255,255,0.6);
  border-left: 3px solid var(--sf-primary);
  border-radius: 0 var(--sf-radius-sm) var(--sf-radius-sm) 0;
}

/* ========== Mermaid 图表区 ========== */
.mermaid-box {
  margin: 0 var(--sf-space-5) var(--sf-space-5);
  background: var(--sf-surface-gradient-soft);
  padding: var(--sf-space-5);
  border-radius: var(--sf-radius-md);
  border: 1px solid var(--sf-border-light);
}
.mermaid-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: var(--sf-space-3);
  color: var(--sf-text-secondary);
  display: flex;
  align-items: center;
  gap: var(--sf-space-2);
}
.mermaid-title::before {
  content: '';
  width: 4px;
  height: 14px;
  background: var(--sf-gradient);
  border-radius: var(--sf-radius-full);
}
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
.mermaid-loading {
  color: var(--sf-text-muted);
  text-align: center;
  padding: var(--sf-space-5);
  font-size: 13px;
}
.mermaid-error {
  background: var(--sf-danger-bg);
  color: var(--sf-danger);
  padding: var(--sf-space-3);
  border-radius: var(--sf-radius-sm);
  font-size: 13px;
}
.mermaid-code {
  background: #1e293b;
  color: #e2e8f0;
  padding: var(--sf-space-3);
  border-radius: var(--sf-radius-sm);
  font-size: 12px;
  overflow-x: auto;
  margin-top: 6px;
  max-height: 240px;
  overflow-y: auto;
}

/* ========== Markdown 长文 ========== */
.markdown-body {
  line-height: 1.8;
  color: var(--sf-text-primary);
  padding: 0 var(--sf-space-5) var(--sf-space-5);
}
.markdown-body :deep(h1) {
  font-size: 24px;
  margin: var(--sf-space-5) 0 var(--sf-space-3);
  color: var(--sf-text-primary);
  font-weight: 700;
  background: var(--sf-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  display: inline-block;
}
.markdown-body :deep(h2) {
  font-size: 19px;
  margin: var(--sf-space-5) 0 var(--sf-space-3);
  color: var(--sf-text-primary);
  font-weight: 600;
  padding-bottom: 6px;
  border-bottom: 2px solid var(--sf-border-light);
  display: flex;
  align-items: center;
  gap: var(--sf-space-2);
}
.markdown-body :deep(h2)::before {
  content: '';
  width: 4px;
  height: 18px;
  background: var(--sf-gradient);
  border-radius: var(--sf-radius-full);
  display: inline-block;
}
.markdown-body :deep(h3) {
  font-size: 16px;
  margin: var(--sf-space-4) 0 var(--sf-space-2);
  color: var(--sf-text-secondary);
  font-weight: 600;
}
.markdown-body :deep(p) { margin: var(--sf-space-2) 0; }
.markdown-body :deep(code) {
  background: rgba(102, 126, 234, 0.08);
  padding: 2px 8px;
  border-radius: var(--sf-radius-xs);
  font-size: 0.9em;
  color: var(--sf-primary-dark);
  font-weight: 500;
  border: 1px solid rgba(102, 126, 234, 0.12);
}
.markdown-body :deep(pre) {
  background: #1e293b;
  color: #e2e8f0;
  padding: var(--sf-space-4);
  border-radius: var(--sf-radius-md);
  overflow-x: auto;
  border: 1px solid #334155;
  box-shadow: var(--sf-shadow-md);
}
.markdown-body :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
  border: none;
}
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 24px; }
.markdown-body :deep(li) { margin: var(--sf-space-1) 0; }
.markdown-body :deep(a) {
  color: var(--sf-primary);
  text-decoration: none;
  border-bottom: 1px solid rgba(102, 126, 234, 0.3);
  transition: all var(--sf-transition-fast);
}
.markdown-body :deep(a:hover) {
  color: var(--sf-accent);
  border-bottom-color: var(--sf-accent);
}
.markdown-body :deep(blockquote) {
  border-left: 4px solid var(--sf-primary);
  background: var(--sf-gradient-soft);
  padding: var(--sf-space-3) var(--sf-space-4);
  color: var(--sf-text-secondary);
  margin: var(--sf-space-3) 0;
  border-radius: 0 var(--sf-radius-sm) var(--sf-radius-sm) 0;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: var(--sf-space-3) 0;
  font-size: 13px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 8px 12px;
  border: 1px solid var(--sf-border-light);
  text-align: left;
}
.markdown-body :deep(th) {
  background: var(--sf-bg-subtle);
  font-weight: 600;
  color: var(--sf-text-secondary);
}
.markdown-body :deep(tr:hover td) {
  background: var(--sf-surface-hover);
}

/* ========== 凭证反馈（保留原样式但用变量化） ========== */
.cred-badge { margin-top: 6px; padding: 6px 12px; border-radius: var(--sf-radius-sm); font-size: 12px; line-height: 1.6; }
.cred-badge.cred-ok { background: var(--sf-success-bg); border: 1px solid #bbf7d0; color: var(--sf-success); }
.cred-badge.cred-miss { background: var(--sf-warning-bg); border: 1px solid #fde68a; color: #b45309; }
.cred-badge code { background: rgba(0,0,0,0.06); padding: 1px 5px; border-radius: 3px; font-size: 11px; color: var(--sf-primary-dark); }
.cred-type { margin-left: 6px; padding: 1px 8px; background: rgba(102, 126, 234, 0.1); color: var(--sf-primary-dark); border-radius: var(--sf-radius-full); font-size: 11px; }
.cred-more { color: var(--sf-text-tertiary); margin-left: 6px; font-size: 11px; }
</style>
