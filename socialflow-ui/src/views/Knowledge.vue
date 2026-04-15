<!--
  ============================================================
  Knowledge.vue —— 知识库管理页面
  ============================================================
  三个功能区域：
    1. 知识库列表 —— 展示当前用户的所有知识库，支持创建和删除
    2. 知识库详情 —— 展示选中知识库中的文档列表，支持上传和删除文档
    3. 检索测试   —— 在选中的知识库中进行语义搜索测试
  ============================================================
-->

<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { kbApi } from '@/api/knowledge'
import type { KbVO, KbDocVO, ChunkSearchVO } from '@/types/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

// ==================== 知识库列表 ====================

/** 知识库列表数据 */
const kbList = ref<KbVO[]>([])
/** 列表加载状态 */
const kbLoading = ref(false)

/** 加载知识库列表 */
async function loadKbList() {
  kbLoading.value = true
  try {
    kbList.value = await kbApi.list()
  } finally {
    kbLoading.value = false
  }
}

// ==================== 创建知识库 ====================

/** 创建对话框是否可见 */
const createDialogVisible = ref(false)
/** 创建表单数据 */
const createForm = ref({
  name: '',
  description: '',
  category: ''
})
/** 创建按钮加载状态 */
const createLoading = ref(false)

/** 打开创建对话框 */
function openCreateDialog() {
  createForm.value = { name: '', description: '', category: '' }
  createDialogVisible.value = true
}

/** 提交创建知识库 */
async function handleCreate() {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  createLoading.value = true
  try {
    await kbApi.create({
      name: createForm.value.name.trim(),
      description: createForm.value.description.trim() || undefined,
      category: createForm.value.category || undefined
    })
    ElMessage.success('知识库创建成功')
    createDialogVisible.value = false
    await loadKbList()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e.message || '未知错误'))
  } finally {
    createLoading.value = false
  }
}

/** 删除知识库 */
async function handleDeleteKb(kb: KbVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除知识库「${kb.name}」吗？该操作将删除所有关联文档和向量数据，不可恢复。`,
      '确认删除',
      { type: 'warning' }
    )
    await kbApi.delete(kb.id)
    ElMessage.success('知识库已删除')
    // 如果正在查看该知识库，则返回列表
    if (selectedKb.value && selectedKb.value.id === kb.id) {
      handleBack()
    }
    await loadKbList()
  } catch {
    // 用户取消
  }
}

// ==================== 知识库详情（文档管理） ====================

/** 当前选中的知识库 */
const selectedKb = ref<KbVO | null>(null)
/** 文档列表 */
const docList = ref<KbDocVO[]>([])
/** 文档加载状态 */
const docLoading = ref(false)
/** 自动刷新定时器 ID */
let autoRefreshTimer: ReturnType<typeof setInterval> | null = null

/** 查看知识库详情 */
async function handleViewKb(kb: KbVO) {
  selectedKb.value = kb
  await loadDocList()
  startAutoRefresh()
}

/** 返回知识库列表 */
function handleBack() {
  selectedKb.value = null
  docList.value = []
  searchResults.value = []
  searchQuery.value = ''
  stopAutoRefresh()
}

/** 加载文档列表 */
async function loadDocList() {
  if (!selectedKb.value) return
  docLoading.value = true
  try {
    docList.value = await kbApi.listDocs(selectedKb.value.id)
  } finally {
    docLoading.value = false
  }
}

/** 是否有正在处理中的文档（用于判断是否需要自动刷新） */
const hasProcessingDocs = computed(() => {
  return docList.value.some(d => d.parseStatus === 'PENDING' || d.parseStatus === 'PARSING')
})

/** 启动自动刷新（当有文档正在处理时，每 3 秒刷新一次） */
function startAutoRefresh() {
  stopAutoRefresh()
  autoRefreshTimer = setInterval(async () => {
    if (hasProcessingDocs.value && selectedKb.value) {
      await loadDocList()
      // 同时刷新知识库列表以更新统计
      await loadKbList()
    }
  }, 3000)
}

/** 停止自动刷新 */
function stopAutoRefresh() {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

// ==================== 文档上传 ====================

/** el-upload 组件引用 */
const uploadRef = ref<UploadInstance>()
/** 上传加载状态 */
const uploadLoading = ref(false)

/** 手动上传文件 */
async function handleUpload(options: any) {
  if (!selectedKb.value) return
  uploadLoading.value = true
  try {
    await kbApi.upload(selectedKb.value.id, options.file)
    ElMessage.success('文档上传成功，正在后台解析...')
    await loadDocList()
    startAutoRefresh()
  } catch (e: any) {
    ElMessage.error('上传失败：' + (e.message || '未知错误'))
  } finally {
    uploadLoading.value = false
  }
}

/** 删除文档 */
async function handleDeleteDoc(doc: KbDocVO) {
  if (!selectedKb.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除文档「${doc.fileName}」吗？关联的分块和向量数据也会被清除。`,
      '确认删除',
      { type: 'warning' }
    )
    await kbApi.deleteDoc(selectedKb.value.id, doc.id)
    ElMessage.success('文档已删除')
    await loadDocList()
    await loadKbList()
  } catch {
    // 用户取消
  }
}

