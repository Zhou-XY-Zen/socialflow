<!--
  NotesEditor.vue —— 笔记编辑器（P0 简化版）
  - 左侧 Markdown 输入（textarea） / 右侧实时预览（markdown-it + highlight.js）
  - 标题 / 分类 / 标签 / 公开 / 置顶 / 草稿 状态
  - 自动保存：3 秒静默保存
  P1 升级：换 md-editor-v3、加 AI 副驾、双向链接 [[ 自动补全
-->

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { noteApi, noteCategoryApi, noteTagApi } from '@/api/note'
import type { NoteVO, NoteCategoryVO, NoteTagVO, NoteLinkVO } from '@/types/api'

const route = useRoute()
const router = useRouter()

const idParam = computed(() => route.params.id ? Number(route.params.id) : undefined)
const isNew = computed(() => !idParam.value)

const title = ref('')
const contentMd = ref('')
const categoryId = ref<number | undefined>()
const tags = ref<string[]>([])
const isPinned = ref(0)
const isPublic = ref(0)
const status = ref(1)
const wordCount = computed(() => contentMd.value.replace(/\s+/g, '').length)

const saving = ref(false)
const lastSavedAt = ref<string>('')
const dirty = ref(false)

const categories = ref<NoteCategoryVO[]>([])
const allTags = ref<NoteTagVO[]>([])
const backlinks = ref<NoteLinkVO[]>([])
const forwardLinks = ref<NoteLinkVO[]>([])

const md = new MarkdownIt({
  html: false, linkify: true, breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try { return hljs.highlight(str, { language: lang }).value } catch {}
    }
    return ''
  }
})
const previewHtml = computed(() => md.render(contentMd.value || ''))

const categoryOptions = computed(() => {
  const flat: { id: number; label: string }[] = []
  function walk(list: NoteCategoryVO[], depth: number) {
    for (const c of list) {
      flat.push({ id: c.id, label: '— '.repeat(depth) + c.name })
      if (c.children?.length) walk(c.children, depth + 1)
    }
  }
  walk(categories.value, 0)
  return flat
})

async function loadFilters() {
  const [cats, tgs] = await Promise.all([noteCategoryApi.tree(), noteTagApi.list()])
  categories.value = cats
  allTags.value = tgs
}

async function loadNote() {
  if (!idParam.value) return
  const n = await noteApi.get(idParam.value)
  applyNote(n)
  await loadLinks()
}

async function loadLinks() {
  if (!idParam.value) { backlinks.value = []; forwardLinks.value = []; return }
  try {
    const [bl, fl] = await Promise.all([
      noteApi.backlinks(idParam.value),
      noteApi.forwardLinks(idParam.value),
    ])
    backlinks.value = bl
    forwardLinks.value = fl
  } catch { /* ignore */ }
}

function jumpTo(id: number) { router.push({ name: 'notes-edit', params: { id } }) }

function applyNote(n: NoteVO) {
  title.value = n.title
  contentMd.value = n.contentMd ?? ''
  categoryId.value = n.categoryId
  tags.value = n.tags ?? []
  isPinned.value = n.isPinned ?? 0
  isPublic.value = n.isPublic ?? 0
  status.value = n.status
  dirty.value = false
}

onMounted(async () => {
  await loadFilters()
  await loadNote()
  // 监听字段变更标记 dirty
  watch([title, contentMd, categoryId, tags, isPinned, isPublic],
        () => { dirty.value = true })
})

let saveTimer: ReturnType<typeof setTimeout> | null = null
watch([title, contentMd], () => {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => { autoSave() }, 3000)
})

async function autoSave() {
  if (!dirty.value) return
  if (!title.value.trim() && !contentMd.value.trim()) return
  await save(true)
}

