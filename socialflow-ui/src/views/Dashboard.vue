<!--
  ============================================================
  Dashboard.vue —— 数据看板页面
  ============================================================
  以统计卡片展示概览数据（总内容数、平台数、今日生成、Token 消耗、
  平均 Token、最常用平台），底部展示平台分布、Token 消耗排行、
  近7天生成趋势、最近生成的内容表（含查看详情）。
  ============================================================
-->

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { contentApi } from '@/api/content'
import { dashboardApi } from '@/api/dashboard'
import type { ContentVO, DashboardOverviewVO } from '@/types/api'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'

/** 所有内容列表（最多加载 100 条用于统计 + 最近内容表展示） */
const allContent = ref<ContentVO[]>([])
/** Wave 3.2: 后端聚合的总览数据（优先用，client-side 聚合作 fallback） */
const overview = ref<DashboardOverviewVO | null>(null)
/** 加载状态 */
const loading = ref(false)
/** 详情弹窗可见性 */
const detailDialogVisible = ref(false)
/** 当前查看的内容 */
const currentDetail = ref<ContentVO | null>(null)

/**
 * 加载数据：并行拉后端 overview + 内容列表，前者失败时降级到 client-side 聚合。
 */
async function loadData() {
  loading.value = true
  try {
    const [overviewResult, listResult] = await Promise.allSettled([
      dashboardApi.overview(),
      contentApi.list({ pageNum: 1, pageSize: 100 }),
    ])
    if (overviewResult.status === 'fulfilled') {
      overview.value = overviewResult.value
    }
    if (listResult.status === 'fulfilled') {
      allContent.value = listResult.value.records
    }
  } catch (e) {
    /* 错误已由拦截器处理 */
  } finally {
    loading.value = false
  }
}

/** 总内容数（优先用后端聚合） */
const totalCount = computed(() =>
  overview.value?.contentTotal ?? allContent.value.length)

/** Wave 3.2: 待执行的定时发布数（后端聚合，无 fallback） */
const scheduledPending = computed(() => overview.value?.scheduledPending ?? 0)

/** Wave 3.2: 近 7 日 AI 调用次数 */
const aiCalls7d = computed(() => overview.value?.aiCalls7d ?? 0)

/** Wave 3.2: 近 7 日 AI 累计成本（美元） */
const aiCost7d = computed(() => Number(overview.value?.aiCost7d ?? 0).toFixed(4))

/** 平台数（去重） */
const platformCount = computed(() => {
  const platforms = new Set(allContent.value.map(c => c.platform))
  return platforms.size
})

/** 今日生成数（按创建日期筛选） */
const todayCount = computed(() => {
  const today = new Date().toISOString().split('T')[0]
  return allContent.value.filter(c => c.createTime?.startsWith(today)).length
})

/** 总 Token 消耗（优先用后端 7 日真实数据，否则降级到 client-side 累计 100 条） */
const totalTokens = computed(() => {
  if (overview.value?.aiTokens7d != null) return overview.value.aiTokens7d
  return allContent.value.reduce((sum, c) => sum + (c.tokenUsage || 0), 0)
})

/** 平均 Token 消耗 */
const avgTokens = computed(() => {
  const contentWithTokens = allContent.value.filter(c => c.tokenUsage && c.tokenUsage > 0)
  if (contentWithTokens.length === 0) return 0
  const total = contentWithTokens.reduce((sum, c) => sum + (c.tokenUsage || 0), 0)
  return Math.round(total / contentWithTokens.length)
})

/** 最常用平台 */
const mostUsedPlatform = computed(() => {
  if (allContent.value.length === 0) return '--'
  const countMap: Record<string, number> = {}
  allContent.value.forEach(c => {
    countMap[c.platform] = (countMap[c.platform] || 0) + 1
  })
  let maxPlatform = ''
  let maxCount = 0
  for (const [platform, count] of Object.entries(countMap)) {
    if (count > maxCount) {
      maxCount = count
      maxPlatform = platform
    }
  }
  return getPlatformLabel(maxPlatform)
})

/** 平台分布数据 */
const platformDistribution = computed(() => {
  const countMap: Record<string, number> = {}
  allContent.value.forEach(c => {
    countMap[c.platform] = (countMap[c.platform] || 0) + 1
  })
  const total = allContent.value.length || 1
  return Object.entries(countMap)
    .map(([platform, count]) => ({
      platform,
      label: getPlatformLabel(platform),
      count,
      percentage: Math.round((count / total) * 100),
      color: getPlatformColor(platform),
    }))
    .sort((a, b) => b.count - a.count)
})

