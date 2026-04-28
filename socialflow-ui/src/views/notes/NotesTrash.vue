<!--
  NotesTrash.vue —— 回收站（status = 3）
-->

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { noteApi } from '@/api/note'
import type { NoteVO } from '@/types/api'

const items = ref<NoteVO[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await noteApi.list({ status: 3, pageSize: 100 })
    items.value = res.records
  } finally { loading.value = false }
}
onMounted(load)

async function restore(n: NoteVO) {
  await noteApi.restore(n.id)
  ElMessage.success('已恢复')
  await load()
}

async function purge(n: NoteVO) {
  try {
    await ElMessageBox.confirm(`彻底删除「${n.title}」？无法恢复。`, '危险操作', { type: 'error' })
    await noteApi.remove(n.id)
    ElMessage.success('已彻底删除')
    await load()
  } catch {/**/}
}

function fmt(t?: string) { return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '' }

function fmtRelative(t?: string) {
  if (!t) return '-'
  const diff = Date.now() - new Date(t).getTime()
  const d = Math.floor(diff / 86400000)
  return d === 0 ? '今天' : d < 30 ? `${d} 天前` : fmt(t)
}

/** 距离 30 天自动清理还有几天 */
function daysLeft(t?: string) {
  if (!t) return 30
  const diff = Date.now() - new Date(t).getTime()
  return Math.max(0, 30 - Math.floor(diff / 86400000))
}
</script>

<template>
  <div class="trash">
    <PageHeader title="回收站" :subtitle="`${items.length} 条笔记 · 30 天后自动清理`"
                icon="Delete">
      <template #actions>
        <el-button :icon="'Refresh'" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div v-loading="loading" class="grid">
      <EmptyState v-if="!loading && items.length === 0"
                  title="回收站是空的"
                  description="移入回收站的笔记会显示在这里，30 天后自动彻底删除" />

      <article v-for="n in items" :key="n.id" class="trash-card">
        <div class="card-icon">
          <el-icon><component :is="'Document'" /></el-icon>
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ n.title }}</h3>
          <p v-if="n.summary" class="card-summary">{{ n.summary }}</p>
          <div class="card-meta">
            <span><el-icon><component :is="'Edit'" /></el-icon> {{ n.wordCount ?? 0 }} 字</span>
            <span><el-icon><component :is="'Clock'" /></el-icon> {{ fmtRelative(n.updateTime) }}移入</span>
            <span class="countdown">{{ daysLeft(n.updateTime) }} 天后自动清理</span>
          </div>
        </div>
        <div class="card-actions">
          <el-button :icon="'RefreshLeft'" type="success" plain size="small" @click="restore(n)">还原</el-button>
          <el-button :icon="'Delete'" type="danger" size="small" @click="purge(n)">彻底删除</el-button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.trash { padding: 16px; max-width: 1080px; margin: 0 auto; }
.grid { display: flex; flex-direction: column; gap: 12px; }

.trash-card { display: flex; align-items: flex-start; gap: 16px;
              background: #fff; border: 1px solid #e5e7eb; border-radius: 12px;
              padding: 16px 20px; transition: all .15s;
              box-shadow: 0 1px 3px rgba(0,0,0,.03); }
.trash-card:hover { border-color: #d1d5db; box-shadow: 0 4px 12px rgba(0,0,0,.05); }

.card-icon { font-size: 24px; color: #ef4444; padding: 10px;
             background: #fef2f2; border-radius: 10px; flex-shrink: 0; }

.card-body { flex: 1; min-width: 0; }
.card-title { margin: 0 0 6px; font-size: 15px; font-weight: 600; color: #1f2937;
              text-decoration: line-through; text-decoration-color: #d1d5db; }
.card-summary { margin: 0 0 8px; font-size: 13px; color: #9ca3af; line-height: 1.5;
                display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
                overflow: hidden; }
.card-meta { display: flex; gap: 16px; font-size: 12px; color: #9ca3af; flex-wrap: wrap; }
.card-meta span { display: inline-flex; align-items: center; gap: 4px; }
.card-meta .el-icon { font-size: 12px; }
.countdown { color: #f59e0b; }

.card-actions { display: flex; gap: 6px; flex-shrink: 0; align-self: center; }
</style>
