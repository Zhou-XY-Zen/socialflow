<!--
  ============================================================
  EvalCenter.vue —— 评估中心页面
  ============================================================
  内容质量 A/B 测试评估功能：
  - 评估任务列表（查看、执行、删除）
  - 创建评估任务（配置两套生成参数 + 测试主题）
  - 查看评估报告（全屏弹窗：胜负统计、维度对比、详细结果）
  ============================================================
-->

<script setup lang="ts">
import { onMounted, onUnmounted, ref, reactive, computed } from 'vue'
import { evalApi, type EvalTaskVO, type EvalReportVO } from '@/api/eval'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'

/* ==================== 平台选项 ==================== */

const platformOptions = [
  { label: '小红书', value: 'XIAOHONGSHU' },
  { label: '抖音', value: 'DOUYIN' },
  { label: '朋友圈', value: 'WECHAT_MOMENT' },
  { label: '公众号', value: 'WECHAT_MP' },
]

/** 平台值 → 中文名映射 */
function getPlatformLabel(platform: string): string {
  const map: Record<string, string> = {
    XIAOHONGSHU: '小红书',
    DOUYIN: '抖音',
    WECHAT_MOMENT: '朋友圈',
    WECHAT_MP: '公众号',
  }
  return map[platform] || platform
}

/* ==================== 模型选项 ==================== */

const modelOptions = [
  { label: 'DeepSeek Reasoner', value: 'deepseek-reasoner' },
  { label: 'DeepSeek Chat', value: 'deepseek-chat' },
  { label: 'Qwen Plus', value: 'qwen-plus' },
  { label: 'Qwen Max', value: 'qwen-max' },
]

/* ==================== 维度名称映射 ==================== */

const dimensionLabels: Record<string, string> = {
  relevance: '内容相关性',
  style: '平台风格',
  fluency: '语言流畅',
  creativity: '创意性',
  format: '格式规范',
}

/* ==================== Section 1: 任务列表 ==================== */

const taskList = ref<EvalTaskVO[]>([])
const loading = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

/** 加载任务列表 */
async function loadTasks() {
  loading.value = true
  try {
    taskList.value = await evalApi.listTasks()
  } catch {
    /* 错误已由拦截器处理 */
  } finally {
    loading.value = false
  }
}

/** 是否有正在运行的任务 */
const hasRunningTask = computed(() =>
  taskList.value.some((t) => t.status === 'RUNNING'),
)

/** 启动/停止自动刷新 */
function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    if (hasRunningTask.value) {
      loadTasks()
    }
  }, 5000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

/** 获取任务状态标签类型 */
function getStatusTagType(status: string): string {
  const map: Record<string, string> = {
    PENDING: 'info',
    RUNNING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger',
  }
  return map[status] || 'info'
}

/** 获取任务状态中文名 */
function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待执行',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
  }
  return map[status] || status
}

/** 执行任务 */
async function handleRunTask(task: EvalTaskVO) {
  try {
    await ElMessageBox.confirm(
      `确定要执行评估任务「${task.name}」吗？执行后将开始 A/B 对比测试。`,
      '确认执行',
      { confirmButtonText: '执行', cancelButtonText: '取消', type: 'info' },
    )
    await evalApi.runTask(task.id)
    ElMessage.success('任务已开始执行')
    loadTasks()
    startAutoRefresh()
  } catch {
    /* 用户取消或请求失败 */
  }
}

