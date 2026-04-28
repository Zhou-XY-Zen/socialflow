<!--
  NotesDetail.vue —— 笔记详情（只读预览）
   - 上：标题 + 元信息 + 操作按钮
   - 中：Markdown 渲染（markdown-it + highlight.js）
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
import type { NoteVO } from '@/types/api'

const route = useRoute()
const router = useRouter()

const idParam = computed(() => {
  const v = route.params.id
  if (!v) return undefined
  return Array.isArray(v) ? v[0] : v
})

const note = ref<NoteVO | null>(null)
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

async function load() {
  if (!idParam.value) return
  loading.value = true
  try {
    note.value = await noteApi.get(idParam.value)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(idParam, (n, o) => { if (n && n !== o) load() })

function fmt(t?: string) { return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '' }

function goEdit() {
  if (!note.value) return
  router.push({ name: 'notes-edit', params: { id: note.value.id } })
}
function goList() { router.push({ name: 'notes' }) }

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
      <el-tag v-if="note.sourceType && note.sourceType !== 'manual'" size="small" type="info">
        来源：{{ note.sourceType }}
      </el-tag>
      <span class="meta-time">创建 {{ fmt(note.createTime) }}</span>
    </div>

    <div v-if="note" class="content-col markdown-body" v-html="html"></div>
  </div>
</template>

<style scoped>
.detail { padding: 16px; max-width: 1080px; margin: 0 auto; }
.meta-row { display: flex; gap: 8px; align-items: center; margin: -6px 0 14px; flex-wrap: wrap; }
.meta-time { color: #9ca3af; font-size: 12px; margin-left: auto; }
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
</style>
