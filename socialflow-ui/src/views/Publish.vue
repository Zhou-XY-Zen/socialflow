<!--
  ============================================================
  Publish.vue —— 分发中心页面
  ============================================================
  功能：
  1. 展示各平台发布状态（辅助发布 / API 对接中）
  2. 一键复制文案：选择内容 → 准备发布 → 复制文案
  3. 查看发布任务列表
  ============================================================
-->

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { contentApi } from '@/api/content'
import { publishApi } from '@/api/publish'
import type { ContentVO } from '@/types/api'
import type { PublishResult, PublishTaskVO } from '@/api/publish'

/** 平台卡片数据 */
const platforms = [
  {
    name: '小红书',
    icon: 'Notebook',
    color: '#ff2442',
    status: '辅助发布',
    statusType: 'danger' as const,
    description: '复制文案到小红书 App，支持 Emoji 和话题标签自动格式化',
  },
  {
    name: '抖音',
    icon: 'VideoPlay',
    color: '#000000',
    status: '辅助发布',
    statusType: '' as const,
    description: '复制视频描述到抖音 App，自动添加话题标签',
  },
  {
    name: '朋友圈',
    icon: 'ChatDotRound',
    color: '#07c160',
    status: '辅助发布',
    statusType: 'success' as const,
    description: '复制精简文案到微信朋友圈，风格简洁正式',
  },
  {
    name: '公众号',
    icon: 'Reading',
    color: '#576b95',
    status: '辅助发布',
    statusType: 'warning' as const,
    description: '复制文案到公众号后台「新的创作」发布（自动发布需认证公众号）',
  },
]

/** 所有内容列表 */
const contentList = ref<ContentVO[]>([])
/** 选中的平台（用于筛选内容） */
const selectedPlatform = ref('')
/** 选中的内容 ID */
const selectedContentId = ref<number | undefined>(undefined)
/** 准备发布的结果 */
const prepareResult = ref<PublishResult | null>(null)
/** 格式化后的文案文本 */
const formattedText = ref('')
/** 准备发布中 loading */
const preparing = ref(false)
/** 内容加载中 */
const loadingContent = ref(false)
/** 发布任务列表 */
const publishTasks = ref<PublishTaskVO[]>([])
/** 任务加载中 */
const loadingTasks = ref(false)
/** 关联的配图列表 */
const mediaList = ref<any[]>([])
/** 加载配图中 */
const loadingMedia = ref(false)
/** 自动发布中 loading */
const autoPublishing = ref(false)

/** 按平台筛选后的内容列表 */
const filteredContentList = computed(() => {
  if (!selectedPlatform.value) return contentList.value
  return contentList.value.filter(c => c.platform === selectedPlatform.value)
})

/** 当前选中的内容对象 */
const selectedContent = computed(() => {
  if (!selectedContentId.value) return null
  return contentList.value.find(c => c.id === selectedContentId.value) || null
})

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
 * 获取标题显示文本
 */
function getDisplayTitle(row: ContentVO): string {
  if (row.title) return row.title
  if (row.body) return row.body.substring(0, 40) + (row.body.length > 40 ? '...' : '')
  return '无标题'
}

/**
 * 获取下拉选项的标签（平台 + 标题）
 */
function getOptionLabel(item: ContentVO): string {
  const platform = getPlatformLabel(item.platform)
  const title = getDisplayTitle(item)
  return `[${platform}] ${title}`
}

/**
 * 加载内容列表
 */
async function loadContentList() {
  loadingContent.value = true
  try {
    const result = await contentApi.list({ pageNum: 1, pageSize: 100 })
    contentList.value = result.records
  } catch (e) {
    /* 错误已由拦截器处理 */
  } finally {
    loadingContent.value = false
  }
}

/**
 * 加载发布任务列表
 */
async function loadTasks() {
  loadingTasks.value = true
  try {
    publishTasks.value = await publishApi.tasks()
  } catch (e) {
    /* 错误已由拦截器处理 */
  } finally {
    loadingTasks.value = false
  }
}

/**
 * 准备发布 —— 调用后端 prepare 接口获取格式化文案
 */
async function handlePrepare() {
  if (!selectedContentId.value) {
    ElMessage.warning('请先选择要发布的内容')
    return
  }
  preparing.value = true
  prepareResult.value = null
  formattedText.value = ''
  mediaList.value = []
  try {
    const result = await publishApi.prepare(selectedContentId.value)
    prepareResult.value = result
    // bundleUrl 中存放的是格式化后的文案文本
    formattedText.value = result.bundleUrl || ''
    ElMessage.success('文案已准备完成，可以复制了')
    // 加载关联配图
    loadMedia()
  } catch (e) {
    /* 错误已由拦截器处理 */
  } finally {
    preparing.value = false
  }
}

/**
 * 复制文案 + 图片链接到剪贴板
 */
