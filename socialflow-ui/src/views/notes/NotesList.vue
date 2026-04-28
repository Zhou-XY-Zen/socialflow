<!--
  NotesList.vue —— 我的笔记 主列表
  搜索 + 分类/标签筛选 + 卡片列表 + 置顶 + 新建/编辑/删除
-->

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { noteApi, noteCategoryApi, noteTagApi } from '@/api/note'
import type { NoteVO, NoteCategoryVO, NoteTagVO, NoteQueryDTO } from '@/types/api'

const router = useRouter()

const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const items = ref<NoteVO[]>([])

const keyword = ref('')
const categoryId = ref<string | undefined>()
const tagIds = ref<string[]>([])
const sortBy = ref<NoteQueryDTO['sortBy']>('pinned-first')

const categories = ref<NoteCategoryVO[]>([])
const tags = ref<NoteTagVO[]>([])

/** 把树拍平给下拉用 */
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

async function loadAll() {
  loading.value = true
  try {
    const q: NoteQueryDTO = {
      keyword: keyword.value || undefined,
      categoryId: categoryId.value,
      tagIds: tagIds.value.length ? tagIds.value : undefined,
      status: 1,
      sortBy: sortBy.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    const res = await noteApi.list(q)
    items.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadFilters() {
  const [cats, tgs] = await Promise.all([
    noteCategoryApi.tree(),
    noteTagApi.list(),
  ])
  categories.value = cats
  tags.value = tgs
}

onMounted(async () => {
  await loadFilters()
  await loadAll()
})

watch([keyword, categoryId, tagIds, sortBy], () => {
  pageNum.value = 1
  loadAll()
})

function goNew()      { router.push({ name: 'notes-edit' }) }
function goEdit(n: NoteVO) { router.push({ name: 'notes-edit', params: { id: n.id } }) }
function goUpload()   { router.push({ name: 'notes-upload' }) }

async function pin(n: NoteVO) {
  await noteApi.togglePin(n.id)
  await loadAll()
}

async function trash(n: NoteVO) {
  try {
    await ElMessageBox.confirm(`将「${n.title}」移入回收站？`, '确认', { type: 'warning' })
    await noteApi.trash(n.id)
    ElMessage.success('已移入回收站')
    await loadAll()
  } catch { /* cancel */ }
}

function fmtTime(t?: string) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : ''
}
</script>

<template>
  <div class="notes-list">
    <PageHeader title="我的笔记" subtitle="知识中枢 · 学习沉淀" icon="Notebook">
      <template #extra>
        <el-button type="primary" :icon="'Upload'" @click="goUpload">导入笔记</el-button>
        <el-button type="success" :icon="'Plus'" @click="goNew">新建笔记</el-button>
      </template>
    </PageHeader>

    <el-card class="filter-bar" shadow="never">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-input v-model="keyword" placeholder="搜索标题、正文、摘要…" clearable :prefix-icon="'Search'" />
        </el-col>
        <el-col :span="6">
          <el-select v-model="categoryId" placeholder="分类（全部）" clearable style="width:100%">
            <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.label" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select v-model="tagIds" multiple collapse-tags collapse-tags-tooltip
                     placeholder="标签筛选" clearable style="width:100%">
            <el-option v-for="t in tags" :key="t.id" :value="t.id" :label="`${t.name} (${t.usageCount ?? 0})`" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="sortBy" style="width:100%">
            <el-option value="pinned-first" label="置顶 + 最近" />
            <el-option value="updated" label="按更新时间" />
            <el-option value="created" label="按创建时间" />
          </el-select>
        </el-col>
      </el-row>
    </el-card>

    <div v-loading="loading" class="grid">
      <EmptyState v-if="!loading && items.length === 0"
                  title="还没有笔记"
                  description="点右上角「导入笔记」批量上传，或「新建笔记」从零开始" />
      <el-card v-for="n in items" :key="n.id" class="note-card" shadow="hover" @click="goEdit(n)">
        <div class="note-head">
          <span class="note-title">
            <el-icon v-if="n.isPinned === 1" color="#f59e0b"><component :is="'Star'" /></el-icon>
            {{ n.title }}
          </span>
          <el-tag v-if="n.sourceType && n.sourceType !== 'manual'" size="small" type="info">
            {{ n.sourceType }}
          </el-tag>
        </div>
        <p class="note-summary">{{ n.summary || '（暂无摘要）' }}</p>
        <div class="note-meta">
          <el-tag v-if="n.categoryName" size="small" type="success">{{ n.categoryName }}</el-tag>
          <el-tag v-for="t in n.tags || []" :key="t" size="small">{{ t }}</el-tag>
        </div>
        <div class="note-footer">
          <span>{{ n.wordCount ?? 0 }} 字 · {{ fmtTime(n.updateTime) }}</span>
          <span class="actions" @click.stop>
            <el-button link size="small" @click="pin(n)">{{ n.isPinned === 1 ? '取消置顶' : '置顶' }}</el-button>
            <el-button link size="small" type="danger" @click="trash(n)">回收站</el-button>
          </span>
        </div>
      </el-card>
    </div>

    <el-pagination class="pager" v-if="total > pageSize"
                   :total="total" :page-size="pageSize" :current-page="pageNum"
                   layout="prev, pager, next, total"
                   @current-change="(p:number)=>{pageNum=p; loadAll();}" />
  </div>
</template>

<style scoped>
.notes-list { padding: 16px; }
.filter-bar { margin-bottom: 16px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 14px; }
.note-card { cursor: pointer; transition: transform .15s; }
.note-card:hover { transform: translateY(-2px); }
.note-head { display: flex; justify-content: space-between; gap: 8px; align-items: center; margin-bottom: 6px; }
.note-title { font-weight: 600; font-size: 15px; color: #1f2937; display: inline-flex; align-items: center; gap: 4px; }
.note-summary { color: #6b7280; font-size: 13px; line-height: 1.5;
                display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.note-meta { display: flex; flex-wrap: wrap; gap: 6px; margin: 8px 0; }
.note-footer { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: #9ca3af; }
.note-footer .actions .el-button { padding: 0 4px; }
.pager { margin-top: 18px; display: flex; justify-content: center; }
</style>
