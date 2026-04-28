<!--
  NotesImportReview.vue —— 上传后的审阅页（P2）
   左：item 列表 / 中：原文预览 + AI 富化结果可改 / 右：入库设置 + 冲突处理
   底部：一键 commit
-->

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import PageHeader from '@/components/PageHeader.vue'
import { noteImportApi, noteCategoryApi } from '@/api/note'
import type {
  NoteImportTaskVO, NoteImportItemVO,
  NoteCategoryVO, NoteImportItemUpdateDTO,
} from '@/types/api'

const route = useRoute()
const router = useRouter()
/* 直接当字符串用 —— Number() 会让 19 位雪花 ID 末尾几位变 0 */
const taskId = Array.isArray(route.params.taskId)
  ? route.params.taskId[0]
  : (route.params.taskId as string)

const task = ref<NoteImportTaskVO | null>(null)
const cats = ref<NoteCategoryVO[]>([])
const loading = ref(false)
const committing = ref(false)

const md = new MarkdownIt({
  html: false, linkify: true, breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try { return hljs.highlight(str, { language: lang }).value } catch {}
    }
    return ''
  }
})

const selectedId = ref<string | null>(null)
const editing = ref<{
  itemId: string
  title: string
  categoryId: string | null
  isPublic: number
  resolution: NoteImportItemUpdateDTO['resolution']
} | null>(null)

const items = computed<NoteImportItemVO[]>(() => task.value?.items ?? [])
const selectedItem = computed<NoteImportItemVO | undefined>(() =>
  items.value.find(i => i.id === selectedId.value))
const previewHtml = computed(() => md.render(selectedItem.value?.parsedMd ?? ''))

const categoryOptions = computed(() => {
  const flat: { id: string; label: string }[] = []
  function walk(list: NoteCategoryVO[], depth: number) {
    for (const c of list) {
      flat.push({ id: c.id, label: '— '.repeat(depth) + c.name })
      if (c.children?.length) walk(c.children, depth + 1)
    }
  }
  walk(cats.value, 0)
  return flat
})

const stats = computed(() => {
  const all = items.value
  return {
    total: all.length,
    pending: all.filter(i => i.resolution === 'pending').length,
    create: all.filter(i => i.resolution === 'create').length,
    skip: all.filter(i => i.resolution === 'skip').length,
    overwrite: all.filter(i => i.resolution === 'overwrite').length,
    merge: all.filter(i => i.resolution === 'merge').length,
    failed: all.filter(i => i.parseStatus === 'failed').length,
  }
})

async function load() {
  loading.value = true
  try {
    const [t, c] = await Promise.all([
      noteImportApi.getTask(taskId),
      noteCategoryApi.tree(),
    ])
    task.value = t
    cats.value = c
    if (t.items?.length && selectedId.value === null) {
      select(t.items[0].id)
    }
  } finally { loading.value = false }
}

onMounted(load)

function parsedAi(item: NoteImportItemVO) {
  if (!item.aiPayload) return {}
  try { return JSON.parse(item.aiPayload) }
  catch { return {} }
}

function select(id: string) {
  // 提交当前 editing 改动
  if (editing.value) flush()
  selectedId.value = id
  const item = items.value.find(i => i.id === id)
  if (!item) return
  const ai = parsedAi(item)
  editing.value = {
    itemId: id,
    title: item.parsedTitle ?? '',
    categoryId: ai.categoryId ?? null,
    isPublic: ai.isPublic ?? 0,
    resolution: (item.resolution as NoteImportItemUpdateDTO['resolution']) ?? 'create',
  }
}

async function flush() {
  if (!editing.value) return
  const e = editing.value
  await noteImportApi.updateItem(taskId, e.itemId, {
    parsedTitle: e.title || undefined,
    categoryId: e.categoryId ?? undefined,
    isPublic: e.isPublic,
    resolution: e.resolution,
  })
  // 同步回本地 item，避免列表显示陈旧
  const it = items.value.find(i => i.id === e.itemId)
  if (it) {
    it.parsedTitle = e.title
    it.resolution = e.resolution ?? 'pending'
  }
}