async function handleCopy() {
  if (!formattedText.value) {
    ElMessage.warning('没有可复制的文案，请先准备发布')
    return
  }
  // 拼接文案 + 图片链接
  let fullText = formattedText.value
  if (mediaList.value.length > 0) {
    fullText += '\n\n--- 配图链接 ---\n'
    mediaList.value.forEach((m: any, i: number) => {
      fullText += `${i + 1}. ${m.fileUrl}\n`
    })
  }
  try {
    await navigator.clipboard.writeText(fullText)
    ElMessage.success(mediaList.value.length > 0
      ? `文案 + ${mediaList.value.length}张配图链接已复制`
      : '文案已复制到剪贴板')
  } catch (e) {
    const textarea = document.createElement('textarea')
    textarea.value = fullText
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('文案已复制到剪贴板')
  }
}

/**
 * 加载内容关联的配图
 */
async function loadMedia() {
  if (!selectedContentId.value) return
  loadingMedia.value = true
  try {
    const res = await contentApi.get(selectedContentId.value)
    // 如果内容有关联配图，通过绑定接口获取
    // 注意：实际配图通过 /content/{id}/media 接口获取
    // 这里使用 fetch 直接调用，因为 contentApi 未封装此接口
    const http = (await import('@/api/http')).default
    const mediaRes = await http.get<any>(`/content/${selectedContentId.value}/media`)
    if (mediaRes.data && mediaRes.data.data) {
      mediaList.value = mediaRes.data.data
    }
  } catch (e) {
    // 配图加载失败不影响主流程
    mediaList.value = []
  } finally {
    loadingMedia.value = false
  }
}

/**
 * 获取任务状态标签类型
 */
function getTaskStatusType(status: string): string {
  const map: Record<string, string> = {
    PENDING: 'info',
    EXECUTING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
  }
  return map[status] || 'info'
}

/**
 * 获取任务状态显示文本
 */
function getTaskStatusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '等待中',
    EXECUTING: '执行中',
    SUCCESS: '已成功',
    FAILED: '已失败',
  }
  return map[status] || status
}

/**
 * 逐个下载配图（浏览器会弹出保存对话框）
 */
function handleDownloadImages() {
  if (mediaList.value.length === 0) return
  mediaList.value.forEach((m: any, i: number) => {
    const link = document.createElement('a')
    link.href = m.fileUrl
    link.download = m.fileName || `配图_${i + 1}.jpg`
    link.target = '_blank'
    document.body.appendChild(link)
    // 延迟触发，避免浏览器拦截多个下载
    setTimeout(() => {
      link.click()
      document.body.removeChild(link)
    }, i * 500)
  })
  ElMessage.success(`正在下载 ${mediaList.value.length} 张配图...`)
}

/**
 * 自动发布到微信公众号
 */
async function handleAutoPublish() {
  if (!selectedContentId.value) {
    ElMessage.warning('请先选择要发布的内容')
    return
  }
  autoPublishing.value = true
  try {
    const result = await publishApi.autoPublish(selectedContentId.value, 'WECHAT_MP')
    if (result.success) {
      ElMessage.success('已成功发布到微信公众号')
      // 刷新任务列表
      loadTasks()
    } else {
      ElMessage.error(result.errorMessage || '发布失败，请稍后重试')
    }
  } catch (e) {
    /* 错误已由拦截器处理 */
  } finally {
    autoPublishing.value = false
  }
}

onMounted(() => {
  loadContentList()
  loadTasks()
})
</script>

