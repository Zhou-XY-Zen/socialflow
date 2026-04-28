<!--
  NotesTrash.vue —— 回收站（status = 3）
-->

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import PageHeader from '@/components/PageHeader.vue'
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
</script>

<template>
  <div class="trash">
    <PageHeader title="回收站" subtitle="30 天后定时清理；已删笔记可在此还原"
                icon="Delete" />
    <el-table :data="items" v-loading="loading" empty-text="回收站是空的" stripe>
      <el-table-column prop="title" label="标题" min-width="240" />
      <el-table-column label="字数" width="80" prop="wordCount" />
      <el-table-column label="移入时间" width="180">
        <template #default="{ row }">{{ fmt(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="success" size="small" @click="restore(row)">还原</el-button>
          <el-button link type="danger"  size="small" @click="purge(row)">彻底删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.trash { padding: 16px; }
</style>
