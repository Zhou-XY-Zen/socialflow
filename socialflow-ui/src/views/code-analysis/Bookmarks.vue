<!--
  Bookmarks.vue —— 仓库收藏 CRUD
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { RepoBookmark } from '@/types/codeAnalysis'

const router = useRouter()
const list = ref<RepoBookmark[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref<Partial<RepoBookmark>>({ nickname: '', gitUrl: '', branch: 'main' })

async function load() {
  loading.value = true
  try { list.value = await codeAnalysisApi.listBookmarks() } finally { loading.value = false }
}

onMounted(load)

function openAdd() {
  form.value = { nickname: '', gitUrl: '', branch: 'main' }
  dialogVisible.value = true
}

function openEdit(b: RepoBookmark) {
  form.value = { ...b }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.nickname || !form.value.gitUrl) {
    ElMessage.warning('昵称和 Git 地址必填')
    return
  }
  await codeAnalysisApi.saveBookmark({
    id: form.value.id,
    nickname: form.value.nickname!,
    gitUrl: form.value.gitUrl!,
    branch: form.value.branch,
    tags: form.value.tags,
  })
  ElMessage.success('已保存')
  dialogVisible.value = false
  load()
}

async function remove(b: RepoBookmark) {
  await ElMessageBox.confirm(`删除收藏「${b.nickname}」？`, '警告', { type: 'warning' })
  await codeAnalysisApi.deleteBookmark(b.id)
  ElMessage.success('已删除')
  load()
}

function useForProject(b: RepoBookmark) {
  router.push({ path: '/code-analysis/project', query: { git: b.gitUrl, branch: b.branch || 'main' } })
}

function useForReview(b: RepoBookmark) {
  router.push({ path: '/code-analysis/review', query: { git: b.gitUrl, branch: b.branch || 'main' } })
}

function extractRepoName(url: string) {
  if (!url) return ''
  const u = url.endsWith('.git') ? url.slice(0, -4) : url
  const i = u.lastIndexOf('/')
  return i >= 0 ? u.slice(i + 1) : u
}
</script>

<template>
  <div class="bookmarks-page" v-loading="loading">
    <div class="top-bar">
      <div class="page-title">⭐ 仓库收藏</div>
      <el-button type="primary" @click="openAdd">+ 添加仓库</el-button>
    </div>

    <div v-if="list.length === 0" class="empty">
      <div class="empty-icon">📂</div>
      <div>还没有任何收藏，点击右上角「添加仓库」开始</div>
    </div>

    <div v-else class="grid">
      <div v-for="b in list" :key="b.id" class="bm-card">
        <div class="bm-name">{{ b.nickname }}</div>
        <div class="bm-repo">{{ extractRepoName(b.gitUrl) }}</div>
        <div class="bm-url">{{ b.gitUrl }}</div>
        <div class="bm-meta">
          <span>🔀 {{ b.branch || 'main' }}</span>
          <span v-if="b.lastScore != null">评分 {{ b.lastScore }}</span>
        </div>
        <div class="bm-actions">
          <el-button size="small" type="primary" plain @click="useForProject(b)">📖 项目概览</el-button>
          <el-button size="small" type="success" plain @click="useForReview(b)">🔎 提交审查</el-button>
          <el-button size="small" link @click="openEdit(b)">✏️</el-button>
          <el-button size="small" type="danger" link @click="remove(b)">✕</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑收藏' : '添加收藏'" width="500px">
      <el-form label-position="top">
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="Git URL"><el-input v-model="form.gitUrl" /></el-form-item>
        <el-form-item label="默认分支"><el-input v-model="form.branch" /></el-form-item>
        <el-form-item label="标签（逗号分隔）"><el-input v-model="form.tags" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bookmarks-page { padding: 20px; }
.top-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 18px; font-weight: 600; color: #111827; }
.empty { text-align: center; padding: 80px 20px; color: #9ca3af; }
.empty-icon { font-size: 64px; opacity: 0.3; margin-bottom: 10px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }
.bm-card {
  background: #fff; border-radius: 10px; padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); transition: transform 0.2s;
  display: flex; flex-direction: column; gap: 6px;
}
.bm-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); }
.bm-name { font-size: 15px; font-weight: 600; color: #111827; }
.bm-repo { color: #6d28d9; font-size: 13px; font-weight: 500; }
.bm-url { color: #9ca3af; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bm-meta { display: flex; gap: 12px; color: #6b7280; font-size: 12px; }
.bm-actions { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 8px; }
</style>
