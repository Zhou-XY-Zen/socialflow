<!--
  ============================================================
  Calendar.vue —— 内容日历页面
  ============================================================
  使用 Element Plus 的 el-calendar 组件展示月历视图，
  在有内容生成的日期上显示圆点标记。
  点击日期可查看该日期下的内容列表。
  ============================================================
-->

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { contentApi } from '@/api/content'
import type { ContentVO } from '@/types/api'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

/** 所有内容列表 */
const allContent = ref<ContentVO[]>([])
/** 加载状态 */
const loading = ref(false)
/** 当前选中的日期 */
const selectedDate = ref(new Date())

/**
 * 加载内容数据
 */
async function loadData() {
  loading.value = true
  try {
    const result = await contentApi.list({ pageNum: 1, pageSize: 200 })
    allContent.value = result.records
  } catch (e) {
    /* 错误已由拦截器处理 */
  } finally {
    loading.value = false
  }
}

/**
 * 按日期分组的内容映射表
 * key: 日期字符串（YYYY-MM-DD），value: 该日期下的内容列表
 */
const contentByDate = computed(() => {
  const map: Record<string, ContentVO[]> = {}
  allContent.value.forEach(item => {
    if (item.createTime) {
      const dateKey = item.createTime.substring(0, 10)
      if (!map[dateKey]) map[dateKey] = []
      map[dateKey].push(item)
    }
  })
  return map
})

/**
 * 检查指定日期是否有内容
 */
function hasContent(date: Date): boolean {
  const dateKey = formatDate(date)
  return !!contentByDate.value[dateKey]
}

/**
 * 获取指定日期的内容数量
 */
function getContentCount(date: Date): number {
  const dateKey = formatDate(date)
  return contentByDate.value[dateKey]?.length || 0
}

/**
 * 格式化日期为 YYYY-MM-DD 字符串
 */
function formatDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 当前选中日期对应的内容列表 */
const selectedDateContent = computed(() => {
  const dateKey = formatDate(selectedDate.value)
  return contentByDate.value[dateKey] || []
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
 * 获取标题显示文本
 */
function getDisplayTitle(row: ContentVO): string {
  if (row.title) return row.title
  if (row.body) return row.body.substring(0, 30) + (row.body.length > 30 ? '...' : '')
  return '无标题'
}

onMounted(loadData)
</script>

<template>
  <div v-loading="loading" class="sf-page-container">
    <PageHeader
      title="内容日历"
      subtitle="按日期查看和管理内容"
      icon="Calendar"
    />
    <el-row :gutter="20">
      <!-- 日历区域 -->
      <el-col :span="16">
        <el-card>
          <template #header>
            <div style="display: flex; align-items: center">
              <el-icon style="margin-right: 8px"><Calendar /></el-icon>
              <span>内容日历</span>
            </div>
          </template>
          <el-calendar v-model="selectedDate">
            <template #date-cell="{ data }">
              <div style="height: 100%; position: relative">
                <span>{{ data.day.split('-')[2] }}</span>
                <!-- 有内容的日期显示标记 -->
                <div
                  v-if="hasContent(new Date(data.day))"
                  style="
                    position: absolute;
                    bottom: 4px;
                    left: 50%;
                    transform: translateX(-50%);
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    min-width: 22px;
                    height: 18px;
                    padding: 0 6px;
                    border-radius: 9px;
                    background: var(--sf-gradient);
                    color: #fff;
                    font-size: 11px;
                    font-weight: 600;
                    line-height: 1;
                    box-shadow: var(--sf-shadow-sm);
                  "
                >
                  {{ getContentCount(new Date(data.day)) }}
                </div>
              </div>
            </template>
          </el-calendar>
        </el-card>
      </el-col>

      <!-- 选中日期的内容列表 -->
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>{{ formatDate(selectedDate) }} 的内容</span>
          </template>
          <div v-if="selectedDateContent.length > 0">
            <div
              v-for="item in selectedDateContent"
              :key="item.id"
              style="
                padding: 12px;
                border-bottom: 1px solid #ebeef5;
                margin-bottom: 8px;
              "
            >
              <div style="font-weight: 500; margin-bottom: 6px">
                {{ getDisplayTitle(item) }}
              </div>
              <div style="display: flex; align-items: center; gap: 8px">
                <el-tag :type="getPlatformType(item.platform)" size="small">
                  {{ getPlatformLabel(item.platform) }}
                </el-tag>
                <span style="font-size: 12px; color: #909399">{{ item.model }}</span>
              </div>
            </div>
          </div>
          <EmptyState
            v-else
            icon="Calendar"
            title="这天没有内容"
            description="选择其他日期查看"
            size="small"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