async function commit() {
  if (stats.value.pending > 0) {
    try {
      await ElMessageBox.confirm(
        `还有 ${stats.value.pending} 条未决（pending），将按"跳过"处理。继续？`,
        '提示', { type: 'warning' })
    } catch { return }
  }
  if (editing.value) await flush()
  committing.value = true
  try {
    const r = await noteImportApi.commit(taskId)
    const inDb = r.created + r.overwritten + r.merged
    const parts: string[] = []
    if (r.created > 0)     parts.push(`新建 ${r.created}`)
    if (r.overwritten > 0) parts.push(`覆盖 ${r.overwritten}`)
    if (r.merged > 0)      parts.push(`合并 ${r.merged}`)
    if (r.skipped > 0)     parts.push(`跳过 ${r.skipped}${r.skippedDup ? `（含 ${r.skippedDup} 条重复）` : ''}`)
    if (r.failed > 0)      parts.push(`失败 ${r.failed}`)
    const msg = inDb > 0
      ? `已入库 ${inDb} 条 — ${parts.join('、')}`
      : `没有新笔记入库（${parts.join('、') || '全部已存在'}）`
    ElMessage({ type: inDb > 0 ? 'success' : 'warning', message: msg, duration: 5000 })
    router.push({ name: 'notes' })
  } finally { committing.value = false }
}

async function cancel() {
  try {
    await ElMessageBox.confirm('放弃此次导入？已解析的内容会被清除。', '确认', { type: 'warning' })
    await noteImportApi.cancel(taskId)
    router.push({ name: 'notes-upload' })
  } catch {/**/}
}

function statusDot(item: NoteImportItemVO) {
  if (item.parseStatus === 'failed') return 'err'
  if (item.resolution === 'skip') return 'skip'
  if (item.resolution === 'pending' && item.conflictWithNoteId) return 'warn'
  return 'ok'
}
</script>