<template>
  <div>
    <!-- 平台状态卡片 -->
    <el-row :gutter="16" style="margin-bottom: 20px">
      <el-col
        v-for="p in platforms"
        :key="p.name"
        :xs="24"
        :sm="12"
        :md="12"
        :lg="6"
        style="margin-bottom: 16px"
      >
        <el-card shadow="hover" style="height: 100%">
          <div style="text-align: center; padding: 12px 0">
            <el-icon :size="40" :color="p.color">
              <component :is="p.icon" />
            </el-icon>
            <h3 style="margin: 12px 0 8px">{{ p.name }}</h3>
            <el-tag size="small" :type="p.statusType" style="margin-bottom: 12px">
              {{ p.status }}
            </el-tag>
            <p style="color: #909399; font-size: 13px; line-height: 1.6; margin: 0">
              {{ p.description }}
            </p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 一键复制区域 -->
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; align-items: center">
          <el-icon style="margin-right: 6px"><CopyDocument /></el-icon>
          <span>一键复制</span>
        </div>
      </template>

      <!-- 先选平台，再选内容 -->
      <el-form label-width="100px">
        <el-form-item label="选择平台">
          <el-radio-group
            v-model="selectedPlatform"
            @change="() => { selectedContentId = undefined; prepareResult = null; formattedText = ''; mediaList = [] }"
          >
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="XIAOHONGSHU">小红书</el-radio-button>
            <el-radio-button value="DOUYIN">抖音</el-radio-button>
            <el-radio-button value="WECHAT_MOMENT">朋友圈</el-radio-button>
            <el-radio-button value="WECHAT_MP">公众号</el-radio-button>
          </el-radio-group>
          <el-tag style="margin-left: 12px" type="info" size="small">
            {{ filteredContentList.length }} 篇
          </el-tag>
        </el-form-item>

        <el-form-item label="选择内容">
          <el-select
            v-model="selectedContentId"
            placeholder="选择要发布的内容"
            filterable
            style="width: 100%"
            :loading="loadingContent"
            :disabled="filteredContentList.length === 0"
            @change="() => { prepareResult = null; formattedText = ''; mediaList = [] }"
          >
            <el-option
              v-for="item in filteredContentList"
              :key="item.id"
              :label="getOptionLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <!-- 选中内容的基本信息 -->
        <el-form-item v-if="selectedContent" label="内容信息">
          <el-descriptions :column="3" size="small" border>
            <el-descriptions-item label="平台">
              {{ getPlatformLabel(selectedContent.platform) }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              {{ selectedContent.status }}
            </el-descriptions-item>
            <el-descriptions-item label="Token">
              {{ selectedContent.tokenUsage || '--' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-form-item>

        <!-- 操作按钮 -->
        <el-form-item>
          <el-button
            type="primary"
            :loading="preparing"
            :disabled="!selectedContentId"
            @click="handlePrepare"
          >
            <el-icon style="margin-right: 4px"><Promotion /></el-icon>
            准备发布
          </el-button>
          <el-button
            type="success"
            :disabled="!formattedText"
            @click="handleCopy"
          >
            <el-icon style="margin-right: 4px"><DocumentCopy /></el-icon>
            复制文案{{ mediaList.length > 0 ? ' + 图片链接' : '' }}
          </el-button>
          <el-button
            v-if="mediaList.length > 0"
            type="warning"
            @click="handleDownloadImages"
          >
            <el-icon style="margin-right: 4px"><Download /></el-icon>
            下载全部配图（{{ mediaList.length }}张）
          </el-button>
          <el-tooltip
            v-if="selectedContent && selectedContent.platform === 'WECHAT_MP'"
            content="自动发布需认证公众号，当前为辅助发布模式：请复制文案后到公众号后台手动发布"
            placement="top"
          >
            <el-button
              type="info"
              disabled
            >
              <el-icon style="margin-right: 4px"><Upload /></el-icon>
              自动发布（需认证）
            </el-button>
          </el-tooltip>
        </el-form-item>

        <!-- 格式化后的文案预览 -->
        <el-form-item v-if="formattedText" label="文案预览">
          <el-input
            v-model="formattedText"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 14 }"
            readonly
            style="width: 100%"
          />
        </el-form-item>

        <!-- 关联配图 -->
        <el-form-item v-if="mediaList.length > 0" label="关联配图">
          <div style="display: flex; flex-wrap: wrap; gap: 12px">
            <el-card
              v-for="media in mediaList"
              :key="media.id"
              shadow="hover"
              style="width: 120px; padding: 0"
              :body-style="{ padding: '8px' }"
            >
              <el-image
                :src="media.fileUrl"
                fit="cover"
                style="width: 104px; height: 104px; border-radius: 4px"
              />
              <div style="font-size: 12px; color: #909399; text-align: center; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                {{ media.fileName || '配图' }}
              </div>
            </el-card>
          </div>
        </el-form-item>
        <el-form-item v-else-if="prepareResult && !loadingMedia" label="关联配图">
          <span style="color: #909399; font-size: 13px">暂无关联配图</span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 发布任务列表 -->
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <div style="display: flex; align-items: center">
            <el-icon style="margin-right: 6px"><List /></el-icon>
            <span>发布任务</span>
          </div>
          <el-button text type="primary" size="small" @click="loadTasks">
            <el-icon style="margin-right: 4px"><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <el-table :data="publishTasks" stripe v-loading="loadingTasks">
        <el-table-column prop="contentId" label="内容 ID" width="120" />
        <el-table-column label="发布类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.publishType === 'IMMEDIATE' ? '' : 'warning'">
              {{ row.publishType === 'IMMEDIATE' ? '立即发布' : '定时发布' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="getTaskStatusType(row.status)">
              {{ getTaskStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scheduledTime" label="排期时间" width="180" />
        <el-table-column prop="executedTime" label="执行时间" width="180" />
        <el-table-column prop="resultMsg" label="结果" min-width="200" show-overflow-tooltip />
        <el-table-column prop="retryCount" label="重试次数" width="90" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
      </el-table>
      <el-empty
        v-if="!loadingTasks && publishTasks.length === 0"
        description="暂无发布任务"
        :image-size="80"
      />
    </el-card>
  </div>
</template>