// ==================== 检索测试 ====================

/** 搜索查询文本 */
const searchQuery = ref('')
/** 返回结果数量 */
const searchTopK = ref(5)
/** 搜索结果 */
const searchResults = ref<ChunkSearchVO[]>([])
/** 搜索加载状态 */
const searchLoading = ref(false)

/** 执行搜索 */
async function handleSearch() {
  if (!selectedKb.value) return
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入搜索内容')
    return
  }
  searchLoading.value = true
  try {
    searchResults.value = await kbApi.search(selectedKb.value.id, searchQuery.value.trim(), searchTopK.value)
  } catch (e: any) {
    ElMessage.error('搜索失败：' + (e.message || '未知错误'))
  } finally {
    searchLoading.value = false
  }
}

// ==================== 工具函数 ====================

/** 格式化文件大小 */
function formatBytes(size: number): string {
  if (size < 1024) return size + 'B'
  if (size < 1048576) return (size / 1024).toFixed(1) + 'KB'
  return (size / 1048576).toFixed(1) + 'MB'
}

/** 获取解析状态对应的 Tag 类型 */
function parseStatusType(status: string): string {
  switch (status) {
    case 'PENDING': return 'info'
    case 'PARSING': return 'warning'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}

/** 获取解析状态的中文显示 */
function parseStatusLabel(status: string): string {
  switch (status) {
    case 'PENDING': return '待处理'
    case 'PARSING': return '解析中'
    case 'COMPLETED': return '已完成'
    case 'FAILED': return '失败'
    default: return status
  }
}

// ==================== 生命周期 ====================

onMounted(loadKbList)

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<template>
  <div class="sf-page-container">
    <!-- 页面头部 -->
    <PageHeader title="知识库" subtitle="上传文档供 AI 生成时参考" icon="Reading" />

    <!-- ====== 第一区域：知识库列表（始终可见） ====== -->
    <el-card v-loading="kbLoading" style="margin-bottom: 16px">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span style="font-size: 16px; font-weight: bold">我的知识库</span>
        <div>
          <el-button type="primary" @click="openCreateDialog">创建</el-button>
          <el-button @click="loadKbList">刷新</el-button>
        </div>
      </div>
    </template>

    <el-table :data="kbList" stripe>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="docCount" label="文档数" width="90" align="center" />
      <el-table-column prop="chunkCount" label="分块数" width="90" align="center" />
      <el-table-column prop="embeddingModel" label="嵌入模型" width="160" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="150" align="center">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleViewKb(row)">查看</el-button>
          <el-button type="danger" link @click="handleDeleteKb(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <!-- ====== 第二区域：知识库详情 - 文档管理（选中知识库后显示） ====== -->
  <el-card v-if="selectedKb" v-loading="docLoading" style="margin-bottom: 16px">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span style="font-size: 16px; font-weight: bold">{{ selectedKb.name }} - 文档列表</span>
        <div>
          <el-upload
            ref="uploadRef"
            :http-request="handleUpload"
            :show-file-list="false"
            accept=".pdf,.docx,.doc,.txt,.md,.html"
          >
            <el-button type="success" :loading="uploadLoading">上传文档</el-button>
          </el-upload>
          <el-button style="margin-left: 8px" @click="handleBack">返回</el-button>
        </div>
      </div>
    </template>

    <el-table :data="docList" stripe>
      <el-table-column prop="fileName" label="文件名" min-width="200" />
      <el-table-column prop="fileType" label="类型" width="80" align="center" />
      <el-table-column label="大小" width="100" align="center">
        <template #default="{ row }">
          {{ formatBytes(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="分块数" width="90" align="center">
        <template #default="{ row }">
          {{ row.chunkCount ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column label="解析状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag
            :type="parseStatusType(row.parseStatus)"
            size="small"
          >
            <template v-if="row.parseStatus === 'PARSING'">
              <el-icon class="is-loading" style="margin-right: 4px"><Loading /></el-icon>
            </template>
            {{ parseStatusLabel(row.parseStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ row }">
          <el-button type="danger" link @click="handleDeleteDoc(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <EmptyState
      v-if="docList.length === 0 && !docLoading"
      icon="Document"
      title="暂无文档"
      description="请上传文档以供 AI 参考"
      size="small"
    />
  </el-card>

  <!-- ====== 第三区域：检索测试（选中知识库后显示） ====== -->
  <el-card v-if="selectedKb">
    <template #header>
      <span style="font-size: 16px; font-weight: bold">检索测试</span>
    </template>

    <div style="display: flex; gap: 12px; margin-bottom: 16px; align-items: center">
      <el-input
        v-model="searchQuery"
        placeholder="输入查询内容进行语义检索..."
        clearable
        style="flex: 1"
        @keyup.enter="handleSearch"
      />
      <el-input-number
        v-model="searchTopK"
        :min="1"
        :max="20"
        controls-position="right"
        style="width: 120px"
        placeholder="TopK"
      />
      <el-button type="primary" :loading="searchLoading" @click="handleSearch">搜索</el-button>
    </div>

    <div v-if="searchResults.length > 0">
      <el-card
        v-for="(item, index) in searchResults"
        :key="item.chunkId"
        shadow="hover"
        style="margin-bottom: 12px"
      >
        <div style="display: flex; justify-content: space-between; margin-bottom: 8px">
          <span style="color: #606266; font-size: 13px">
            {{ item.docName || '未知文档' }} - 片段 #{{ item.chunkIndex }}
          </span>
          <el-tag type="warning" size="small" v-if="item.score != null">
            相似度: {{ item.score.toFixed(4) }}
          </el-tag>
        </div>
        <div style="white-space: pre-wrap; line-height: 1.6; color: #303133; font-size: 14px">
          {{ item.contentText }}
        </div>
      </el-card>
    </div>

    <EmptyState
      v-if="searchResults.length === 0 && !searchLoading"
      icon="Search"
      title="开始检索"
      description="输入查询内容开始检索"
      size="small"
    />
  </el-card>

  <!-- ====== 创建知识库对话框 ====== -->
  <el-dialog v-model="createDialogVisible" title="创建知识库" width="480px">
    <el-form label-width="80px">
      <el-form-item label="名称" required>
        <el-input v-model="createForm.name" placeholder="请输入知识库名称" maxlength="50" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="createForm.description"
          type="textarea"
          :rows="3"
          placeholder="简要描述知识库的用途和内容"
          maxlength="200"
        />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="createForm.category" placeholder="选择分类" clearable style="width: 100%">
          <el-option label="产品资料" value="产品资料" />
          <el-option label="行业报告" value="行业报告" />
          <el-option label="品牌素材" value="品牌素材" />
          <el-option label="营销方案" value="营销方案" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="createLoading" @click="handleCreate">确定</el-button>
    </template>
  </el-dialog>
  </div>
</template>

<script lang="ts">
import { Loading } from '@element-plus/icons-vue'
export default { components: { Loading } }
</script>