/** Token 消耗排行 Top 5 */
const tokenRanking = computed(() => {
  return [...allContent.value]
    .filter(c => c.tokenUsage && c.tokenUsage > 0)
    .sort((a, b) => (b.tokenUsage || 0) - (a.tokenUsage || 0))
    .slice(0, 5)
})

/** Token 排行中的最大值（用于计算进度条比例） */
const maxTokenInRanking = computed(() => {
  if (tokenRanking.value.length === 0) return 1
  return tokenRanking.value[0].tokenUsage || 1
})

/** 近 7 天生成趋势 */
const weeklyTrend = computed(() => {
  const days: { date: string; label: string; count: number }[] = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    const dateStr = d.toISOString().split('T')[0]
    const month = d.getMonth() + 1
    const day = d.getDate()
    days.push({
      date: dateStr,
      label: `${month}/${day}`,
      count: allContent.value.filter(c => c.createTime?.startsWith(dateStr)).length,
    })
  }
  return days
})

/** 趋势图中的最大值（用于计算柱状图高度） */
const maxTrendCount = computed(() => {
  const max = Math.max(...weeklyTrend.value.map(d => d.count))
  return max || 1
})

/** 最近 5 条内容 */
const recentContent = computed(() => {
  return allContent.value.slice(0, 5)
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
 * 获取平台进度条颜色
 */
function getPlatformColor(platform: string): string {
  const map: Record<string, string> = {
    XIAOHONGSHU: '#ff2442',
    DOUYIN: '#333333',
    WECHAT_MOMENT: '#07c160',
    WECHAT_MP: '#576b95',
  }
  return map[platform] || '#409eff'
}

/**
 * 获取标题显示文本
 */
function getDisplayTitle(row: ContentVO): string {
  if (row.title) return row.title
  if (row.body) return row.body.substring(0, 30) + (row.body.length > 30 ? '...' : '')
  return '无标题'
}

/**
 * 查看内容详情
 */
function viewDetail(row: ContentVO) {
  currentDetail.value = row
  detailDialogVisible.value = true
}

onMounted(loadData)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <PageHeader title="数据看板" subtitle="实时掌握内容生产和 AI 使用情况" icon="TrendCharts" />

    <!-- 统计卡片：第一行（4个主要指标） -->
    <div class="stat-grid">
      <StatCard label="总内容数" :value="totalCount" icon="Document" color="primary" hint="累计生成" />
      <StatCard label="覆盖平台" :value="platformCount" icon="Platform" color="success" hint="已发布的平台数" />
      <StatCard label="今日生成" :value="todayCount" icon="Sunrise" color="warning" hint="今天的内容量" />
      <StatCard label="Token 消耗" :value="totalTokens.toLocaleString()" icon="Coin" color="danger" hint="累计消耗" />
    </div>

    <!-- 统计卡片：第二行（3个辅助指标） -->
    <div class="stat-grid stat-grid--3">
      <StatCard label="平均 Token / 篇" :value="avgTokens.toLocaleString()" icon="DataAnalysis" color="info" />
      <StatCard label="最常用平台" :value="mostUsedPlatform" icon="Trophy" color="primary" />
      <StatCard
        label="Token 排行最高"
        :value="tokenRanking.length > 0 ? (tokenRanking[0].tokenUsage || 0).toLocaleString() : '--'"
        icon="Timer"
        color="warning"
      />
    </div>

    <!-- Wave 3.2: 后端聚合的真实 7 日数据 + 待执行任务 -->
    <div v-if="overview" class="stat-grid stat-grid--3">
      <StatCard
        label="待执行定时任务"
        :value="scheduledPending"
        icon="AlarmClock"
        color="warning"
        hint="尚未到点的 SCHEDULED PublishTask"
      />
      <StatCard
        label="近 7 日 AI 调用"
        :value="aiCalls7d.toLocaleString()"
        icon="MagicStick"
        color="primary"
        hint="ai_usage_log 真实统计"
      />
      <StatCard
        :label="'近 7 日成本 (USD)'"
        :value="aiCost7d"
        icon="Money"
        color="danger"
        hint="按 provider 单价累加"
      />
    </div>

    <!-- 中间区域：平台分布 + Token 排行 -->
    <el-row :gutter="16" style="margin-bottom: 20px">
      <!-- 平台分布 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display: flex; align-items: center">
              <el-icon style="margin-right: 6px"><PieChart /></el-icon>
              <span>平台分布</span>
            </div>
          </template>
          <div v-if="platformDistribution.length > 0">
            <div
              v-for="item in platformDistribution"
              :key="item.platform"
              style="margin-bottom: 16px"
            >
              <div style="display: flex; justify-content: space-between; margin-bottom: 4px">
                <span style="font-size: 14px; color: #303133">{{ item.label }}</span>
                <span style="font-size: 13px; color: #909399">{{ item.count }} 篇 ({{ item.percentage }}%)</span>
              </div>
              <el-progress
                :percentage="item.percentage"
                :color="item.color"
                :stroke-width="16"
                :show-text="false"
              />
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="80" />
        </el-card>
      </el-col>

      <!-- Token 消耗排行 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display: flex; align-items: center">
              <el-icon style="margin-right: 6px"><Medal /></el-icon>
              <span>Token 消耗排行</span>
            </div>
          </template>
          <div v-if="tokenRanking.length > 0">
            <div
              v-for="(item, index) in tokenRanking"
              :key="item.id"
              style="margin-bottom: 16px"
            >
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px">
                <div style="display: flex; align-items: center; flex: 1; min-width: 0">
                  <el-tag
                    :type="index === 0 ? 'danger' : index === 1 ? 'warning' : 'info'"
                    size="small"
                    style="margin-right: 8px; flex-shrink: 0"
                  >
                    #{{ index + 1 }}
                  </el-tag>
                  <span style="font-size: 13px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                    {{ getDisplayTitle(item) }}
                  </span>
                </div>
                <span style="font-size: 13px; color: #f56c6c; font-weight: 500; margin-left: 8px; flex-shrink: 0">
                  {{ (item.tokenUsage || 0).toLocaleString() }}
                </span>
              </div>
              <el-progress
                :percentage="Math.round(((item.tokenUsage || 0) / maxTokenInRanking) * 100)"
                :color="index === 0 ? '#f56c6c' : index === 1 ? '#e6a23c' : '#909399'"
                :stroke-width="12"
                :show-text="false"
              />
            </div>
          </div>
          <el-empty v-else description="暂无 Token 消耗数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 近 7 天生成趋势 -->
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; align-items: center">
          <el-icon style="margin-right: 6px"><TrendCharts /></el-icon>
          <span>近 7 天生成趋势</span>
        </div>
      </template>
      <div style="display: flex; align-items: flex-end; justify-content: space-around; height: 180px; padding: 0 20px">
        <div
          v-for="day in weeklyTrend"
          :key="day.date"
          style="display: flex; flex-direction: column; align-items: center; flex: 1; max-width: 80px"
        >
          <!-- 数值标签 -->
          <span style="font-size: 13px; color: #409eff; font-weight: 600; margin-bottom: 4px">
            {{ day.count }}
          </span>
          <!-- CSS 柱状条 -->
          <div
            :style="{
              width: '36px',
              height: day.count > 0 ? (day.count / maxTrendCount * 130) + 'px' : '4px',
              backgroundColor: day.count > 0 ? '#409eff' : '#e4e7ed',
              borderRadius: '4px 4px 0 0',
              transition: 'height 0.5s ease',
              minHeight: '4px',
            }"
          />
          <!-- 日期标签 -->
          <span style="font-size: 12px; color: #909399; margin-top: 6px">
            {{ day.label }}
          </span>
        </div>
      </div>
    </el-card>

    <!-- 最近内容 -->
    <el-card>
      <template #header>
        <span>最近内容</span>
      </template>
      <el-table :data="recentContent" stripe>
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
        <el-table-column prop="model" label="模型" width="130" />
        <el-table-column prop="tokenUsage" label="Token" width="90" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewDetail(row)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && recentContent.length === 0" description="暂无内容，快去工作台生成吧" />
    </el-card>

    <!-- 内容详情弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="内容详情"
      width="660px"
      :close-on-click-modal="true"
    >
      <template v-if="currentDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题" :span="2">
            {{ currentDetail.title || '无标题' }}
          </el-descriptions-item>
          <el-descriptions-item label="平台">
            <el-tag :type="getPlatformType(currentDetail.platform)" size="small">
              {{ getPlatformLabel(currentDetail.platform) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small">{{ currentDetail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="模型">
            {{ currentDetail.model || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="Token 消耗">
            {{ currentDetail.tokenUsage?.toLocaleString() || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">
            {{ currentDetail.tags || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">
            {{ currentDetail.createTime || '--' }}
          </el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 16px">
          <div style="font-size: 14px; color: #606266; font-weight: 500; margin-bottom: 8px">正文内容</div>
          <div
            style="
              background: #f5f7fa;
              border-radius: 6px;
              padding: 16px;
              font-size: 14px;
              line-height: 1.8;
              color: #303133;
              max-height: 300px;
              overflow-y: auto;
              white-space: pre-wrap;
              word-break: break-word;
            "
          >{{ currentDetail.body }}</div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--sf-space-4);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sf-space-3);
}

.stat-grid--3 {
  grid-template-columns: repeat(3, 1fr);
}

@media (max-width: 1200px) {
  .stat-grid, .stat-grid--3 {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
