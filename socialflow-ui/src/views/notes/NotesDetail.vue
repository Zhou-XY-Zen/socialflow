<!--
  NotesDetail.vue —— 笔记详情（只读预览）
   - 上：标题 + 元信息 + 操作按钮
   - 中（左主区）：Markdown 渲染
   - 右侧栏：元信息卡片 + 自动 TOC 目录
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

/** 给 heading 加 id 用 —— 标题 → 安全 slug */
function slug(text: string) {
  return text.trim().toLowerCase()
              .replace(/[^\w一-龥]+/g, '-')
              .replace(/^-+|-+$/g, '')
}

const md = new MarkdownIt({
  html: false, linkify: true, breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try { return hljs.highlight(str, { language: lang }).value } catch {}
    }
    return ''
  }
})

/* 给所有 heading 渲染时加 id，TOC 点击 anchor 跳转用 */
const headingIndex = { count: 0 }
md.renderer.rules.heading_open = (tokens, idx) => {
  const tag = tokens[idx].tag
  const inline = tokens[idx + 1]
  const text = inline?.children?.filter(t => t.type === 'text').map(t => t.content).join('') ?? ''
  const id = `h-${headingIndex.count++}-${slug(text)}`
  return `<${tag} id="${id}">`
}

const html = computed(() => {
  headingIndex.count = 0
  return md.render(note.value?.contentMd ?? '')
})

