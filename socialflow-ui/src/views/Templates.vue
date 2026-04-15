<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { templateApi, type TemplateVO } from '@/api/template'
import type { TemplatePreviewVO } from '@/types/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

const templates = ref<TemplateVO[]>([])
const loading = ref(false)
const filterPlatform = ref('')

/** 平台选项 */
const platformOptions = [
  { label: '全部', value: '' },
  { label: '小红书', value: 'XIAOHONGSHU' },
  { label: '抖音', value: 'DOUYIN' },
  { label: '朋友圈', value: 'WECHAT_MOMENT' },
  { label: '公众号', value: 'WECHAT_MP' },
]
const categoryOptions = ['种草', '教程', '测评', '故事', '脚本', '推广', '日常', '深度', '通用']

function getPlatformLabel(p: string) {
  return { XIAOHONGSHU: '小红书', DOUYIN: '抖音', WECHAT_MOMENT: '朋友圈', WECHAT_MP: '公众号' }[p] || p
}
function getPlatformType(p: string) {
  return { XIAOHONGSHU: 'danger', DOUYIN: '', WECHAT_MOMENT: 'success', WECHAT_MP: 'warning' }[p] || 'info'
}

async function loadList() {
  loading.value = true
  try {
    templates.value = await templateApi.list(filterPlatform.value || undefined)
  } catch { /* fallback handled by interceptor */ }
  finally { loading.value = false }
}

/** 创建/编辑 对话框 */
const dialogVisible = ref(false)
const dialogTitle = ref('创建模板')
const editingId = ref<string | number | null>(null)
const form = reactive({
  templateName: '',
  platform: 'XIAOHONGSHU',
  category: '通用',
  systemPrompt: '',
  userPromptTemplate: '',
  outputFormat: 'TEXT',
})

function openCreate() {
  editingId.value = null
  dialogTitle.value = '创建模板'
  Object.assign(form, { templateName: '', platform: 'XIAOHONGSHU', category: '通用', systemPrompt: '', userPromptTemplate: '请根据以下信息生成文案：\n主题：{{topic}}\n关键词：{{keywords}}', outputFormat: 'TEXT' })
  dialogVisible.value = true
}

