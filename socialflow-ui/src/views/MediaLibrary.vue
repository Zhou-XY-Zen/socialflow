<!--
  ============================================================
  MediaLibrary.vue —— 素材库页面
  ============================================================
  功能区域：
    1. 顶部操作栏 —— 上传按钮、文件类型筛选、关键词搜索
    2. 素材网格   —— 以卡片网格展示图片/视频素材，支持预览和删除
    3. 分页控件   —— 底部分页导航
    4. 上传对话框 —— 拖拽上传，支持图片和视频
    5. 预览对话框 —— 点击素材查看大图
  ============================================================
-->

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { mediaApi, type MediaAssetVO } from '@/api/media'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, UploadFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

// ==================== 素材列表数据 ====================

/** 素材列表 */
const mediaList = ref<MediaAssetVO[]>([])
/** 列表加载状态 */
const loading = ref(false)
/** 分页参数 */
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

/** 筛选条件 */
const filterType = ref('')
const keyword = ref('')

/** 加载素材列表 */
async function loadList() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    if (filterType.value) {
      params.fileType = filterType.value
    }
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }
    const result = await mediaApi.list(params)
    mediaList.value = result.records
    total.value = result.total
  } catch (e: any) {
    ElMessage.error('加载素材列表失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

/** 搜索按钮点击 */
function handleSearch() {
  pageNum.value = 1
  loadList()
}

/** 翻页 */
function handlePageChange(page: number) {
  pageNum.value = page
  loadList()
}

/** 每页条数改变 */
function handleSizeChange(size: number) {
  pageSize.value = size
  pageNum.value = 1
  loadList()
}

// ==================== 上传功能 ====================

/** 上传对话框是否可见 */
const uploadDialogVisible = ref(false)
/** 上传中状态 */
const uploading = ref(false)
/** 上传标签 */
const uploadTags = ref('')

/** 打开上传对话框 */
function openUploadDialog() {
  uploadTags.value = ''
  uploadDialogVisible.value = true
}

/** 自定义上传处理 */
async function handleUpload(options: { file: File }) {
  uploading.value = true
  try {
    await mediaApi.upload(options.file, uploadTags.value.trim() || undefined)
    ElMessage.success('上传成功')
    // 上传成功后刷新列表
    await loadList()
  } catch (e: any) {
    ElMessage.error('上传失败：' + (e.message || '未知错误'))
  } finally {
    uploading.value = false
  }
}

// ==================== 删除功能 ====================

/** 删除素材 */
async function handleDelete(item: MediaAssetVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除素材「${item.fileName}」吗？此操作将同时删除存储中的文件，不可恢复。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await mediaApi.delete(item.id)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e: any) {
    // 用户取消不提示
    if (e === 'cancel' || e?.toString?.().includes('cancel')) return
    ElMessage.error('删除失败：' + (e.message || '未知错误'))
  }
}

// ==================== 预览功能 ====================

/** 预览对话框是否可见 */
const previewVisible = ref(false)
/** 当前预览的素材 */
const previewItem = ref<MediaAssetVO | null>(null)

/** 点击素材卡片预览 */
function handlePreview(item: MediaAssetVO) {
  previewItem.value = item
  previewVisible.value = true
}

// ==================== 工具函数 ====================

/** 格式化文件大小 */
function formatSize(size: number): string {
  if (size < 1024) return size + 'B'
  if (size < 1048576) return (size / 1024).toFixed(1) + 'KB'
  return (size / 1048576).toFixed(1) + 'MB'
}

/** 判断是否为图片类型 */
function isImage(item: MediaAssetVO): boolean {
  return item.mimeType?.startsWith('image/') ?? false
}

// ==================== 生命周期 ====================

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="media-library sf-page-container">
    <!-- 页面头部 -->
    <PageHeader title="素材库" subtitle="管理图片、视频等素材文件" icon="Picture" />

    <!-- 顶部操作栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" @click="openUploadDialog">
            <el-icon><Upload /></el-icon>
            <span style="margin-left: 4px">上传素材</span>
          </el-button>
        </div>
        <div class="toolbar-right">
          <el-select
            v-model="filterType"
            placeholder="全部类型"
            clearable
            style="width: 130px"
            @change="handleSearch"
          >
            <el-option label="全部类型" value="" />
            <el-option label="图片" value="IMAGE" />
            <el-option label="视频" value="VIDEO" />
          </el-select>
          <el-input
            v-model="keyword"
            placeholder="搜索文件名或标签"
            clearable
            style="width: 220px; margin-left: 10px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" style="margin-left: 10px" @click="handleSearch">
            <el-icon><Search /></el-icon>
            <span style="margin-left: 4px">搜索</span>
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 素材网格 -->
    <div v-loading="loading" class="media-grid-wrapper">
      <!-- 空状态 -->
      <EmptyState
        v-if="!loading && mediaList.length === 0"
        icon="Picture"
        title="暂无素材"
        description="点击上传添加你的第一个素材"
        actionText="上传素材"
        @action="openUploadDialog"
      />

      <!-- 素材卡片网格（一行 5 张） -->
      <div v-else class="media-grid">
        <div
          v-for="item in mediaList"
          :key="item.id"
        >
          <div class="media-card" @click="handlePreview(item)">
            <!-- 缩略图区域 -->
            <div class="media-thumb">
              <img
                v-if="isImage(item)"
                :src="item.fileUrl"
                :alt="item.fileName"
                class="thumb-img"
              />
              <video
                v-else
                :src="item.fileUrl"
                preload="metadata"
                muted
                class="thumb-img"
                style="pointer-events: none"
              />
              <div v-if="!isImage(item)" class="video-badge">
                <el-icon :size="16"><VideoCamera /></el-icon>
                <span>视频</span>
              </div>
              <!-- 右上角删除按钮（悬浮显示） -->
              <el-button
                class="media-delete-btn"
                type="danger"
                :icon="Delete"
                circle
                size="small"
                @click.stop="handleDelete(item)"
              />
            </div>
            <!-- 信息区域 -->
            <div class="media-info">
              <div class="media-name" :title="item.fileName">{{ item.fileName }}</div>
              <div class="media-meta">
                <span>{{ formatSize(item.fileSize) }}</span>
                <span v-if="item.tags" class="media-tags">{{ item.tags }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 上传对话框 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传素材"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px">
        <el-form-item label="标签">
          <el-input
            v-model="uploadTags"
            placeholder="输入标签，多个用逗号分隔（可选）"
          />
        </el-form-item>
        <el-form-item label="文件">
          <el-upload
            drag
            :auto-upload="true"
            :show-file-list="true"
            :http-request="handleUpload"
            accept="image/*,video/*"
            :disabled="uploading"
            multiple
          >
            <el-icon :size="40" style="color: #909399"><UploadFilled /></el-icon>
            <div style="margin-top: 8px; color: #606266">
              将文件拖拽到此处，或 <em style="color: #409eff">点击上传</em>
            </div>
            <template #tip>
              <div style="color: #909399; font-size: 12px; margin-top: 8px">
                支持 JPG、PNG、GIF、MP4 等常见图片和视频格式
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 预览对话框（图片+视频通用，全屏居中大图） -->
    <el-dialog
      v-model="previewVisible"
      :title="previewItem?.fileName || '素材预览'"
      width="50%"
      :close-on-click-modal="true"
      align-center
      class="preview-dialog"
    >
      <div v-if="previewItem" class="preview-content">
        <img
          v-if="isImage(previewItem)"
          :src="previewItem.fileUrl"
          :alt="previewItem.fileName"
          class="preview-img"
        />
        <video
          v-else
          :src="previewItem.fileUrl"
          controls
          autoplay
          class="preview-video"
        />
      </div>
      <div v-if="previewItem" class="preview-meta">
        <span>文件名：{{ previewItem.fileName }}</span>
        <span>大小：{{ formatSize(previewItem.fileSize) }}</span>
        <span>类型：{{ previewItem.mimeType }}</span>
        <span v-if="previewItem.tags">标签：{{ previewItem.tags }}</span>
        <span v-if="previewItem.createTime">上传时间：{{ previewItem.createTime }}</span>
      </div>
    </el-dialog>
  </div>
</template>


<style scoped>
.media-library {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar-card {
  margin-bottom: 0;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.toolbar-left {
  display: flex;
  align-items: center;
}

.toolbar-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.media-grid-wrapper {
  min-height: 300px;
}

/* 素材卡片网格：一行 5 张 */
.media-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

/* 素材卡片 */
.media-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
  background: #fff;
}

.media-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

/* 缩略图区域 */
.media-thumb {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 视频角标 */
.video-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
  pointer-events: none;
}

/* 右上角删除按钮 */
.media-delete-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  opacity: 0;
  transition: opacity 0.2s;
}

.media-card:hover .media-delete-btn {
  opacity: 1;
}

/* 信息区域 */
.media-info {
  padding: 10px 12px;
}

.media-name {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.media-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.media-tags {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 16px 0;
}

/* 预览弹窗 */
.preview-content {
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
}

.preview-img {
  max-width: 100%;
  max-height: 65vh;
  object-fit: contain;
  border-radius: 8px;
}

.preview-video {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  background: #000;
}

.preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: #606266;
  padding: 12px 0 0;
}
</style>