async function save(silent = false) {
  if (!title.value.trim()) {
    if (!silent) ElMessage.warning('请填写标题')
    return
  }
  saving.value = true
  try {
    if (isNew.value) {
      const n = await noteApi.create({
        title: title.value.trim(),
        contentMd: contentMd.value,
        categoryId: categoryId.value,
        tags: tags.value,
        isPinned: isPinned.value,
        isPublic: isPublic.value,
        status: status.value,
      })
      router.replace({ name: 'notes-edit', params: { id: n.id } })
    } else {
      await noteApi.update(idParam.value!, {
        title: title.value.trim(),
        contentMd: contentMd.value,
        categoryId: categoryId.value,
        tags: tags.value,
        isPinned: isPinned.value,
        isPublic: isPublic.value,
        status: status.value,
      })
    }
    dirty.value = false
    lastSavedAt.value = new Date().toLocaleTimeString()
    if (!silent) ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

function back() { router.push({ name: 'notes' }) }
</script>

<template>
  <div class="editor">
    <div class="toolbar">
      <el-button :icon="'ArrowLeft'" link @click="back">返回</el-button>
      <el-input v-model="title" placeholder="标题…" class="title-input" size="large" />

      <el-select v-model="categoryId" placeholder="分类" clearable style="width: 140px">
        <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.label" />
      </el-select>

      <el-select v-model="tags" multiple filterable allow-create default-first-option
                 placeholder="标签…" style="width: 220px"
                 :collapse-tags="true" :collapse-tags-tooltip="true">
        <el-option v-for="t in allTags" :key="t.id" :value="t.name" :label="t.name" />
      </el-select>

      <el-tooltip content="置顶">
        <el-button :icon="'Star'" :type="isPinned === 1 ? 'warning' : ''" circle
                   @click="isPinned = isPinned === 1 ? 0 : 1" />
      </el-tooltip>
      <el-tooltip content="公开（公开博客）">
        <el-button :icon="'Share'" :type="isPublic === 1 ? 'success' : ''" circle
                   @click="isPublic = isPublic === 1 ? 0 : 1" />
      </el-tooltip>

      <span class="status">
        <span v-if="dirty" class="dot dirty">●</span>
        <span v-else class="dot saved">●</span>
        {{ saving ? '保存中…' : lastSavedAt ? `已保存 ${lastSavedAt}` : (dirty ? '未保存' : '') }}
        · {{ wordCount }} 字
      </span>

      <el-button type="primary" :loading="saving" @click="save(false)">保存</el-button>
    </div>

    <div class="split">
      <textarea v-model="contentMd" class="md-input"
                placeholder="开始用 Markdown 写作… 用 [[标题]] 链接到其他笔记"></textarea>
      <div class="md-preview markdown-body" v-html="previewHtml"></div>
    </div>

    <div class="links-bar" v-if="!isNew && (backlinks.length || forwardLinks.length)">
      <div class="links-col" v-if="forwardLinks.length">
        <span class="links-label">链接到 →</span>
        <el-tag v-for="l in forwardLinks" :key="'f'+l.dstNoteId" size="small"
                class="link-chip" type="primary" @click="jumpTo(l.dstNoteId)">
          {{ l.dstTitle ?? `#${l.dstNoteId}` }}
        </el-tag>
      </div>
      <div class="links-col" v-if="backlinks.length">
        <span class="links-label">← 反向引用</span>
        <el-tag v-for="l in backlinks" :key="'b'+l.srcNoteId" size="small"
                class="link-chip" type="success" @click="jumpTo(l.srcNoteId)">
          {{ l.srcTitle ?? `#${l.srcNoteId}` }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<style scoped>
.editor { display: flex; flex-direction: column; height: calc(100vh - 60px); padding: 12px 16px; gap: 10px; }
.toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.title-input { flex: 1; min-width: 240px; }
.status { color: #6b7280; font-size: 12px; margin: 0 8px; display: inline-flex; align-items: center; gap: 4px; }
.dot { font-size: 8px; }
.dot.dirty { color: #f59e0b; }
.dot.saved { color: #10b981; }
.split { flex: 1; display: grid; grid-template-columns: 1fr 1fr; gap: 12px; min-height: 0; }
.md-input { width: 100%; height: 100%; padding: 14px; resize: none;
            border: 1px solid #e5e7eb; border-radius: 8px; font-family: ui-monospace, "SFMono-Regular", monospace;
            font-size: 14px; line-height: 1.7; outline: none; background: #fafafa; }
.md-input:focus { border-color: #409eff; background: #fff; }
.md-preview { padding: 14px 18px; border: 1px solid #e5e7eb; border-radius: 8px;
              overflow: auto; background: #fff; font-size: 14px; line-height: 1.7; }
.md-preview :deep(h1), .md-preview :deep(h2), .md-preview :deep(h3) { margin-top: 1em; }
.md-preview :deep(pre) { background: #f6f8fa; padding: 12px; border-radius: 6px; overflow-x: auto; }
.md-preview :deep(code) { font-family: ui-monospace, "SFMono-Regular", monospace; font-size: 13px; }
.md-preview :deep(blockquote) { border-left: 3px solid #d1d5db; color: #6b7280; padding-left: 12px; margin-left: 0; }
.md-preview :deep(table) { border-collapse: collapse; }
.md-preview :deep(th), .md-preview :deep(td) { border: 1px solid #e5e7eb; padding: 6px 10px; }
.links-bar { display: flex; gap: 24px; padding: 8px 4px 0; flex-wrap: wrap; border-top: 1px dashed #e5e7eb; }
.links-col { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.links-label { font-size: 12px; color: #9ca3af; margin-right: 2px; }
.link-chip { cursor: pointer; }
</style>
