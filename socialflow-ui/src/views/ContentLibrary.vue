<!--
  ============================================================
  ContentLibrary.vue —— 内容库页面
  ============================================================
  以分页表格形式展示所有已生成的内容，
  支持按平台、状态、关键词筛选，
  以及查看详情、编辑、删除、版本历史、导出操作，
  以及批量选择、批量删除、批量导出功能。
  ============================================================
-->

<script setup lang="ts">
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { contentApi } from '@/api/content'
import { get } from '@/api/http'
import type { ContentVO, PageResult } from '@/types/api'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, breaks: true, linkify: true })
function renderMd(text: string) { return md.render(text || '') }
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

/** 平台选项列表 */
const platformOptions = [
  { label: '全部', value: '' },
  { label: '小红书', value: 'XIAOHONGSHU' },
  { label: '抖音', value: 'DOUYIN' },
  { label: '朋友圈', value: 'WECHAT_MOMENT' },
  { label: '公众号', value: 'WECHAT_MP' },
]

/** 状态选项列表 */
const statusOptions = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '定时发布', value: 'SCHEDULED' },
]

/** 筛选条件 */
const filters = reactive({
  platform: '',
  status: '',
  keyword: '',
})

/** 分页参数 */
const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0,
})

/** 表格数据 */
const tableData = ref<ContentVO[]>([])
/** 加载状态 */
const loading = ref(false)

/** 查看详情弹窗 */
const detailVisible = ref(false)
const detailContent = ref<ContentVO | null>(null)

/* ===================== 编辑弹窗相关 ===================== */
/** 编辑弹窗可见性 */
const editVisible = ref(false)
/** 编辑表单数据 */
const editForm = reactive({
  id: 0,
  title: '',
  body: '',
  tags: '',
  scheduledTime: '' as string | null,
})
/** 编辑保存中 */
const editSaving = ref(false)

/* ===================== Wave 4.3 autosave ===================== */
/** autosave 状态文本（"已保存 12:34:56" / "保存中..." / ""） */
const autosaveStatus = ref('')
/** autosave 防抖定时器 */
let autosaveTimer: ReturnType<typeof setTimeout> | null = null

function scheduleAutosave() {
  if (!editVisible.value || !editForm.id) return
  autosaveStatus.value = '编辑中...'
  if (autosaveTimer) clearTimeout(autosaveTimer)
  autosaveTimer = setTimeout(async () => {
    if (!editVisible.value || !editForm.id) return
    try {
      autosaveStatus.value = '保存中...'
      await contentApi.saveDraft(editForm.id, {
        title: editForm.title,
        body: editForm.body,
        tags: editForm.tags,
      })
      const t = new Date()
      const hh = String(t.getHours()).padStart(2, '0')
      const mm = String(t.getMinutes()).padStart(2, '0')
      const ss = String(t.getSeconds()).padStart(2, '0')
      autosaveStatus.value = `已自动保存 ${hh}:${mm}:${ss}`
    } catch {
      autosaveStatus.value = '自动保存失败，请手动保存'
    }
  }, 3000)
}

// 监听 editForm 变化触发 autosave 防抖
watch(() => `${editForm.title}|${editForm.body}|${editForm.tags}`, () => {
  scheduleAutosave()
})

/* ===================== 版本历史抽屉相关 ===================== */
/** 版本历史抽屉可见性 */
const versionDrawerVisible = ref(false)
/** 版本历史数据 */
const versionList = ref<any[]>([])
/** 版本历史加载中 */
const versionLoading = ref(false)

/* ===================== 批量选择相关 ===================== */
/** 表格引用 */
const tableRef = ref<any>(null)
/** 已选择的行 */
const selectedRows = ref<ContentVO[]>([])

/** 是否有选中项 */
const hasSelection = computed(() => selectedRows.value.length > 0)

/**
 * 加载内容列表
 */
async function loadData() {
  loading.value = true
  try {
    const result = await contentApi.list({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      platform: filters.platform,
      status: filters.status,
      keyword: filters.keyword,
    })
    tableData.value = result.records
    pagination.total = result.total
  } catch (e) {
    /* 错误已由拦截器处理 */
  } finally {
    loading.value = false
  }
}

/**
 * 搜索按钮点击，重置到第一页
 */
function handleSearch() {
  pagination.pageNum = 1
  loadData()
}