/** 删除任务 */
async function handleDeleteTask(task: EvalTaskVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除评估任务「${task.name}」吗？删除后不可恢复。`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    const { del } = await import('@/api/http')
    await del<void>(`/eval/task/${task.id}`)
    ElMessage.success('删除成功')
    loadTasks()
  } catch {
    /* 用户取消或请求失败 */
  }
}

/* ==================== Section 2: 创建任务弹窗 ==================== */

const createDialogVisible = ref(false)

/** 创建表单初始状态工厂 */
function makeCreateForm() {
  return {
    name: '',
    configA: { model: 'deepseek-reasoner', temperature: 0.7 },
    configB: { model: 'qwen-plus', temperature: 0.7 },
    testTopics: [
      { topic: '', platform: 'XIAOHONGSHU' },
      { topic: '', platform: 'XIAOHONGSHU' },
      { topic: '', platform: 'XIAOHONGSHU' },
    ] as { topic: string; platform: string }[],
  }
}

const createForm = reactive(makeCreateForm())

/** 打开创建弹窗 */
function openCreateDialog() {
  Object.assign(createForm, makeCreateForm())
  createDialogVisible.value = true
}

/** 添加测试主题行 */
function addTopicRow() {
  createForm.testTopics.push({ topic: '', platform: 'XIAOHONGSHU' })
}

/** 删除测试主题行 */
function removeTopicRow(index: number) {
  createForm.testTopics.splice(index, 1)
}

/** 提交创建任务 */
const createLoading = ref(false)

async function handleCreateTask() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入任务名称')
    return
  }
  const validTopics = createForm.testTopics.filter((t) => t.topic.trim())
  if (validTopics.length === 0) {
    ElMessage.warning('请至少添加一个测试主题')
    return
  }
  createLoading.value = true
  try {
    await evalApi.createTask({
      name: createForm.name.trim(),
      configA: { ...createForm.configA },
      configB: { ...createForm.configB },
      testTopics: validTopics.map((t) => ({ topic: t.topic.trim(), platform: t.platform })),
    })
    ElMessage.success('任务创建成功')
    createDialogVisible.value = false
    loadTasks()
  } catch {
    /* 错误已由拦截器处理 */
  } finally {
    createLoading.value = false
  }
}

/* ==================== Section 3: 评估报告弹窗 ==================== */

const reportDialogVisible = ref(false)
const reportData = ref<EvalReportVO | null>(null)
const reportLoading = ref(false)
/** 报告中展开行的 topic 集合 */
const expandedTopics = ref<Set<string>>(new Set())

/** 查看评估报告 */
async function handleViewReport(task: EvalTaskVO) {
  reportDialogVisible.value = true
  reportLoading.value = true
  expandedTopics.value = new Set()
  try {
    reportData.value = await evalApi.getReport(task.id)
  } catch {
    /* 错误已由拦截器处理 */
    reportData.value = null
  } finally {
    reportLoading.value = false
  }
}

/** 计算百分比 */
function pct(count: number, total: number): string {
  if (total === 0) return '0%'
  return (count / total * 100).toFixed(1) + '%'
}

/**
 * Wave 4.6 - 下载评估报告 CSV。
 *
 * 用 fetch + Authorization 头拿到 blob，触发浏览器下载。
 * 比用 a 标签 + token query 更通用（后端不需要支持从 query 读 token）。
 */
async function exportReportCsv() {
  if (!reportData.value) return
  const taskId = reportData.value.taskId
  try {
    const userStore = useUserStore()
    const res = await fetch(evalApi.exportUrl(taskId), {
      headers: { Authorization: `Bearer ${userStore.token || ''}` },
    })
    if (!res.ok) {
      ElMessage.error(`下载失败: HTTP ${res.status}`)
      return
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `eval_${taskId}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success('CSV 已下载')
  } catch (e: any) {
    ElMessage.error('下载失败: ' + (e?.message || ''))
  }
}

/** 获取维度中文名 */
function getDimensionLabel(key: string): string {
  return dimensionLabels[key] || key
}

/** 获取维度胜者 */
function getDimensionWinner(dim: string): string {
  if (!reportData.value) return '-'
  const a = reportData.value.avgScoresA[dim] ?? 0
  const b = reportData.value.avgScoresB[dim] ?? 0
  if (a > b) return 'A'
  if (b > a) return 'B'
  return '平局'
}

/** 维度列表（从 avgScoresA 的 key 中提取） */
const dimensionKeys = computed(() => {
  if (!reportData.value) return []
  return Object.keys(reportData.value.avgScoresA)
})

/** 切换详情行展开 */
function toggleExpand(topic: string) {
  if (expandedTopics.value.has(topic)) {
    expandedTopics.value.delete(topic)
  } else {
    expandedTopics.value.add(topic)
  }
}

/** 解析任务的 testTopics JSON 获取详细结果列表（用于报告展示） */
function getTaskDetailCases(task: EvalTaskVO | null): any[] {
  if (!task) return []
  try {
    return JSON.parse(task.testTopics)
  } catch {
    return []
  }
}

/* ==================== 生命周期 ==================== */

