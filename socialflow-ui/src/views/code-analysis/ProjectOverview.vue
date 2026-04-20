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
import type { CodeAnalysis, RepoBookmark } from '@/types/codeAnalysis'

const router = useRouter()
const route = useRoute()
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const form = reactive({
  gitUrl: '',
  branch: 'main',
})

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
  ANALYZING: 'LLM 分析', RENDERING: '渲染报告', DONE: '完成',
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
  const tick = async () => {
    try {
      const r = await codeAnalysisApi.get(id)
      current.value = r
      if (r.status === 'SUCCESS' || r.status === 'FAILED') {
        stopPoll()
        loading.value = false
        return
      }
      pollDelayMs = Math.min(Math.ceil(pollDelayMs * 1.25), POLL_MAX_MS)
      pollerTimer = window.setTimeout(tick, pollDelayMs)
    } catch {
      stopPoll()
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
onUnmounted(stopPoll)
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

      <!-- 进度卡 -->
      <div v-else-if="current.status !== 'SUCCESS' && current.status !== 'FAILED'" class="progress-card">
        <div class="progress-title">
          <el-icon class="spin"><Loading /></el-icon>
          {{ STAGE_LABEL[current.stage || 'INIT'] || current.stage }}
        </div>
        <el-progress :percentage="current.progressPercent || 0"
                     :stroke-width="10" :show-text="true"
                     :color="[{color: '#667eea', percentage: 50}, {color: '#764ba2', percentage: 100}]" />
        <div class="progress-msg">{{ current.progressMessage || '正在处理...' }}</div>
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

/* ========== 进度卡 ========== */
.progress-card {
  background: var(--sf-surface-gradient);
  border: 1px solid var(--sf-border-light);
  border-radius: var(--sf-radius-lg);
  padding: var(--sf-space-7);
  box-shadow: var(--sf-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: var(--sf-space-4);
}
.progress-title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: var(--sf-space-2);
  color: var(--sf-text-primary);
}
.spin {
  animation: spin 1s linear infinite;
  color: var(--sf-primary);
  font-size: 20px;
}
@keyframes spin { to { transform: rotate(360deg); } }
.progress-msg {
  color: var(--sf-text-tertiary);
  font-size: 13px;
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