<template>
  <div class="review" v-loading="loading">
    <PageHeader title="审阅与入库"
                :subtitle="task?.sourceName ? `任务 #${taskId} · ${task.sourceName}` : ''"
                icon="DocumentChecked">
      <template #actions>
        <el-button :icon="'Close'" @click="cancel">放弃</el-button>
        <el-button type="primary" :icon="'Check'" :loading="committing" @click="commit"
                   :disabled="stats.total === 0">
          入库（{{ stats.create + stats.overwrite + stats.merge }} 条）
        </el-button>
      </template>
    </PageHeader>

    <div class="stats">
      <el-tag size="large">总 {{ stats.total }}</el-tag>
      <el-tag size="large" type="success">入库 {{ stats.create + stats.overwrite + stats.merge }}</el-tag>
      <el-tag size="large" type="info">跳过 {{ stats.skip }}</el-tag>
      <el-tag size="large" type="warning">未决 {{ stats.pending }}</el-tag>
      <el-tag v-if="stats.failed > 0" size="large" type="danger">失败 {{ stats.failed }}</el-tag>
    </div>

    <div class="layout">
      <!-- 左：item 列表 -->
      <el-card class="col col-list" shadow="never">
        <template #header><span>导入项 ({{ items.length }})</span></template>
        <ul class="item-list">
          <li v-for="it in items" :key="it.id"
              :class="['item-row', { active: it.id === selectedId }]"
              @click="select(it.id)">
            <span class="dot" :class="statusDot(it)"></span>
            <div class="item-text">
              <div class="t">{{ it.parsedTitle || it.fileName }}</div>
              <div class="sub">
                <small>{{ it.fileName }}</small>
                <el-tag v-if="it.conflictWithNoteId" size="small" type="warning">冲突</el-tag>
                <el-tag v-if="it.parseStatus === 'failed'" size="small" type="danger">失败</el-tag>
              </div>
            </div>
          </li>
        </ul>
      </el-card>

      <!-- 中：原文预览 -->
      <el-card class="col col-preview" shadow="never">
        <template #header>
          <div class="preview-head">
            <span>{{ selectedItem?.parsedTitle || selectedItem?.fileName || '请选择' }}</span>
            <el-tag v-if="selectedItem" size="small">{{ selectedItem.parseStatus }}</el-tag>
          </div>
        </template>
        <div v-if="selectedItem?.errorMsg" class="error-msg">
          ❌ {{ selectedItem.errorMsg }}
        </div>
        <div v-else class="preview markdown-body" v-html="previewHtml"></div>
      </el-card>

      <!-- 右：入库设置 -->
      <el-card class="col col-edit" shadow="never">
        <template #header><span>入库设置</span></template>
        <div v-if="editing && selectedItem" class="form">
          <el-alert v-if="selectedItem.conflictWithNoteId"
                    type="warning" :closable="false" show-icon class="conflict">
            检测到与已有笔记冲突 →
            「{{ selectedItem.conflictWithNoteTitle ?? `#${selectedItem.conflictWithNoteId}` }}」<br>
            请选择处理方式（跳过 / 覆盖 / 合并 / 仍创建）
          </el-alert>

          <el-form-item label="标题">
            <el-input v-model="editing.title" @blur="flush" />
          </el-form-item>

          <el-form-item label="分类">
            <el-select v-model="editing.categoryId" placeholder="不归类" clearable style="width:100%"
                       @change="flush">
              <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.label" />
            </el-select>
          </el-form-item>

          <el-form-item label="公开">
            <el-switch v-model="editing.isPublic" :active-value="1" :inactive-value="0" @change="flush" />
          </el-form-item>

          <el-form-item label="入库方式">
            <el-radio-group v-model="editing.resolution" @change="flush">
              <el-radio-button value="create">新建</el-radio-button>
              <el-radio-button value="overwrite" :disabled="!selectedItem.conflictWithNoteId">覆盖原笔记</el-radio-button>
              <el-radio-button value="merge" :disabled="!selectedItem.conflictWithNoteId">合并到原笔记</el-radio-button>
              <el-radio-button value="skip">跳过</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.review { padding: 16px; }
.stats { display: flex; gap: 8px; margin: 8px 0 14px; }
.layout { display: grid; grid-template-columns: 280px 1fr 380px; gap: 12px;
          height: calc(100vh - 220px); min-height: 500px; }
.col { display: flex; flex-direction: column; min-height: 0; }
.col :deep(.el-card__body) { flex: 1; overflow: auto; padding: 8px; }
.item-list { list-style: none; padding: 0; margin: 0; }
.item-row { display: flex; gap: 8px; padding: 8px 10px; border-radius: 6px; cursor: pointer;
            align-items: center; }
.item-row:hover { background: #f3f4f6; }
.item-row.active { background: #eff6ff; }
.item-text { flex: 1; min-width: 0; }
.item-text .t { font-weight: 500; font-size: 13px; color: #111827;
                white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.item-text .sub { display: flex; gap: 4px; align-items: center; color: #9ca3af; font-size: 11px; margin-top: 2px; }
.dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot.ok   { background: #10b981; }
.dot.warn { background: #f59e0b; }
.dot.err  { background: #ef4444; }
.dot.skip { background: #9ca3af; }
.preview-head { display: flex; justify-content: space-between; align-items: center; }
.preview { padding: 8px 12px; font-size: 13px; line-height: 1.7; color: #1f2937; }
.preview :deep(pre) { background: #f6f8fa; padding: 10px; border-radius: 6px; overflow-x: auto; }
.preview :deep(code) { font-family: ui-monospace, monospace; font-size: 12px; }
.preview :deep(blockquote) { border-left: 3px solid #d1d5db; color: #6b7280; padding-left: 12px; }
.error-msg { color: #ef4444; padding: 12px; }
.form { padding: 4px 8px; }
.conflict { margin-bottom: 12px; }
</style>
