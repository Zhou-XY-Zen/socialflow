<!--
  NotesEditor.vue —— 笔记编辑器（md-editor-v3 升级版）
  - md-editor-v3：自带工具栏（标题/加粗/斜体/列表/代码/表格/链接/图片）+ 实时预览 + 全屏
  - 上方独立 toolbar：返回/标题/分类/标签/置顶/公开/保存
  - 自动保存：3 秒静默保存
  - 底部反向链接面板（点击跳详情）
-->

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { noteApi, noteCategoryApi, noteTagApi } from '@/api/note'
import type { NoteVO, NoteCategoryVO, NoteTagVO, NoteLinkVO } from '@/types/api'

const route = useRoute()
const router = useRouter()

/* 字符串 ID —— 雪花 19 位超 JS Number 精度 */
const idParam = computed(() => {
  const v = route.params.id
  if (!v) return undefined
  return Array.isArray(v) ? v[0] : v
})
const isNew = computed(() => !idParam.value)

const title = ref('')
const contentMd = ref('')
const categoryId = ref<string | undefined>()
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

/* md-editor-v3 工具栏：精选项 */
const toolbars = [
  'bold', 'underline', 'italic', 'strikeThrough', '-',
  'title', 'sub', 'sup', 'quote', 'unorderedList', 'orderedList', 'task', '-',
  'codeRow', 'code', 'link', 'image', 'table', 'mermaid', 'katex', '-',
  'revoke', 'next', 'pageFullscreen', 'preview', 'previewOnly', 'catalog',
] as const

const categoryOptions = computed(() => {
  const flat: { id: string; label: string }[] = []
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

function jumpTo(id: string) { router.push({ name: 'notes-detail', params: { id } }) }

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
  watch([title, contentMd, categoryId, tags, isPinned, isPublic],
        () => { dirty.value = true })
})

watch(idParam, async (newId, oldId) => {
  if (oldId && newId && oldId !== newId) await loadNote()
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

function back() {
  if (idParam.value) router.push({ name: 'notes-detail', params: { id: idParam.value } })
  else router.push({ name: 'notes' })
}
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

    <MdEditor v-model="contentMd" class="md-area"
              :toolbars="toolbars as never"
              language="zh-CN"
              previewTheme="github"
              codeTheme="github"
              :showCodeRowNumber="true"
              :preview="true"
              placeholder="开始用 Markdown 写作… 输入 [[标题]] 链接到其他笔记" />

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
.md-area { flex: 1; min-height: 0; border-radius: 8px; overflow: hidden; }
:deep(.md-editor) { height: 100%; }
.links-bar { display: flex; gap: 24px; padding: 8px 4px 0; flex-wrap: wrap; border-top: 1px dashed #e5e7eb; }
.links-col { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.links-label { font-size: 12px; color: #9ca3af; margin-right: 2px; }
.link-chip { cursor: pointer; }
</style>
