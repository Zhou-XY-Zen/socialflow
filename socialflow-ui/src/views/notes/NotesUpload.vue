<!--
  NotesUpload.vue —— 导入笔记（P1+P2 升级版）
   - 拖多文件 / 选文件夹 / 拖 ZIP（Notion / Obsidian 导出自动解包）
   - 立即异步入库 → SSE 实时显示流水线进度
   - 任务完成后跳"审阅页"，让用户在入库前确认 AI 富化结果
-->

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import PageHeader from '@/components/PageHeader.vue'
import { noteImportApi } from '@/api/note'
import { useImportSse } from '@/composables/useImportSse'
import type { NoteImportTaskVO } from '@/types/api'

const router = useRouter()
const recentTasks = ref<NoteImportTaskVO[]>([])
const loading = ref(false)
const enrichEnabled = ref(true)

/* SSE 进度状态 */
const liveTaskId = ref<string | null>(null)
const liveTotal = ref(0)
const liveDone = ref(0)
const liveFailed = ref(0)
const liveStage = ref('')
const liveLog = ref<{ time: string; text: string; level: 'info' | 'ok' | 'err' }[]>([])
const sse = useImportSse()

const SUPPORTED = ['.md', '.markdown', '.mdown', '.txt', '.text', '.log',
                   '.docx', '.doc', '.pdf', '.rtf', '.odt', '.epub',
                   '.html', '.htm', '.ipynb', '.zip']

const fileInputRef = ref<HTMLInputElement | null>(null)
const folderInputRef = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)

async function loadRecent() {
  loading.value = true
  try { recentTasks.value = await noteImportApi.listTasks() }
  finally { loading.value = false }
}
onMounted(loadRecent)
onUnmounted(() => sse.stop())

/* ============== 文件拾取入口 ============== */

function pickFiles()  { fileInputRef.value?.click() }
function pickFolder() { folderInputRef.value?.click() }

function onFilePicked(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  upload(Array.from(input.files))
  input.value = ''
}

function onDrop(e: DragEvent) {
  dragOver.value = false
  e.preventDefault()
  const dt = e.dataTransfer
  if (!dt) return
  // 简单先：取所有 file（不递归读 webkitDirectory entry，单层 + ZIP 已能覆盖大多数场景）
  const files = Array.from(dt.files)
  if (files.length) upload(files)
}

function onDragOver(e: DragEvent) { e.preventDefault(); dragOver.value = true }
function onDragLeave() { dragOver.value = false }

/* ============== 上传 + SSE 订阅 ============== */

async function upload(files: File[]) {
  const valid = files.filter(f => {
    const lower = f.name.toLowerCase()
    return SUPPORTED.some(ext => lower.endsWith(ext))
  })
  if (valid.length === 0) {
    ElMessage.warning(`没有可识别的文件类型，支持：${SUPPORTED.join(', ')}`)
    return
  }
  const tooBig = valid.find(f => f.size > 50 * 1024 * 1024)
  if (tooBig) {
    ElMessage.error(`文件 ${tooBig.name} 超过 50MB`)
    return
  }

  liveLog.value = []
  liveTotal.value = valid.length
  liveDone.value = 0
  liveFailed.value = 0
  liveStage.value = '上传中…'
  pushLog('开始上传 ' + valid.length + ' 个文件', 'info')

  try {
    const taskId = await noteImportApi.importBatch(valid, enrichEnabled.value)
    liveTaskId.value = taskId
    liveStage.value = '后端已接收，开始解析…'
    pushLog(`任务 #${taskId} 已创建，订阅进度…`, 'info')
    subscribe(taskId)
  } catch (e: unknown) {
    pushLog('上传失败：' + (e as Error).message, 'err')
    liveStage.value = ''
  }
}

function subscribe(taskId: string) {
  sse.start(taskId, {
    onStage(d) {
      liveStage.value = stageLabel(d.stage)
      if (d.stage === 'parsing' && d.fileName) pushLog(`解析中：${d.fileName}`, 'info')
      else if (d.stage === 'parsed' && d.title) pushLog(`✓ 已解析「${d.title}」`, 'ok')
      else if (d.stage === 'enriching') pushLog(`🤖 AI 富化中…`, 'info')
    },
    onItemDone(d) {
      liveDone.value++
      const tail = d.conflictWithNoteId ? `（检测到冲突 #${d.conflictWithNoteId}）` : ''
      pushLog(`✅ #${d.itemId} ${d.parsedTitle ?? ''}  富化=${d.enrichStatus ?? '-'}  ${tail}`, 'ok')
    },
    onError(d) {
      liveFailed.value++
      pushLog(`❌ ${d.itemId ? '#' + d.itemId : ''} ${d.msg}`, 'err')
    },
    onTaskDone(d) {
      liveStage.value = '已完成 → 跳转审阅页'
      pushLog(`🎉 任务 #${d.taskId} 完成：${d.processed} 个解析成功，${d.failed} 个失败`, 'ok')
      ElMessage.success('解析完成，进入审阅')
      setTimeout(() => router.push({ name: 'notes-import-review', params: { taskId: d.taskId } }), 800)
      loadRecent()
    },
  })
}

function stageLabel(s: string) {
  const map: Record<string, string> = {
    running: '处理中', parsing: '解析中', parsed: '解析完成',
    enriching: 'AI 富化', done: '完成',
  }
  return map[s] ?? s
}
function pushLog(text: string, level: 'info' | 'ok' | 'err' = 'info') {
  liveLog.value.unshift({ time: dayjs().format('HH:mm:ss'), text, level })
  if (liveLog.value.length > 200) liveLog.value.pop()
}