/**
 * 分页变化
 */
function handlePageChange(page: number) {
  pagination.pageNum = page
  loadData()
}

/**
 * 每页条数变化
 */
function handleSizeChange(size: number) {
  pagination.pageSize = size
  pagination.pageNum = 1
  loadData()
}

/**
 * 关联配图数据
 */
const detailMediaList = ref<any[]>([])
const detailMediaLoading = ref(false)
const detailPreviewUrl = ref('')
const detailPreviewVisible = ref(false)

/**
 * 查看内容详情（同时加载关联配图）
 */
async function handleView(row: ContentVO) {
  detailContent.value = row
  detailVisible.value = true
  detailMediaList.value = []
  detailMediaLoading.value = true
  try {
    const media = await get<any[]>(`/content/${row.id}/media`)
    detailMediaList.value = media || []
  } catch {
    detailMediaList.value = []
  } finally {
    detailMediaLoading.value = false
  }
}

/**
 * 打开编辑弹窗
 */
function handleEdit(row: ContentVO) {
  editForm.id = row.id
  editForm.title = row.title || ''
  editForm.body = row.body || ''
  editForm.tags = row.tags || ''
  editForm.scheduledTime = row.scheduledTime || null
  editVisible.value = true
  // 重置 autosave 状态（避免显示上一篇的"已保存 xx:xx"）
  autosaveStatus.value = ''
  if (autosaveTimer) { clearTimeout(autosaveTimer); autosaveTimer = null }
}

/**
 * 保存编辑
 */
async function handleEditSave() {
  if (!editForm.body.trim()) {
    ElMessage.warning('正文内容不能为空')
    return
  }
  editSaving.value = true
  try {
    await contentApi.update(editForm.id, editForm.title, editForm.body, editForm.tags)
    ElMessage.success('保存成功')
    editVisible.value = false
    loadData()
  } catch (e) {
    /* 错误已由拦截器处理 */
  } finally {
    editSaving.value = false
  }
}

/**
 * Wave 4.3 - 克隆内容为新草稿。
 */
async function handleClone(row: ContentVO) {
  try {
    const cloned = await contentApi.clone(row.id)
    ElMessage.success(`已克隆为新草稿 #${cloned.id}`)
    loadData()
  } catch {
    /* 拦截器已提示 */
  }
}

/**
 * Wave 4.3 - 批量改状态。
 */
async function handleBulkStatus(status: string) {
  if (selectedRows.value.length === 0) return
  try {
    const ids = selectedRows.value.map(r => r.id)
    const res = await contentApi.bulkUpdateStatus(ids, status)
    ElMessage.success(`已更新 ${res.affected} 条记录状态为 ${status}`)
    selectedRows.value = []
    loadData()
  } catch {
    /* 拦截器已提示 */
  }
}

/**
 * 删除内容
 */
