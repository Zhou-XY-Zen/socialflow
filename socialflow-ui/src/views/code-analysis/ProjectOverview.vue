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
        <div class="panel-title">📖 项目概览分析</div>
        <div class="hint">输入 Git 仓库地址，AI 自动生成项目介绍、技术栈、模块分层与关键文件导读。</div>

        <el-form label-position="top" @submit.prevent>
          <el-form-item label="选择要分析的仓库">
            <RepoPicker :model-value="pickerValue" @update:model-value="onPickerChange" :show-branch="true" />
          </el-form-item>
          <div class="sub-hint" style="margin-bottom: 12px;">
            Map-Reduce 全量扫描：逐模块读全部源码 → 汇总项目全景（无需配置克隆深度）
          </div>
          <el-button type="primary" :loading="loading" size="large" class="submit-btn" @click="start">
            🚀 开始分析
          </el-button>
        </el-form>
      </div>

      <div v-if="bookmarks.length" class="panel-card">
        <div class="panel-title">⭐ 我的收藏</div>
        <div v-for="b in bookmarks" :key="b.id" class="bookmark-item" @click="fillFromBookmark(b)">
          <div class="bm-name">{{ b.nickname }}</div>
          <div class="bm-url">{{ b.gitUrl }}</div>
        </div>
      </div>
    </div>

    <!-- 右栏：进度 + 结果 -->
    <div class="right-panel">
      <div v-if="!current" class="placeholder">
        <div class="ph-icon">📖</div>
        <div class="ph-title">等待分析</div>
        <div class="ph-desc">在左侧填写 Git 地址并点击"开始分析"</div>
      </div>

      <!-- 进度卡 -->
      <div v-else-if="current.status !== 'SUCCESS' && current.status !== 'FAILED'" class="progress-card">
        <div class="progress-title">
          <el-icon class="spin"><Loading /></el-icon>
          {{ STAGE_LABEL[current.stage || 'INIT'] || current.stage }}
        </div>
        <el-progress :percentage="current.progressPercent || 0"
                     :stroke-width="12" status="primary" />
        <div class="progress-msg">{{ current.progressMessage || '正在处理...' }}</div>
      </div>

      <!-- 失败 -->
      <div v-else-if="current.status === 'FAILED'" class="error-card">
        <div class="error-title">❌ 分析失败</div>
        <div class="error-msg">{{ current.errorMsg }}</div>
        <el-button @click="current = undefined">重试</el-button>
      </div>

      <!-- 成功 -->
      <div v-else class="result-card">
        <!-- 顶部摘要 -->
        <div class="summary-header">
          <div>
            <div class="repo-title">{{ current.gitUrl }}</div>
            <div class="meta-row">
              <span>分支: {{ current.branch || 'main' }}</span>
              <span>耗时: {{ ((current.durationMs || 0) / 1000).toFixed(1) }}s</span>
              <span v-if="current.llmTokensUsed">Token: {{ current.llmTokensUsed }}</span>
            </div>
          </div>
          <div class="actions">
            <el-button size="small" @click="favorite">
              {{ current.isFavorite ? '★ 已收藏' : '☆ 收藏' }}
            </el-button>
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

        <!-- 技术栈 / 语言占比 -->
        <div class="stats-row">
          <div class="stats-box">
            <div class="stats-title">🛠️ 技术栈</div>
            <div class="tech-chips">
              <span v-for="t in topTechs" :key="t" class="tech-chip">{{ t }}</span>
              <span v-if="!topTechs.length" class="muted">（无）</span>
            </div>
          </div>
          <div class="stats-box">
            <div class="stats-title">📊 语言占比 ({{ totalLines.toLocaleString() }} 行)</div>
            <div class="lang-bars">
              <div v-for="l in (current.languageStats || []).slice(0, 6)" :key="l.language" class="lang-row">
                <span class="lang-name">{{ l.language }}</span>
                <div class="lang-track">
                  <div class="lang-fill" :style="{ width: l.percent + '%' }" />
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
  gap: 16px;
  padding: 20px;
  height: 100%;
}
.left-panel { display: flex; flex-direction: column; gap: 16px; }
.right-panel { min-width: 0; }

