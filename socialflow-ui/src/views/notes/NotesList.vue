<!--
  NotesList.vue —— 我的笔记
  - 左：分类目录（树形 + 数量），点击切换筛选
  - 右：搜索 + 排序 + 卡片网格
-->

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { noteApi, noteCategoryApi } from '@/api/note'
import type { NoteVO, NoteCategoryVO, NoteQueryDTO } from '@/types/api'

const router = useRouter()

const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const items = ref<NoteVO[]>([])

const keyword = ref('')
/** 'all' = 全部；'uncat' = 未分类；其他 = category id */
const activeCat = ref<string>('all')
const sortBy = ref<NoteQueryDTO['sortBy']>('pinned-first')

const categories = ref<NoteCategoryVO[]>([])

/** 把分类树拍平到 [{id, label, count}] —— 用于侧栏渲染（保留缩进） */
const flatCats = computed(() => {
  const out: { id: string; label: string; count: number; depth: number }[] = []
  function walk(list: NoteCategoryVO[], depth: number) {
    for (const c of list) {
      out.push({ id: c.id, label: c.name, count: c.noteCount ?? 0, depth })
      if (c.children?.length) walk(c.children, depth + 1)
    }
  }
  walk(categories.value, 0)
  return out
})

/** 顶部"全部"和"未分类"的笔记数 */
const totalAll = computed(() => total.value) // 当 activeCat === all 时是全部
const allCount = ref(0)
const uncatCount = ref(0)