onMounted(() => {
  loadTasks()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<template>
  <div class="sf-page-container">
    <PageHeader
      title="评估中心"
      subtitle="A/B 测试评估 AI 生成效果"
      icon="DataAnalysis"
    />
    <!-- ==================== 任务列表 ==================== -->
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span style="font-weight: 600; font-size: 16px">评估任务</span>
          <div>
            <el-button type="primary" @click="openCreateDialog">创建任务</el-button>
            <el-button @click="loadTasks">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="taskList" v-loading="loading" stripe>
        <el-table-column prop="name" label="任务名称" min-width="180" />

        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
            <el-icon
              v-if="row.status === 'RUNNING'"
              class="is-loading"
              style="margin-left: 4px; color: #e6a23c"
            >
              <Loading />
            </el-icon>
          </template>
        </el-table-column>

        <el-table-column label="进度" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="row.totalCases > 0 ? Math.round(row.completedCases / row.totalCases * 100) : 0"
              :status="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'exception' : undefined"
              :stroke-width="14"
              :text-inside="true"
              style="width: 100%"
            />
            <span style="font-size: 12px; color: #909399">
              {{ row.completedCases }} / {{ row.totalCases }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" width="180" />

        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="primary"
              @click="handleRunTask(row)"
            >
              执行
            </el-button>
            <el-button
              v-if="row.status === 'COMPLETED'"
              link
              type="success"
              @click="handleViewReport(row)"
            >
              查看报告
            </el-button>
            <el-button link type="danger" @click="handleDeleteTask(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ==================== 创建任务弹窗 ==================== -->
    <el-dialog
      v-model="createDialogVisible"
      title="创建评估任务"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px" label-position="right">
        <!-- 任务名称 -->
        <el-form-item label="任务名称" required>
          <el-input v-model="createForm.name" placeholder="请输入任务名称" maxlength="50" />
        </el-form-item>

        <!-- 配置 A -->
        <el-divider content-position="left">配置 A</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型">
              <el-select v-model="createForm.configA.model" style="width: 100%">
                <el-option
                  v-for="opt in modelOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度">
              <el-slider
                v-model="createForm.configA.temperature"
                :min="0"
                :max="1"
                :step="0.1"
                show-input
                :show-input-controls="false"
                input-size="small"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 配置 B -->
        <el-divider content-position="left">配置 B</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型">
              <el-select v-model="createForm.configB.model" style="width: 100%">
                <el-option
                  v-for="opt in modelOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度">
              <el-slider
                v-model="createForm.configB.temperature"
                :min="0"
                :max="1"
                :step="0.1"
                show-input
                :show-input-controls="false"
                input-size="small"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 测试主题 -->
        <el-divider content-position="left">测试主题</el-divider>
        <div
          v-for="(item, index) in createForm.testTopics"
          :key="index"
          style="display: flex; gap: 8px; margin-bottom: 12px; align-items: center"
        >
          <el-input
            v-model="item.topic"
            placeholder="请输入测试主题"
            style="flex: 1"
          />
          <el-select v-model="item.platform" style="width: 140px">
            <el-option
              v-for="opt in platformOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-button
            type="danger"
            link
            :disabled="createForm.testTopics.length <= 1"
            @click="removeTopicRow(index)"
          >
            删除
          </el-button>
        </div>
        <el-button type="primary" link @click="addTopicRow">
          + 添加主题
        </el-button>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreateTask">
          确认创建
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 评估报告弹窗（全屏） ==================== -->
    <el-dialog
      v-model="reportDialogVisible"
      :title="reportData?.taskName ? `评估报告：${reportData.taskName}` : '评估报告'"
      fullscreen
      class="report-dialog"
    >
      <div v-loading="reportLoading" style="min-height: 200px">
        <template v-if="reportData && !reportLoading">
          <!-- Wave 4.6: 工具栏 - 导出 CSV -->
          <div style="display: flex; justify-content: flex-end; margin-bottom: 12px">
            <el-button type="primary" @click="exportReportCsv">
              <el-icon style="margin-right: 4px"><Download /></el-icon>
              下载 CSV 报告
            </el-button>
          </div>
          <!-- 顶部：总体统计卡片 -->
          <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 24px;">
            <StatCard
              label="A 胜"
              :value="reportData.winsA"
              :hint="pct(reportData.winsA, reportData.totalCases)"
              icon="Trophy"
              color="danger"
            />
            <StatCard
              label="B 胜"
              :value="reportData.winsB"
              :hint="pct(reportData.winsB, reportData.totalCases)"
              icon="Trophy"
              color="info"
            />
            <StatCard
              label="平局"
              :value="reportData.ties"
              :hint="pct(reportData.ties, reportData.totalCases)"
              icon="Medal"
              color="warning"
            />
          </div>

          <!-- 中部：各维度平均分对比 -->
          <el-card style="margin-bottom: 24px">
            <template #header>
              <span style="font-weight: 600">各维度平均分对比</span>
              <span style="margin-left: 16px; font-size: 13px; color: #909399">
                总体平均 —— A: {{ reportData.overallAvgA.toFixed(2) }}，B: {{ reportData.overallAvgB.toFixed(2) }}
              </span>
            </template>
            <el-table :data="dimensionKeys.map(k => ({ key: k }))" stripe>
              <el-table-column label="维度" min-width="140">
                <template #default="{ row }">
                  {{ getDimensionLabel(row.key) }}
                </template>
              </el-table-column>
              <el-table-column label="A 平均分" width="140">
                <template #default="{ row }">
                  {{ (reportData!.avgScoresA[row.key] ?? 0).toFixed(2) }}
                </template>
              </el-table-column>
              <el-table-column label="B 平均分" width="140">
                <template #default="{ row }">
                  {{ (reportData!.avgScoresB[row.key] ?? 0).toFixed(2) }}
                </template>
              </el-table-column>
              <el-table-column label="胜者" width="120">
                <template #default="{ row }">
                  <el-tag
                    :type="getDimensionWinner(row.key) === 'A' ? 'danger' : getDimensionWinner(row.key) === 'B' ? 'primary' : 'info'"
                    size="small"
                  >
                    {{ getDimensionWinner(row.key) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 底部：最佳 / 最差案例 -->
          <el-row :gutter="16" style="margin-bottom: 24px">
            <el-col :span="12">
              <el-card>
                <template #header>
                  <span style="font-weight: 600; color: #67c23a">最佳案例</span>
                </template>
                <el-table :data="reportData.bestCases" stripe size="small">
                  <el-table-column prop="topic" label="主题" min-width="120" />
                  <el-table-column label="胜者" width="80">
                    <template #default="{ row }">
                      <el-tag
                        :type="row.winner === 'A' ? 'danger' : row.winner === 'B' ? 'primary' : 'info'"
                        size="small"
                      >
                        {{ row.winner }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="scoreA" label="A 分" width="80" />
                  <el-table-column prop="scoreB" label="B 分" width="80" />
                </el-table>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card>
                <template #header>
                  <span style="font-weight: 600; color: #f56c6c">最差案例</span>
                </template>
                <el-table :data="reportData.worstCases" stripe size="small">
                  <el-table-column prop="topic" label="主题" min-width="120" />
                  <el-table-column label="胜者" width="80">
                    <template #default="{ row }">
                      <el-tag
                        :type="row.winner === 'A' ? 'danger' : row.winner === 'B' ? 'primary' : 'info'"
                        size="small"
                      >
                        {{ row.winner }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="scoreA" label="A 分" width="80" />
                  <el-table-column prop="scoreB" label="B 分" width="80" />
                </el-table>
              </el-card>
            </el-col>
          </el-row>
        </template>

        <el-empty v-if="!reportData && !reportLoading" description="暂无报告数据" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 统计卡片 */
.stat-card {
  text-align: center;
  padding: 8px 0;
}
.stat-card__label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}
.stat-card__value {
  font-size: 36px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-card__pct {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}
.stat-card--a .stat-card__value {
  color: #f56c6c;
}
.stat-card--b .stat-card__value {
  color: #409eff;
}
.stat-card--tie .stat-card__value {
  color: #909399;
}

/* 全屏报告弹窗关闭按钮样式 */
:deep(.report-dialog .el-dialog__headerbtn) {
  width: 40px;
  height: 40px;
  font-size: 24px;
  background: #f56c6c;
  border-radius: 50%;
  top: 12px;
  right: 16px;
}
:deep(.report-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: #fff;
  font-size: 20px;
}
:deep(.report-dialog .el-dialog__headerbtn:hover) {
  background: #e04040;
}
</style>
