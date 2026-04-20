<!--
  History.vue —— 历史记录列表，支持筛选/分页/删除/收藏
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { CodeAnalysis } from '@/types/codeAnalysis'

const router = useRouter()
const records = ref<CodeAnalysis[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(20)
const loading = ref(false)
const typeFilter = ref('')
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const p = await codeAnalysisApi.history({
      current: current.value,
      size: size.value,
      analysisType: typeFilter.value || undefined,
      keyword: keyword.value || undefined,
    })
    records.value = p.records
    total.value = p.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function remove(a: CodeAnalysis) {
  await ElMessageBox.confirm(`确定删除该分析记录？`, '警告', { type: 'warning' })
  await codeAnalysisApi.delete(a.id)
  ElMessage.success('已删除')
  load()
}

async function favorite(a: CodeAnalysis) {
  await codeAnalysisApi.toggleFavorite(a.id)
  a.isFavorite = a.isFavorite ? 0 : 1
}

function openResult(a: CodeAnalysis) {
  router.push(`/code-analysis/result/${a.id}`)
}

const typeLabels: Record<string, { label: string; color: string }> = {
  PROJECT_OVERVIEW: { label: '项目概览', color: '#6d28d9' },
  COMMIT_REVIEW:    { label: '提交审查', color: '#2563eb' },
  DIFF_REVIEW:      { label: '对比分析', color: '#059669' },
}

const statusLabels: Record<string, { label: string; color: string }> = {
  PENDING: { label: '排队中', color: '#9ca3af' },
  RUNNING: { label: '分析中', color: '#3b82f6' },
  SUCCESS: { label: '已完成', color: '#10b981' },
  FAILED:  { label: '失败',   color: '#ef4444' },
}

function extractRepoName(url: string) {
  if (!url) return ''
  const u = url.endsWith('.git') ? url.slice(0, -4) : url
  const i = u.lastIndexOf('/')
  return i >= 0 ? u.slice(i + 1) : u
}

function fmtTime(s?: string) {
  if (!s) return ''
  return new Date(s).toLocaleString('zh-CN')
}

// 黄山版 1.2.1：避免魔法值。Token 量级阈值集中常量化。
const TOKEN_K = 1000
const TOKEN_M = 1_000_000

/** Token 数字格式化：123 / 12.3K / 1.23M */
function fmtTokens(n?: number) {
  if (n == null || n === 0) return '-'
  if (n < TOKEN_K) return String(n)
  if (n < TOKEN_M) return (n / TOKEN_K).toFixed(1) + 'K'
  return (n / TOKEN_M).toFixed(2) + 'M'
}
</script>

<template>
  <div class="history-page" v-loading="loading">
    <div class="filter-bar">
      <el-select v-model="typeFilter" placeholder="全部类型" clearable style="width: 160px" @change="load">
        <el-option label="项目概览" value="PROJECT_OVERVIEW" />
        <el-option label="提交审查" value="COMMIT_REVIEW" />
        <el-option label="对比分析" value="DIFF_REVIEW" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索仓库 URL..." clearable style="width: 320px" @keyup.enter="load" />
      <el-button type="primary" @click="load">🔍 搜索</el-button>
      <div style="flex: 1" />
      <span class="count-info">共 {{ total }} 条</span>
    </div>

    <el-table :data="records" style="background: #fff; border-radius: 12px" stripe>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :style="{ background: typeLabels[row.analysisType]?.color + '20',
                            color: typeLabels[row.analysisType]?.color,
                            border: 'none' }">
            {{ typeLabels[row.analysisType]?.label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="仓库" min-width="200">
        <template #default="{ row }">
          <div class="repo-cell">
            <span class="repo-name">{{ extractRepoName(row.gitUrl) }}</span>
            <span class="repo-url">{{ row.gitUrl }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="分支/SHA" width="140">
        <template #default="{ row }">
          <div style="font-size: 12px; color: #6b7280">
            {{ row.branch || '-' }}
            <span v-if="row.commitSha"> · {{ row.commitSha.slice(0, 7) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="评分" width="90" align="center">
        <template #default="{ row }">
          <span v-if="row.overallScore != null" class="score-chip"
                :class="{ s1: row.overallScore >= 85, s2: row.overallScore >= 60 && row.overallScore < 85, s3: row.overallScore < 60 }">
            {{ row.overallScore }}
          </span>
          <span v-else style="color: #9ca3af">-</span>
        </template>
      </el-table-column>
      <el-table-column label="风险数" width="120" align="center">
        <template #default="{ row }">
          <span v-if="row.highCount || row.mediumCount || row.lowCount">
            <span v-if="row.highCount" style="color: #ef4444">高{{ row.highCount }}</span>
            <span v-if="row.mediumCount" style="color: #f59e0b; margin-left: 4px">中{{ row.mediumCount }}</span>
            <span v-if="row.lowCount" style="color: #3b82f6; margin-left: 4px">低{{ row.lowCount }}</span>
          </span>
          <span v-else style="color: #9ca3af">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span :style="{ color: statusLabels[row.status]?.color }">
            {{ statusLabels[row.status]?.label }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="Token" width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.llmTokensUsed" class="token-chip"
                :title="`${row.llmTokensUsed.toLocaleString()} tokens`">
            🧮 {{ fmtTokens(row.llmTokensUsed) }}
          </span>
          <span v-else style="color: #9ca3af">-</span>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170" prop="createTime">
        <template #default="{ row }">
          <span style="color: #6b7280; font-size: 12px">{{ fmtTime(row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openResult(row)">查看</el-button>
          <el-button size="small" link @click="favorite(row)">
            {{ row.isFavorite ? '★' : '☆' }}
          </el-button>
          <el-button size="small" type="danger" link @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next, total"
        background @change="load" />
    </div>
  </div>
</template>

<style scoped>
.history-page { padding: 20px; }
.filter-bar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
.count-info { color: #6b7280; font-size: 13px; }
.repo-cell { display: flex; flex-direction: column; }
.repo-name { font-weight: 500; color: #111827; }
.repo-url { font-size: 11px; color: #9ca3af; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 360px; }
.score-chip {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-weight: 600;
  font-size: 13px;
}
.score-chip.s1 { background: #d1fae5; color: #059669; }
.score-chip.s2 { background: #fef3c7; color: #b45309; }
.score-chip.s3 { background: #fee2e2; color: #b91c1c; }
.token-chip {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  background: #ecfeff;
  color: #0e7490;
  font-size: 12px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
