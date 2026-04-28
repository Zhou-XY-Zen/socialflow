<!--
  NotesDetail.vue —— 笔记详情（只读预览）
   - 上：标题 + 元信息 + 操作按钮（编辑 / 置顶 / 公开 / 回收站 / 推到工作台）
   - 中：Markdown 渲染（markdown-it + highlight.js）
   - 右侧栏（如有 AI 大纲）：outline 目录
   - 底：双向链接（forward + backlinks）
-->

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import dayjs from 'dayjs'
import PageHeader from '@/components/PageHeader.vue'
import { noteApi } from '@/api/note'
import type { NoteVO, NoteLinkVO } from '@/types/api'

const route = useRoute()
const router = useRouter()

const idParam = computed(() => {
  const v = route.params.id
  if (!v) return undefined
  return Array.isArray(v) ? v[0] : v
})

const note = ref<NoteVO | null>(null)
const backlinks = ref<NoteLinkVO[]>([])
const forwardLinks = ref<NoteLinkVO[]>([])
const loading = ref(false)

const md = new MarkdownIt({
  html: false, linkify: true, breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try { return hljs.highlight(str, { language: lang }).value } catch {}
    }
    return ''
  }
})

const html = computed(() => md.render(note.value?.contentMd ?? ''))

/** AI 大纲：ai_outline 是 JSON 字符串数组（每项是带缩进的标题文本） */
const outline = computed<string[]>(() => {
  if (!note.value?.aiOutline) return []
  try {
    const o = JSON.parse(note.value.aiOutline)
    return Array.isArray(o) ? o : []
  } catch { return [] }
})

async function load() {
  if (!idParam.value) return
  loading.value = true
  try {
    note.value = await noteApi.get(idParam.value)
    const [bl, fl] = await Promise.all([
      noteApi.backlinks(idParam.value),
      noteApi.forwardLinks(idParam.value),
    ])
    backlinks.value = bl
    forwardLinks.value = fl
  } finally {
    loading.value = false
  }
}

onMounted(load)
/* 在同一组件实例里通过 backlink 切换不同笔记时，重新加载 */
watch(idParam, (n, o) => { if (n && n !== o) load() })

function fmt(t?: string) { return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '' }

function goEdit() {
  if (!note.value) return
  router.push({ name: 'notes-edit', params: { id: note.value.id } })
}
function goList() { router.push({ name: 'notes' }) }
function jumpTo(id: string) { router.push({ name: 'notes-detail', params: { id } }) }

async function togglePin() {
  if (!note.value) return
  await noteApi.togglePin(note.value.id)
  await load()
}
async function togglePublic() {
  if (!note.value) return
  await noteApi.togglePublic(note.value.id)
  await load()
}
async function trash() {
  if (!note.value) return
  try {
    await ElMessageBox.confirm(`将「${note.value.title}」移入回收站？`, '确认', { type: 'warning' })
    await noteApi.trash(note.value.id)
    ElMessage.success('已移入回收站')
    router.push({ name: 'notes' })
  } catch {/* cancel */}
}
</script>