async function handleDelete(row: ContentVO) {
  try {
    await ElMessageBox.confirm('确定要删除这条内容吗？删除后不可恢复。', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await contentApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    /* 用户取消或请求失败，忽略 */
  }
}

/**
 * 打开版本历史抽屉
 */
async function handleVersionHistory() {
  if (!detailContent.value) return
  versionDrawerVisible.value = true
  versionLoading.value = true
  versionList.value = []
  try {
    const versions = await contentApi.versions(detailContent.value.id)
    versionList.value = versions || []
  } catch {
    versionList.value = []
    ElMessage.error('加载版本历史失败')
  } finally {
    versionLoading.value = false
  }
}

/**
 * 导出单条内容（触发浏览器下载）
 */
function handleExport() {
  if (!detailContent.value) return
  const url = contentApi.exportUrl(detailContent.value.id)
  /* 使用隐藏链接触发下载，附带 Token 通过 URL 参数 */
  const userStore = useUserStore()
  const downloadUrl = `${url}?token=${encodeURIComponent(userStore.token || '')}`
  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = ''
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  ElMessage.success('开始下载')
}

/**
 * 表格选择变化事件
 */
function handleSelectionChange(rows: ContentVO[]) {
  selectedRows.value = rows
}

/**
 * 批量删除
 */
async function handleBatchDelete() {
  if (selectedRows.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 条内容吗？删除后不可恢复。`,
      '确认批量删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    const ids = selectedRows.value.map((r) => r.id)
    await contentApi.batchDelete(ids)
    ElMessage.success(`成功删除 ${ids.length} 条内容`)
    selectedRows.value = []
    loadData()
  } catch (e) {
    /* 用户取消或请求失败，忽略 */
  }
}

/**
 * 批量导出
 */
function handleBatchExport() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)
  const url = contentApi.batchExportUrl(ids)
  const userStore = useUserStore()
  const downloadUrl = `${url}&token=${encodeURIComponent(userStore.token || '')}`
  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = ''
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  ElMessage.success('开始批量导出')
}

/**
 * 获取平台显示名称
 */
function getPlatformLabel(platform: string): string {
  const map: Record<string, string> = {
    XIAOHONGSHU: '小红书',
    DOUYIN: '抖音',
    WECHAT_MOMENT: '朋友圈',
    WECHAT_MP: '公众号',
  }
  return map[platform] || platform
}

/**
 * 获取平台标签颜色
 */
function getPlatformType(platform: string): string {
  const map: Record<string, string> = {
    XIAOHONGSHU: 'danger',
    DOUYIN: '',
    WECHAT_MOMENT: 'success',
    WECHAT_MP: 'warning',
  }
  return map[platform] || 'info'
}

/**
 * 获取状态标签颜色
 */
function getStatusType(status: string): string {
  const map: Record<string, string> = {
    DRAFT: 'info',
    PUBLISHED: 'success',
    SCHEDULED: 'warning',
  }
  return map[status] || 'info'
}

/**
 * 获取状态显示名称
 */
function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    SCHEDULED: '定时发布',
  }
  return map[status] || status
}

/**
 * 获取标题显示文本（标题或正文前 30 字）
 */
function getDisplayTitle(row: ContentVO): string {
  if (row.title) return row.title
  if (row.body) return row.body.substring(0, 30) + (row.body.length > 30 ? '...' : '')
  return '无标题'
}

/** 页面加载时获取数据 */
onMounted(loadData)
</script>

<template>
  <div class="sf-page-container">
    <!-- 页面头部 -->
    <PageHeader title="内容库" subtitle="管理所有生成的文案" icon="Files" />

    <!-- 筛选栏 -->
    <el-card style="margin-bottom: 16px">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="平台">
          <el-select v-model="filters.platform" placeholder="全部平台" clearable style="width: 140px">
            <el-option
              v-for="opt in platformOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索标题或内容"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 批量操作栏（选中时显示） -->
    <el-card v-if="hasSelection" style="margin-bottom: 16px">
      <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
        <el-tag type="info">已选择 {{ selectedRows.length }} 项</el-tag>
        <el-button type="danger" size="small" @click="handleBatchDelete">
          <el-icon style="margin-right: 4px"><Delete /></el-icon>
          批量删除
        </el-button>
        <el-button type="primary" size="small" @click="handleBatchExport">
          <el-icon style="margin-right: 4px"><Download /></el-icon>
          批量导出
        </el-button>
        <!-- Wave 4.3: 批量改状态 -->
        <el-dropdown trigger="click" @command="handleBulkStatus">
          <el-button type="warning" size="small">
            <el-icon style="margin-right: 4px"><Refresh /></el-icon>
            批量改状态
            <el-icon style="margin-left: 4px"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="DRAFT">设为草稿</el-dropdown-item>
              <el-dropdown-item command="SCHEDULED">设为待发布</el-dropdown-item>
              <el-dropdown-item command="PUBLISHED">设为已发布</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-card>

    <!-- 空状态 -->
    <EmptyState
      v-if="!loading && tableData.length === 0"
      icon="Document"
      title="暂无内容"
      description="开始生成你的第一篇文案吧"
      actionText="去创建"
      @action="$router.push('/workspace')"
    />

    <!-- 内容表格 -->
    <el-card v-else v-loading="loading">
      <el-table
        ref="tableRef"
        :data="tableData"
        stripe
        @selection-change="handleSelectionChange"
      >
        <!-- 批量选择列 -->
        <el-table-column type="selection" width="45" />

        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">
            {{ getDisplayTitle(row) }}
          </template>
        </el-table-column>
        <el-table-column label="平台" width="100">
          <template #default="{ row }">
            <el-tag :type="getPlatformType(row.platform)" size="small">
              {{ getPlatformLabel(row.platform) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="模型" width="130" />
        <el-table-column prop="tokenUsage" label="Token" width="90" />
        <!-- 定时发布时间列 -->
        <el-table-column label="定时发布" width="170">
          <template #default="{ row }">
            <span v-if="row.scheduledTime" style="color: #e6a23c; font-size: 13px">
              {{ row.scheduledTime }}
            </span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)" title="查看">
              <el-icon><View /></el-icon>
            </el-button>
            <el-button link type="primary" @click="handleEdit(row)" title="编辑">
              <el-icon><Edit /></el-icon>
            </el-button>
            <!-- Wave 4.3: 克隆 -->
            <el-button link type="warning" @click="handleClone(row)" title="克隆为新草稿">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)" title="删除">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div style="margin-top: 16px; display: flex; justify-content: flex-end">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- ==================== 内容编辑弹窗 ==================== -->
    <el-dialog
      v-model="editVisible"
      title="编辑内容"
      width="700px"
      destroy-on-close
    >
      <el-form label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" placeholder="请输入标题" clearable />
        </el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="editForm.body"
            type="textarea"
            :rows="10"
            placeholder="请输入正文内容"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tags" placeholder="多个标签以逗号分隔" clearable />
        </el-form-item>
        <!-- 定时发布日期选择器（视觉占位，后端尚未支持该参数） -->
        <el-form-item label="定时发布">
          <el-date-picker
            v-model="editForm.scheduledTime"
            type="datetime"
            placeholder="选择定时发布时间（可选）"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px">
            设置后内容将在指定时间自动发布（需后端支持 scheduledTime 参数）
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <!-- Wave 4.3: autosave 状态指示器 -->
          <span style="font-size: 12px; color: #909399">
            {{ autosaveStatus || '编辑超过 3 秒会自动保存草稿' }}
          </span>
          <div>
            <el-button @click="editVisible = false">关闭</el-button>
            <el-button type="primary" :loading="editSaving" @click="handleEditSave">立即保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- ==================== 内容详情弹窗（全屏居中大弹窗） ==================== -->
    <el-dialog
      v-model="detailVisible"
      :title="detailContent?.title || '内容详情'"
      fullscreen
      class="detail-dialog"
    >
      <!-- 详情弹窗头部操作按钮 -->
      <template #header="{ titleId, titleClass }">
        <div style="display: flex; align-items: center; justify-content: space-between; padding-right: 48px">
          <span :id="titleId" :class="titleClass">{{ detailContent?.title || '内容详情' }}</span>
          <div style="display: flex; gap: 8px">
            <el-button size="small" @click="handleVersionHistory">
              <el-icon style="margin-right: 4px"><Clock /></el-icon>
              版本历史
            </el-button>
            <el-button size="small" type="primary" @click="handleExport">
              <el-icon style="margin-right: 4px"><Download /></el-icon>
              导出
            </el-button>
          </div>
        </div>
      </template>

      <template v-if="detailContent">
        <div class="detail-layout">
          <!-- 左侧：文案内容 -->
          <div class="detail-left">
            <el-descriptions :column="2" border size="default" style="margin-bottom: 16px">
              <el-descriptions-item label="平台">
                <el-tag :type="getPlatformType(detailContent.platform)" size="small">
                  {{ getPlatformLabel(detailContent.platform) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="getStatusType(detailContent.status)" size="small">
                  {{ getStatusLabel(detailContent.status) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="模型">
                {{ detailContent.model || '-' }}
                <!-- Wave 3.4: 如果走了 LLM fallback，显示提示 -->
                <el-tag
                  v-if="detailContent.fallback"
                  type="warning"
                  size="small"
                  style="margin-left: 6px"
                  effect="dark"
                  title="主 provider 失败/熔断，本次响应来自 fallback 链路"
                >
                  ⚠ 已切换到 {{ detailContent.providerUsed || '备用' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="Token">{{ detailContent.tokenUsage ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间" :span="2">{{ detailContent.createTime || '-' }}</el-descriptions-item>
              <el-descriptions-item v-if="detailContent.scheduledTime" label="定时发布" :span="2">
                <span style="color: #e6a23c">{{ detailContent.scheduledTime }}</span>
              </el-descriptions-item>
            </el-descriptions>
            <div class="detail-body markdown-body" v-html="renderMd(detailContent.body)">
            </div>
          </div>
          <!-- 右侧：关联配图 -->
          <div class="detail-right">
            <h4 style="margin: 0 0 12px; color: #303133">关联配图</h4>
            <div v-if="detailMediaLoading" v-loading="true" style="height: 200px" />
            <div v-else-if="detailMediaList.length > 0" class="detail-media-grid">
              <div v-for="m in detailMediaList" :key="m.id" class="detail-media-item" @click="detailPreviewUrl = m.fileUrl; detailPreviewVisible = true">
                <img :src="m.fileUrl" :alt="m.fileName" />
                <div class="detail-media-name">{{ m.tags || m.fileName }}</div>
              </div>
            </div>
            <el-empty v-else description="暂无关联配图" :image-size="80" />
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- ==================== 版本历史抽屉 ==================== -->
    <el-drawer
      v-model="versionDrawerVisible"
      title="版本历史"
      direction="rtl"
      size="500px"
    >
      <div v-loading="versionLoading">
        <el-timeline v-if="versionList.length > 0">
          <el-timeline-item
            v-for="(ver, idx) in versionList"
            :key="idx"
            :timestamp="ver.createTime || ver.updatedAt || ''"
            placement="top"
          >
            <el-card shadow="hover" style="margin-bottom: 8px">
              <div style="font-size: 13px; color: #606266">
                <div v-if="ver.title" style="font-weight: 600; margin-bottom: 4px">{{ ver.title }}</div>
                <div style="white-space: pre-wrap; max-height: 200px; overflow-y: auto; color: #909399">
                  {{ ver.body || ver.content || '（无内容）' }}
                </div>
                <div v-if="ver.version" style="margin-top: 6px; color: #c0c4cc; font-size: 12px">
                  版本号: {{ ver.version }}
                </div>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else-if="!versionLoading" description="暂无版本历史" :image-size="80" />
      </div>
    </el-drawer>

    <!-- 配图预览 -->
    <el-dialog v-model="detailPreviewVisible" title="图片预览" width="60%" align-center>
      <img :src="detailPreviewUrl" style="width: 100%; border-radius: 8px" />
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-layout {
  display: flex;
  gap: 24px;
  height: calc(100vh - 120px);
}
.detail-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.detail-body {
  flex: 1;
  line-height: 1.8;
  font-size: 14px;
  color: #303133;
  overflow-y: auto;
  background: #fafafa;
  border-radius: 8px;
  padding: 16px;
}
.detail-right {
  width: 320px;
  flex-shrink: 0;
  overflow-y: auto;
}
.detail-media-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-media-item {
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #ebeef5;
  transition: box-shadow 0.2s;
}
.detail-media-item:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.12);
}
.detail-media-item img {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  display: block;
}
.detail-media-name {
  padding: 6px 8px;
  font-size: 12px;
  color: #909399;
}

/* 全屏弹窗关闭按钮样式 */
:deep(.detail-dialog .el-dialog__headerbtn) {
  width: 40px;
  height: 40px;
  font-size: 24px;
  background: #f56c6c;
  border-radius: 50%;
  top: 12px;
  right: 16px;
}
:deep(.detail-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: #fff;
  font-size: 20px;
}
:deep(.detail-dialog .el-dialog__headerbtn:hover) {
  background: #e04040;
}
/* Markdown 渲染样式 */
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3), .markdown-body :deep(h4) { margin: 16px 0 8px; font-weight: 600; color: #303133; }
.markdown-body :deep(h1) { font-size: 20px; border-bottom: 1px solid #ebeef5; padding-bottom: 8px; }
.markdown-body :deep(h2) { font-size: 17px; }
.markdown-body :deep(h3) { font-size: 15px; }
.markdown-body :deep(p) { margin: 8px 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; }
.markdown-body :deep(strong) { color: #303133; }
.markdown-body :deep(blockquote) { border-left: 4px solid #409eff; padding: 8px 16px; background: #f5f7fa; color: #606266; margin: 8px 0; }
.markdown-body :deep(code) { background: #f5f7fa; padding: 2px 6px; border-radius: 3px; font-size: 13px; }
.markdown-body :deep(hr) { border: none; border-top: 1px solid #ebeef5; margin: 16px 0; }
</style>