function fmtTime(t?: string) { return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '' }
function statusTag(s: string) {
  switch (s) {
    case 'committed': return { type: 'success', text: '已入库' }
    case 'reviewing': return { type: 'warning', text: '待审阅' }
    case 'running':   return { type: 'primary', text: '处理中' }
    case 'failed':    return { type: 'danger',  text: '失败' }
    case 'cancelled': return { type: 'info',    text: '已取消' }
    case 'pending':   return { type: 'info',    text: '排队中' }
    default:          return { type: 'info',    text: s }
  }
}

function goReview(t: NoteImportTaskVO) {
  router.push({ name: 'notes-import-review', params: { taskId: t.id } })
}
</script>

<template>
  <div class="notes-upload">
    <PageHeader title="导入笔记"
                subtitle="批量上传 / Notion·Obsidian ZIP / 整文件夹 — AI 自动摘要 · 标签 · 分类 · 冲突检测"
                icon="Upload" />

    <el-card class="dropzone" shadow="never">
      <div class="dz" :class="{ over: dragOver }"
           @drop="onDrop" @dragover="onDragOver" @dragleave="onDragLeave">
        <el-icon class="dz-icon"><component :is="'UploadFilled'" /></el-icon>
        <div class="dz-text">
          拖拽<strong>文件 / ZIP / 文件夹</strong>到这里，或者
          <el-button type="primary" link @click="pickFiles">选择文件</el-button>
          /
          <el-button type="primary" link @click="pickFolder">选择文件夹</el-button>
        </div>
        <div class="dz-tip">
          支持 {{ SUPPORTED.join(' / ') }}，单文件 ≤ 50MB · ZIP 自动识别 Notion / Obsidian 结构
        </div>
        <div class="dz-opt">
          <el-switch v-model="enrichEnabled" active-text="开启 AI 富化（摘要 / 标签 / 分类 / 大纲）" />
        </div>
        <input ref="fileInputRef" type="file" multiple style="display:none"
               :accept="SUPPORTED.join(',')" @change="onFilePicked" />
        <input ref="folderInputRef" type="file" webkitdirectory directory multiple
               style="display:none" @change="onFilePicked" />
      </div>
    </el-card>

    <el-card v-if="liveTaskId" class="live" shadow="never">
      <template #header>
        <div class="live-head">
          <span>实时流水线 · 任务 #{{ liveTaskId }}</span>
          <span class="live-stage">{{ liveStage }}</span>
        </div>
      </template>
      <el-progress :percentage="liveTotal === 0 ? 0 : Math.round((liveDone + liveFailed) / liveTotal * 100)"
                   :status="liveFailed > 0 ? 'warning' : (liveDone === liveTotal ? 'success' : undefined)" />
      <div class="live-stats">
        <span>总数 {{ liveTotal }}</span>
        <span class="ok">已完成 {{ liveDone }}</span>
        <span class="err">失败 {{ liveFailed }}</span>
      </div>
      <div class="live-log">
        <div v-for="(l, i) in liveLog" :key="i" :class="['log-line', l.level]">
          <span class="log-time">{{ l.time }}</span>
          <span class="log-text">{{ l.text }}</span>
        </div>
      </div>
    </el-card>

    <el-card class="recent" shadow="never">
      <template #header>
        <div class="recent-head">
          <span>最近导入</span>
          <el-button link :icon="'Refresh'" @click="loadRecent">刷新</el-button>
        </div>
      </template>
      <el-table :data="recentTasks" v-loading="loading" empty-text="还没有导入记录" stripe>
        <el-table-column prop="sourceName" label="文件 / 来源" min-width="240" />
        <el-table-column prop="sourceType" label="类型" width="80" />
        <el-table-column label="进度" width="120">
          <template #default="{ row }">{{ row.processedFiles }}/{{ row.totalFiles }}
            <span v-if="row.failedFiles > 0" class="err"> · 失败 {{ row.failedFiles }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status).type">{{ statusTag(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.finishedAt || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button v-if="row.status === 'reviewing'" link type="warning"
                       size="small" @click="goReview(row)">去审阅</el-button>
            <el-button v-else-if="row.status === 'committed' || row.status === 'failed' || row.status === 'cancelled'"
                       link size="small" @click="goReview(row)">查看详情</el-button>
            <el-button v-else-if="row.status === 'running' || row.status === 'pending'"
                       link size="small" @click="subscribe(row.id)">订阅进度</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.notes-upload { padding: 16px; }
.dropzone { margin-bottom: 16px; }
.dz { padding: 32px; border: 2px dashed #d4dbe5; border-radius: 12px; text-align: center;
      transition: all .15s; }
.dz.over { border-color: #409eff; background: #ecf5ff; }
.dz-icon { font-size: 56px; color: #409eff; }
.dz-text { font-size: 16px; color: #1f2937; margin-top: 8px; }
.dz-tip { color: #9ca3af; font-size: 12px; margin-top: 12px; line-height: 1.6; }
.dz-opt { margin-top: 14px; }
.live { margin-bottom: 16px; }
.live-head { display: flex; justify-content: space-between; align-items: center; }
.live-stage { color: #409eff; font-size: 13px; }
.live-stats { display: flex; gap: 20px; margin: 10px 0; font-size: 13px; }
.live-stats .ok { color: #10b981; }
.live-stats .err, .err { color: #ef4444; }
.live-log { max-height: 240px; overflow-y: auto; background: #0f172a; color: #e2e8f0;
            padding: 10px 12px; border-radius: 8px; font-family: ui-monospace, monospace; font-size: 12px; line-height: 1.7; }
.log-line { display: flex; gap: 10px; }
.log-line.ok .log-text { color: #4ade80; }
.log-line.err .log-text { color: #f87171; }
.log-time { color: #94a3b8; flex-shrink: 0; }
.recent-head { display: flex; justify-content: space-between; align-items: center; }
</style>