.panel-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.panel-title { font-size: 16px; font-weight: 600; margin-bottom: 6px; color: #111827; }
.hint { color: #6b7280; font-size: 13px; line-height: 1.6; margin-bottom: 14px; }
.sub-hint { margin-left: 12px; color: #9ca3af; font-size: 12px; }
.submit-btn { width: 100%; background: linear-gradient(135deg, #667eea, #764ba2); border: none; }

.bookmark-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  margin-bottom: 4px;
}
.bookmark-item:hover { background: #f3f4f6; }
.bm-name { font-weight: 500; color: #111827; font-size: 13px; }
.bm-url { color: #9ca3af; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 占位 */
.placeholder {
  background: #fff; border-radius: 12px; height: 100%;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.ph-icon { font-size: 64px; opacity: 0.3; }
.ph-title { font-size: 18px; color: #374151; font-weight: 500; }
.ph-desc { color: #9ca3af; font-size: 13px; }

/* 进度卡 */
.progress-card {
  background: #fff; border-radius: 12px; padding: 40px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex; flex-direction: column; gap: 14px;
}
.progress-title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.spin { animation: spin 1s linear infinite; color: #3b82f6; }
@keyframes spin { to { transform: rotate(360deg); } }
.progress-msg { color: #6b7280; font-size: 13px; }

.error-card {
  background: #fef2f2; border: 1px solid #fca5a5; border-radius: 12px; padding: 30px;
  display: flex; flex-direction: column; gap: 12px;
}
.error-title { color: #b91c1c; font-size: 16px; font-weight: 600; }
.error-msg { color: #7f1d1d; font-family: monospace; font-size: 13px; white-space: pre-wrap; }

/* 结果卡 */
.result-card {
  background: #fff; border-radius: 12px; padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  max-height: calc(100vh - 120px); overflow-y: auto;
}
.summary-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.repo-title { font-size: 15px; font-weight: 600; color: #111827; }
.meta-row { color: #9ca3af; font-size: 12px; margin-top: 4px; display: flex; gap: 14px; }
.actions { display: flex; gap: 6px; }

.stats-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 18px; }
.stats-box { background: #f9fafb; padding: 14px; border-radius: 8px; }
.stats-title { font-size: 13px; color: #374151; font-weight: 600; margin-bottom: 10px; }
.tech-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.tech-chip {
  padding: 3px 10px; background: #ede9fe; color: #6d28d9;
  border-radius: 12px; font-size: 12px; font-weight: 500;
}
.muted { color: #9ca3af; font-size: 13px; }
.lang-bars { display: flex; flex-direction: column; gap: 6px; }
.lang-row { display: grid; grid-template-columns: 80px 1fr 50px; gap: 8px; align-items: center; font-size: 12px; }
.lang-name { color: #374151; }
.lang-track { background: #e5e7eb; height: 6px; border-radius: 3px; overflow: hidden; }
.lang-fill { height: 100%; background: linear-gradient(90deg, #3b82f6, #8b5cf6); }
.lang-percent { color: #6b7280; text-align: right; }

.mermaid-box { background: #f9fafb; padding: 18px; border-radius: 8px; margin-bottom: 18px; border: 1px solid #e5e7eb; }
.mermaid-title { font-size: 14px; font-weight: 600; margin-bottom: 14px; color: #374151; }
.mermaid-svg { display: flex; justify-content: center; overflow-x: auto; }
.mermaid-svg :deep(svg) { max-width: 100%; height: auto; }
.mermaid-loading { color: #9ca3af; text-align: center; padding: 20px; font-size: 13px; }
.mermaid-error { background: #fef2f2; color: #b91c1c; padding: 10px; border-radius: 6px; font-size: 13px; }
.mermaid-code { background: #1e293b; color: #e2e8f0; padding: 12px; border-radius: 6px; font-size: 12px; overflow-x: auto; margin-top: 6px; max-height: 240px; overflow-y: auto; }

/* 凭证匹配反馈 */
.cred-badge { margin-top: 6px; padding: 6px 12px; border-radius: 6px; font-size: 12px; line-height: 1.6; }
.cred-badge.cred-ok { background: #f0fdf4; border: 1px solid #bbf7d0; color: #059669; }
.cred-badge.cred-miss { background: #fffbeb; border: 1px solid #fde68a; color: #b45309; }
.cred-badge code { background: rgba(0,0,0,0.06); padding: 1px 5px; border-radius: 3px; font-size: 11px; color: #6d28d9; }
.cred-type { margin-left: 6px; padding: 1px 8px; background: #ede9fe; color: #6d28d9; border-radius: 10px; font-size: 11px; }
.cred-more { color: #6b7280; margin-left: 6px; font-size: 11px; }

.markdown-body { line-height: 1.75; color: #1f2937; }
.markdown-body :deep(h1) { font-size: 22px; margin: 20px 0 10px; color: #111827; border-bottom: 2px solid #e5e7eb; padding-bottom: 6px; }
.markdown-body :deep(h2) { font-size: 18px; margin: 18px 0 8px; color: #1f2937; }
.markdown-body :deep(h3) { font-size: 15px; margin: 14px 0 6px; color: #374151; }
.markdown-body :deep(p) { margin: 8px 0; }
.markdown-body :deep(code) { background: #f3f4f6; padding: 2px 6px; border-radius: 3px; font-size: 0.9em; color: #6d28d9; }
.markdown-body :deep(pre) { background: #1e293b; color: #e2e8f0; padding: 12px; border-radius: 6px; overflow-x: auto; }
.markdown-body :deep(pre code) { background: transparent; color: inherit; padding: 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 24px; }
.markdown-body :deep(li) { margin: 4px 0; }
.markdown-body :deep(a) { color: #3b82f6; }
.markdown-body :deep(blockquote) { border-left: 4px solid #e5e7eb; padding: 4px 12px; color: #6b7280; margin: 10px 0; }
</style>