async function loadAll() {
  loading.value = true
  try {
    const q: NoteQueryDTO = {
      keyword: keyword.value || undefined,
      categoryId: activeCat.value !== 'all' && activeCat.value !== 'uncat'
        ? activeCat.value : undefined,
      uncategorizedOnly: activeCat.value === 'uncat' ? true : undefined,
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
  categories.value = await noteCategoryApi.tree()
  // 用 list 总数当"全部"计数（一次额外查询，避免在大数据下精确实时）
  const all = await noteApi.list({ status: 1, pageNum: 1, pageSize: 1 })
  allCount.value = all.total
  // 未分类计数：前端粗算 = total - sum(category counts)
  const sumCat = flatCats.value.reduce((s, c) => s + c.count, 0)
  uncatCount.value = Math.max(0, all.total - sumCat)
}

onMounted(async () => {
  await loadFilters()
  await loadAll()
})

watch([keyword, activeCat, sortBy], () => {
  pageNum.value = 1
  loadAll()
})

function selectCat(id: string) { activeCat.value = id }

function goNew()             { router.push({ name: 'notes-edit' }) }
function goDetail(n: NoteVO) { router.push({ name: 'notes-detail', params: { id: n.id } }) }
function goEdit(n: NoteVO)   { router.push({ name: 'notes-edit', params: { id: n.id } }) }
function goUpload()          { router.push({ name: 'notes-upload' }) }

async function pin(n: NoteVO) {
  await noteApi.togglePin(n.id)
  await loadAll()
}

async function trash(n: NoteVO) {
  try {
    await ElMessageBox.confirm(`将「${n.title}」移入回收站？`, '确认', { type: 'warning' })
    await noteApi.trash(n.id)
    ElMessage.success('已移入回收站')
    await loadFilters() // 计数变了
    await loadAll()
  } catch { /* cancel */ }
}

function fmtTime(t?: string) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : ''
}

function sourceLabel(s?: string) {
  switch (s) {
    case 'upload': return '上传'
    case 'url':    return '剪藏'
    case 'clip':   return '剪藏'
    default:       return ''
  }
}
</script>

<template>
  <div class="notes-list">
    <PageHeader title="我的笔记" subtitle="知识中枢 · 学习沉淀" icon="Notebook">
      <template #actions>
        <el-button type="primary" :icon="'Upload'" @click="goUpload">导入笔记</el-button>
        <el-button type="success" :icon="'Plus'" @click="goNew">新建笔记</el-button>
      </template>
    </PageHeader>

    <div class="layout">
      <!-- 左侧分类目录 -->
      <aside class="sidebar">
        <div class="sidebar-head">
          <el-icon><component :is="'CollectionTag'" /></el-icon>
          <span>分类目录</span>
        </div>
        <ul class="cat-list">
          <li :class="['cat-item', { active: activeCat === 'all' }]" @click="selectCat('all')">
            <el-icon class="cat-icon"><component :is="'Files'" /></el-icon>
            <span class="cat-label">全部笔记</span>
            <span class="cat-count">{{ allCount }}</span>
          </li>
          <li v-for="c in flatCats" :key="c.id"
              :class="['cat-item', { active: activeCat === c.id }]"
              :style="{ paddingLeft: `${12 + c.depth * 16}px` }"
              @click="selectCat(c.id)">
            <el-icon class="cat-icon"><component :is="'Folder'" /></el-icon>
            <span class="cat-label">{{ c.label }}</span>
            <span class="cat-count">{{ c.count }}</span>
          </li>
          <li :class="['cat-item', { active: activeCat === 'uncat' }]" @click="selectCat('uncat')">
            <el-icon class="cat-icon"><component :is="'FolderOpened'" /></el-icon>
            <span class="cat-label">未分类</span>
            <span class="cat-count">{{ uncatCount }}</span>
          </li>
        </ul>
      </aside>

      <!-- 右侧主区 -->
      <main class="main">
        <div class="filter-bar">
          <el-input v-model="keyword" placeholder="搜索标题、正文、摘要…" clearable
                    :prefix-icon="'Search'" class="search-input" />
          <el-select v-model="sortBy" class="sort-select">
            <el-option value="pinned-first" label="置顶 + 最近" />
            <el-option value="updated" label="按更新时间" />
            <el-option value="created" label="按创建时间" />
          </el-select>
        </div>

        <div v-loading="loading" class="grid">
          <EmptyState v-if="!loading && items.length === 0"
                      title="还没有笔记"
                      description="点右上角「导入笔记」批量上传，或「新建笔记」从零开始" />

          <article v-for="n in items" :key="n.id" class="note-card"
                   :class="{ pinned: n.isPinned === 1 }"
                   @click="goDetail(n)">
            <header class="card-header">
              <h3 class="card-title">
                <el-icon v-if="n.isPinned === 1" class="pin-icon" color="#f59e0b">
                  <component :is="'StarFilled'" />
                </el-icon>
                <span>{{ n.title }}</span>
              </h3>
              <span v-if="sourceLabel(n.sourceType)" class="card-badge">{{ sourceLabel(n.sourceType) }}</span>
            </header>

            <p v-if="n.summary" class="card-summary">{{ n.summary }}</p>

            <footer class="card-footer">
              <div class="card-meta">
                <span v-if="n.categoryName" class="meta-cat">
                  <el-icon><component :is="'Folder'" /></el-icon>{{ n.categoryName }}
                </span>
                <span class="meta-words">{{ n.wordCount ?? 0 }} 字</span>
                <span class="meta-time">{{ fmtTime(n.updateTime) }}</span>
              </div>
              <div class="card-actions" @click.stop>
                <el-tooltip content="编辑">
                  <el-button :icon="'EditPen'" link size="small" type="primary" @click="goEdit(n)" />
                </el-tooltip>
                <el-tooltip :content="n.isPinned === 1 ? '取消置顶' : '置顶'">
                  <el-button :icon="'Star'" link size="small"
                             :type="n.isPinned === 1 ? 'warning' : 'default'"
                             @click="pin(n)" />
                </el-tooltip>
                <el-tooltip content="移到回收站">
                  <el-button :icon="'Delete'" link size="small" type="danger" @click="trash(n)" />
                </el-tooltip>
              </div>
            </footer>
          </article>
        </div>

        <el-pagination class="pager" v-if="total > pageSize"
                       :total="total" :page-size="pageSize" :current-page="pageNum"
                       layout="prev, pager, next, total"
                       @current-change="(p:number)=>{pageNum=p; loadAll();}" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.notes-list { padding: 16px; }

.layout { display: grid; grid-template-columns: 240px 1fr; gap: 16px; align-items: start; }

/* ============ 左侧分类目录 ============ */
.sidebar { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px;
           padding: 12px 8px; position: sticky; top: 16px; }
.sidebar-head { display: flex; align-items: center; gap: 6px; padding: 4px 8px 10px;
                font-size: 12px; color: #6b7280; font-weight: 600;
                border-bottom: 1px dashed #e5e7eb; margin-bottom: 6px; }
.cat-list { list-style: none; padding: 0; margin: 0; }
.cat-item { display: flex; align-items: center; gap: 8px;
            padding: 8px 12px; border-radius: 6px; cursor: pointer;
            font-size: 13px; color: #374151; transition: background .12s; }
.cat-item:hover { background: #f3f4f6; }
.cat-item.active { background: #eff6ff; color: #2563eb; font-weight: 500; }
.cat-item.active .cat-icon, .cat-item.active .cat-count { color: #2563eb; }
.cat-icon { font-size: 14px; color: #9ca3af; flex-shrink: 0; }
.cat-label { flex: 1; min-width: 0; white-space: nowrap; overflow: hidden;
              text-overflow: ellipsis; }
.cat-count { font-size: 11px; color: #9ca3af; background: #f3f4f6; padding: 0 8px;
              border-radius: 999px; min-width: 24px; text-align: center; }
.cat-item.active .cat-count { background: #dbeafe; }

/* ============ 主区 ============ */
.main { min-width: 0; }
.filter-bar { display: flex; gap: 12px; margin-bottom: 14px; }
.search-input { flex: 1; }
.sort-select { width: 160px; }

/* ============ 卡片 ============ */
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 14px; }
.note-card { display: flex; flex-direction: column; gap: 10px;
             background: #fff; border: 1px solid #e5e7eb; border-radius: 10px;
             padding: 16px 18px; cursor: pointer;
             transition: all .15s ease; }
.note-card:hover { border-color: #93c5fd; box-shadow: 0 6px 20px rgba(37, 99, 235, .08);
                    transform: translateY(-1px); }
.note-card.pinned { border-left: 3px solid #f59e0b; padding-left: 15px; }
.card-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
.card-title { margin: 0; font-size: 15px; font-weight: 600; color: #111827;
              display: flex; align-items: center; gap: 4px;
              line-height: 1.4;
              display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
              overflow: hidden; }
.pin-icon { font-size: 14px; flex-shrink: 0; }
.card-badge { font-size: 11px; color: #6b7280; background: #f3f4f6;
              padding: 2px 8px; border-radius: 999px; flex-shrink: 0; }
.card-summary { margin: 0; font-size: 13px; color: #6b7280; line-height: 1.6;
                display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical;
                overflow: hidden; }
.card-footer { display: flex; justify-content: space-between; align-items: center;
                margin-top: auto; padding-top: 10px; border-top: 1px solid #f3f4f6; }
.card-meta { display: flex; align-items: center; gap: 12px; font-size: 12px; color: #9ca3af;
              flex: 1; min-width: 0; flex-wrap: wrap; }
.meta-cat { display: inline-flex; align-items: center; gap: 4px; color: #059669; }
.meta-cat .el-icon { font-size: 12px; }
.meta-words, .meta-time { white-space: nowrap; }
.card-actions { display: flex; gap: 2px; flex-shrink: 0; }
.card-actions :deep(.el-button) { padding: 0 4px; }

.pager { margin-top: 18px; display: flex; justify-content: center; }
</style>