function openEdit(tpl: TemplateVO) {
  editingId.value = tpl.id
  dialogTitle.value = '编辑模板'
  Object.assign(form, {
    templateName: tpl.templateName,
    platform: tpl.platform,
    category: tpl.category,
    systemPrompt: tpl.systemPrompt,
    userPromptTemplate: tpl.userPromptTemplate || '',
    outputFormat: tpl.outputFormat || 'TEXT',
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.templateName.trim() || !form.systemPrompt.trim()) {
    ElMessage.warning('请填写模板名称和系统提示词')
    return
  }
  try {
    if (editingId.value) {
      await templateApi.update(editingId.value, form)
      ElMessage.success('模板已更新')
    } else {
      await templateApi.create(form)
      ElMessage.success('模板已创建')
    }
    dialogVisible.value = false
    await loadList()
  } catch (e: any) {
    ElMessage.error('操作失败：' + (e.message || ''))
  }
}

async function handleDelete(tpl: TemplateVO) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${tpl.templateName}」吗？`, '确认删除', { type: 'warning' })
    await templateApi.delete(tpl.id)
    ElMessage.success('已删除')
    await loadList()
  } catch { /* cancel */ }
}

/** 查看详情 */
const detailVisible = ref(false)
const detailItem = ref<TemplateVO | null>(null)
function openDetail(tpl: TemplateVO) {
  detailItem.value = tpl
  detailVisible.value = true
}

/* ===================== Wave 4.4 模板预览 ===================== */
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewVarsText = ref('{\n  "topic": "咖啡探店",\n  "tone": "活泼",\n  "wordCount": 300\n}')
const previewResult = ref<TemplatePreviewVO | null>(null)

function openPreview(tpl: TemplateVO) {
  detailItem.value = tpl
  previewResult.value = null
  previewVisible.value = true
}

async function runPreview() {
  if (!detailItem.value) return
  let vars: Record<string, unknown> = {}
  try {
    vars = JSON.parse(previewVarsText.value)
  } catch {
    ElMessage.warning('样例变量必须是合法 JSON')
    return
  }
  previewLoading.value = true
  try {
    previewResult.value = await templateApi.preview(detailItem.value.id, vars)
  } catch {
    /* 拦截器已提示 */
  } finally {
    previewLoading.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <div v-loading="loading" class="sf-page-container">
    <!-- 页面头部 -->
    <PageHeader title="提示词模板" subtitle="预设专业的 AI 生成模板" icon="Collection" />

    <!-- 操作栏 -->
    <el-card style="margin-bottom: 16px">
      <div style="display: flex; justify-content: space-between; align-items: center">
        <div style="display: flex; align-items: center; gap: 12px">
          <span style="font-size: 16px; font-weight: bold">Prompt 模板管理</span>
          <el-select v-model="filterPlatform" placeholder="筛选平台" clearable style="width: 140px" @change="loadList">
            <el-option v-for="o in platformOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </div>
        <div>
          <el-button type="primary" @click="openCreate">创建模板</el-button>
          <el-button @click="loadList">刷新</el-button>
        </div>
      </div>
    </el-card>

    <!-- 模板卡片 -->
    <el-row :gutter="16">
      <el-col v-for="tpl in templates" :key="tpl.id" :xs="24" :sm="12" :md="8" :lg="6" style="margin-bottom: 16px">
        <el-card class="template-card" shadow="hover" style="height: 100%; cursor: pointer" @click="openDetail(tpl)">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 150px">
                {{ tpl.templateName }}
              </span>
              <el-tag :type="getPlatformType(tpl.platform)" size="small">
                {{ getPlatformLabel(tpl.platform) }}
              </el-tag>
            </div>
          </template>
          <div>
            <el-tag size="small" type="info" style="margin-bottom: 8px">{{ tpl.category }}</el-tag>
            <el-tag v-if="tpl.isSystem" size="small" type="warning" style="margin-left: 4px; margin-bottom: 8px">系统</el-tag>
            <p style="color: #606266; font-size: 13px; line-height: 1.6; margin: 0; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden">
              {{ tpl.systemPrompt }}
            </p>
          </div>
          <div style="margin-top: 12px; display: flex; justify-content: flex-end; gap: 4px" @click.stop>
            <el-button link type="success" size="small" @click="openPreview(tpl)">预览</el-button>
            <el-button link type="primary" size="small" @click="openEdit(tpl)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(tpl)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <EmptyState
      v-if="!loading && templates.length === 0"
      icon="Collection"
      title="暂无模板"
      description="创建你的第一个 Prompt 模板"
      actionText="创建模板"
      @action="openCreate"
    />

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" :close-on-click-modal="false">
      <el-form label-width="120px" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模板名称" required>
              <el-input v-model="form.templateName" placeholder="如：小红书种草文案" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="目标平台" required>
              <el-select v-model="form.platform" style="width: 100%">
                <el-option v-for="o in platformOptions.slice(1)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="分类">
              <el-select v-model="form.category" style="width: 100%" allow-create filterable>
                <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="系统提示词（System Prompt）" required>
          <el-input v-model="form.systemPrompt" type="textarea" :rows="5" placeholder="定义 AI 的角色和行为规范，如：你是一位资深的小红书博主..." />
        </el-form-item>
        <el-form-item label="用户提示词模板（支持 {{variable}} 变量）">
          <el-input v-model="form.userPromptTemplate" type="textarea" :rows="4" placeholder="请根据以下信息生成文案：&#10;主题：{{topic}}&#10;关键词：{{keywords}}" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情查看 -->
    <el-dialog v-model="detailVisible" :title="detailItem?.templateName || '模板详情'" width="700px">
      <el-descriptions v-if="detailItem" :column="2" border>
        <el-descriptions-item label="平台">
          <el-tag :type="getPlatformType(detailItem.platform)" size="small">{{ getPlatformLabel(detailItem.platform) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="分类">{{ detailItem.category }}</el-descriptions-item>
        <el-descriptions-item label="输出格式">{{ detailItem.outputFormat }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detailItem.isSystem ? '系统预置' : '用户自定义' }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top: 16px">
        <h4 style="margin: 0 0 8px">系统提示词</h4>
        <div style="background: #f5f7fa; padding: 12px; border-radius: 6px; font-size: 13px; line-height: 1.8; white-space: pre-wrap">{{ detailItem?.systemPrompt }}</div>
      </div>
      <div v-if="detailItem?.userPromptTemplate" style="margin-top: 16px">
        <h4 style="margin: 0 0 8px">用户提示词模板</h4>
        <div style="background: #f5f7fa; padding: 12px; border-radius: 6px; font-size: 13px; line-height: 1.8; white-space: pre-wrap; color: #409eff">{{ detailItem.userPromptTemplate }}</div>
      </div>
    </el-dialog>

    <!-- Wave 4.4: 模板预览对话框 —— 用样例变量试渲染 + 显示变量诊断 -->
    <el-dialog
      v-model="previewVisible"
      :title="`模板预览 - ${detailItem?.templateName || ''}`"
      width="780px"
    >
      <el-form label-position="top">
        <el-form-item label="样例变量（JSON）">
          <el-input
            v-model="previewVarsText"
            type="textarea"
            :rows="6"
            placeholder='{"topic":"咖啡探店","tone":"活泼"}'
          />
        </el-form-item>
        <el-button type="primary" :loading="previewLoading" @click="runPreview">
          运行预览
        </el-button>
      </el-form>

      <div v-if="previewResult" style="margin-top: 20px">
        <!-- 变量诊断 -->
        <h4 style="margin: 0 0 8px">变量诊断</h4>
        <el-row :gutter="12" style="margin-bottom: 16px">
          <el-col :span="8">
            <div style="font-size: 12px; color: #909399; margin-bottom: 4px">已声明（{{ previewResult.declaredVariables.length }}）</div>
            <el-tag v-for="v in previewResult.declaredVariables" :key="'d'+v" size="small" style="margin: 2px">{{ v }}</el-tag>
          </el-col>
          <el-col :span="8">
            <div style="font-size: 12px; color: #f56c6c; margin-bottom: 4px">缺失（{{ previewResult.missingVariables.length }}）</div>
            <el-tag v-for="v in previewResult.missingVariables" :key="'m'+v" type="danger" size="small" style="margin: 2px">{{ v }}</el-tag>
            <span v-if="previewResult.missingVariables.length === 0" style="font-size: 12px; color: #67c23a">✓ 全部已提供</span>
          </el-col>
          <el-col :span="8">
            <div style="font-size: 12px; color: #909399; margin-bottom: 4px">未使用（{{ previewResult.unusedVariables.length }}）</div>
            <el-tag v-for="v in previewResult.unusedVariables" :key="'u'+v" type="info" size="small" style="margin: 2px">{{ v }}</el-tag>
          </el-col>
        </el-row>

        <h4 style="margin: 0 0 8px">渲染后 System Prompt</h4>
        <div style="background: #f5f7fa; padding: 12px; border-radius: 6px; font-size: 13px; line-height: 1.8; white-space: pre-wrap; max-height: 200px; overflow: auto">{{ previewResult.renderedSystemPrompt }}</div>

        <h4 style="margin: 16px 0 8px">渲染后 User Prompt</h4>
        <div style="background: #ecf5ff; padding: 12px; border-radius: 6px; font-size: 13px; line-height: 1.8; white-space: pre-wrap; max-height: 200px; overflow: auto; color: #409eff">{{ previewResult.renderedUserPrompt }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.template-card {
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.template-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--sf-shadow-md);
}
</style>