<template>
  <div class="detail" v-loading="loading">
    <PageHeader v-if="note" :title="note.title" icon="Document"
                :subtitle="`${note.wordCount ?? 0} 字 · 更新于 ${fmt(note.updateTime)}`">
      <template #actions>
        <el-button :icon="'ArrowLeft'" @click="goList">返回列表</el-button>
        <el-button :icon="'Star'" :type="note.isPinned === 1 ? 'warning' : ''"
                   @click="togglePin">
          {{ note.isPinned === 1 ? '取消置顶' : '置顶' }}
        </el-button>
        <el-button :icon="'Share'" :type="note.isPublic === 1 ? 'success' : ''"
                   @click="togglePublic">
          {{ note.isPublic === 1 ? '取消公开' : '公开' }}
        </el-button>
        <el-button :icon="'Delete'" type="danger" plain @click="trash">回收站</el-button>
        <el-button type="primary" :icon="'EditPen'" @click="goEdit">编辑</el-button>
      </template>
    </PageHeader>

    <div v-if="note" class="meta-row">
      <el-tag v-if="note.categoryName" type="success" size="small">{{ note.categoryName }}</el-tag>
      <el-tag v-for="t in note.tags || []" :key="t" size="small">{{ t }}</el-tag>
      <el-tag v-if="note.sourceType && note.sourceType !== 'manual'" size="small" type="info">
        来源：{{ note.sourceType }}
      </el-tag>
      <span class="meta-time">创建 {{ fmt(note.createTime) }}</span>
    </div>

    <div v-if="note?.summary" class="summary-card">
      <el-icon><component :is="'MagicStick'" /></el-icon>
      <span>{{ note.summary }}</span>
    </div>

    <div class="body-grid">
      <div class="content-col markdown-body" v-html="html"></div>
      <div v-if="outline.length" class="outline-col">
        <div class="outline-head">
          <el-icon><component :is="'Menu'" /></el-icon>
          <span>大纲（AI）</span>
        </div>
        <ul class="outline">
          <li v-for="(o, i) in outline" :key="i">{{ o }}</li>
        </ul>
      </div>
    </div>

    <div v-if="forwardLinks.length || backlinks.length" class="links-section">
      <div v-if="forwardLinks.length" class="links-block">
        <div class="links-title">链接到 →</div>
        <el-tag v-for="l in forwardLinks" :key="'f'+l.dstNoteId" size="small"
                class="link-chip" type="primary" @click="jumpTo(l.dstNoteId)">
          {{ l.dstTitle ?? `#${l.dstNoteId}` }}
        </el-tag>
      </div>
      <div v-if="backlinks.length" class="links-block">
        <div class="links-title">← 反向引用</div>
        <el-tag v-for="l in backlinks" :key="'b'+l.srcNoteId" size="small"
                class="link-chip" type="success" @click="jumpTo(l.srcNoteId)">
          {{ l.srcTitle ?? `#${l.srcNoteId}` }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail { padding: 16px; max-width: 1280px; margin: 0 auto; }
.meta-row { display: flex; gap: 8px; align-items: center; margin: -6px 0 14px; flex-wrap: wrap; }
.meta-time { color: #9ca3af; font-size: 12px; margin-left: auto; }
.summary-card { display: flex; gap: 8px; align-items: flex-start;
                padding: 10px 14px; background: #f0f9ff; border-left: 3px solid #409eff;
                border-radius: 6px; color: #1f2937; font-size: 13px; line-height: 1.7;
                margin-bottom: 14px; }
.body-grid { display: grid; grid-template-columns: 1fr 240px; gap: 18px; }
.body-grid:has(.outline-col:empty) { grid-template-columns: 1fr; }
.content-col { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px;
                padding: 24px 32px; font-size: 15px; line-height: 1.8; color: #1f2937; }
.content-col :deep(h1), .content-col :deep(h2), .content-col :deep(h3) { margin-top: 1.2em; }
.content-col :deep(pre) { background: #f6f8fa; padding: 14px; border-radius: 6px; overflow-x: auto; }
.content-col :deep(code) { font-family: ui-monospace, monospace; font-size: 13px; }
.content-col :deep(blockquote) { border-left: 3px solid #d1d5db; color: #6b7280;
                                  padding-left: 14px; margin-left: 0; }
.content-col :deep(table) { border-collapse: collapse; }
.content-col :deep(th), .content-col :deep(td) { border: 1px solid #e5e7eb; padding: 6px 10px; }
.content-col :deep(img) { max-width: 100%; border-radius: 4px; }
.outline-col { background: #fafafa; border: 1px solid #e5e7eb; border-radius: 8px;
                padding: 14px 12px; font-size: 12px; align-self: start; position: sticky; top: 16px; }
.outline-head { display: flex; align-items: center; gap: 6px; color: #4b5563;
                font-weight: 600; margin-bottom: 10px; }
.outline { list-style: none; padding: 0; margin: 0; }
.outline li { padding: 4px 6px; color: #6b7280; line-height: 1.5;
              border-radius: 3px; cursor: default; white-space: pre; }
.outline li:hover { background: #f3f4f6; }
.links-section { margin-top: 18px; padding: 14px; background: #fafafa;
                 border: 1px dashed #e5e7eb; border-radius: 8px; display: flex;
                 gap: 24px; flex-wrap: wrap; }
.links-block { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.links-title { font-size: 12px; color: #9ca3af; margin-right: 4px; }
.link-chip { cursor: pointer; }
</style>