/** 解析正文里的标题成 TOC 树 */
const toc = computed(() => {
  const md = note.value?.contentMd ?? ''
  if (!md) return []
  const result: { level: number; text: string; id: string }[] = []
  let count = 0
  for (const line of md.split('\n')) {
    const m = /^(#{1,3})\s+(.+?)\s*$/.exec(line)
    if (m) {
      const text = m[2].trim()
      result.push({ level: m[1].length, text, id: `h-${count++}-${slug(text)}` })
    }
  }
  return result
})

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

function fmt(t?: string) { return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-' }
function fmtRelative(t?: string) {
  if (!t) return '-'
  const diff = Date.now() - new Date(t).getTime()
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 30) return `${d} 天前`
  return fmt(t)
}

function scrollTo(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function sourceLabel(s?: string) {
  switch (s) {
    case 'upload': return '上传文件'
    case 'url':    return '网页剪藏'
    case 'clip':   return '网页剪藏'
    case 'manual': return '手动创建'
    default:       return s ?? '未知'
  }
}

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
                :subtitle="`${note.wordCount ?? 0} 字 · 更新于 ${fmtRelative(note.updateTime)}`">
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

    <div v-if="note" class="layout">
      <!-- 左侧元信息 + TOC -->
      <aside class="info-col">
        <!-- 元信息卡 -->
        <div class="info-card">
          <div class="info-head">
            <el-icon><component :is="'InfoFilled'" /></el-icon>
            <span>笔记信息</span>
          </div>
          <dl class="info-grid">
            <dt>分类</dt>
            <dd>
              <el-tag v-if="note.categoryName" type="success" size="small">{{ note.categoryName }}</el-tag>
              <span v-else class="muted">未分类</span>
            </dd>
            <dt>字数</dt>
            <dd>{{ note.wordCount ?? 0 }}</dd>
            <dt>来源</dt>
            <dd>{{ sourceLabel(note.sourceType) }}</dd>
            <dt>状态</dt>
            <dd>
              <el-tag v-if="note.isPinned === 1" type="warning" size="small">已置顶</el-tag>
              <el-tag v-if="note.isPublic === 1" type="success" size="small">已公开</el-tag>
              <span v-if="note.isPinned !== 1 && note.isPublic !== 1" class="muted">普通</span>
            </dd>
            <dt>创建</dt>
            <dd>{{ fmt(note.createTime) }}</dd>
            <dt>更新</dt>
            <dd>{{ fmtRelative(note.updateTime) }}</dd>
          </dl>
        </div>

        <!-- TOC 目录 -->
        <div v-if="toc.length" class="info-card">
          <div class="info-head">
            <el-icon><component :is="'Menu'" /></el-icon>
            <span>目录</span>
          </div>
          <ul class="toc">
            <li v-for="t in toc" :key="t.id" :class="`toc-l${t.level}`"
                @click="scrollTo(t.id)">
              {{ t.text }}
            </li>
          </ul>
        </div>
      </aside>

      <!-- 主内容 -->
      <article class="content-col markdown-body" v-html="html"></article>
    </div>
  </div>
</template>

<style scoped>
.detail { padding: 16px; max-width: 1280px; margin: 0 auto; }

.layout { display: grid; grid-template-columns: 260px 1fr; gap: 18px; align-items: start; }

/* 主内容 */
.content-col { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px;
               padding: 32px 40px; font-size: 15px; line-height: 1.85; color: #1f2937;
               box-shadow: 0 1px 3px rgba(0,0,0,.03); min-width: 0; }
.content-col :deep(h1) { font-size: 1.7em; padding-bottom: 8px;
                          border-bottom: 1px solid #e5e7eb; margin: 0 0 .6em; }
.content-col :deep(h2) { font-size: 1.35em; margin: 1.4em 0 .5em; padding-bottom: 4px;
                          border-bottom: 1px dashed #e5e7eb; }
.content-col :deep(h3) { font-size: 1.15em; margin: 1.2em 0 .4em; color: #374151; }
.content-col :deep(h4), .content-col :deep(h5), .content-col :deep(h6)
              { margin: 1em 0 .4em; }
.content-col :deep(p) { margin: 0 0 1em; }
.content-col :deep(pre) { background: #f6f8fa; padding: 14px; border-radius: 6px;
                          overflow-x: auto; font-size: 13px; }
.content-col :deep(code) { font-family: ui-monospace, monospace; font-size: 13px;
                            background: #f3f4f6; padding: 1px 5px; border-radius: 3px; }
.content-col :deep(pre code) { background: transparent; padding: 0; }
.content-col :deep(blockquote) { border-left: 3px solid #93c5fd; color: #4b5563;
                                  padding: .4em 14px; margin: 1em 0; background: #f8fafc;
                                  border-radius: 0 6px 6px 0; }
.content-col :deep(table) { border-collapse: collapse; margin: 1em 0; }
.content-col :deep(th) { background: #f9fafb; }
.content-col :deep(th), .content-col :deep(td) { border: 1px solid #e5e7eb; padding: 8px 12px; }
.content-col :deep(img) { max-width: 100%; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
.content-col :deep(ul), .content-col :deep(ol) { padding-left: 1.5em; }
.content-col :deep(a) { color: #2563eb; text-decoration: none; }
.content-col :deep(a:hover) { text-decoration: underline; }

/* 右侧栏 */
.info-col { display: flex; flex-direction: column; gap: 14px; position: sticky; top: 16px; }
.info-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 14px 16px;
             box-shadow: 0 1px 3px rgba(0,0,0,.03); }
.info-head { display: flex; align-items: center; gap: 6px; padding-bottom: 10px;
             margin-bottom: 10px; border-bottom: 1px dashed #e5e7eb;
             font-size: 12px; color: #6b7280; font-weight: 600; }
.info-grid { display: grid; grid-template-columns: 50px 1fr; gap: 8px 10px;
             margin: 0; font-size: 12px; }
.info-grid dt { color: #9ca3af; }
.info-grid dd { color: #374151; margin: 0; word-break: break-word; }
.info-grid dd .el-tag + .el-tag { margin-left: 4px; }
.muted { color: #9ca3af; }

/* TOC */
.toc { list-style: none; padding: 0; margin: 0; max-height: 50vh; overflow-y: auto; }
.toc li { padding: 4px 8px; border-radius: 4px; cursor: pointer; font-size: 12px;
          color: #4b5563; line-height: 1.5; transition: all .12s; }
.toc li:hover { background: #f3f4f6; color: #2563eb; }
.toc-l1 { font-weight: 600; color: #1f2937; }
.toc-l2 { padding-left: 18px; }
.toc-l3 { padding-left: 32px; color: #6b7280; }
</style>
