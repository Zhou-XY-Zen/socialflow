<script setup lang="ts">
/**
 * AI 文案工作台 —— 生成文案 + 自动推荐配图
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import { useSse } from '@/composables/useSse'
import { templateApi, type TemplateVO } from '@/api/template'
import { kbApi } from '@/api/knowledge'
import { mediaApi, type MediaAssetVO } from '@/api/media'
import { imageApi, type ImageTaskStatusVO } from '@/api/image'
import { post } from '@/api/http'
import { ElMessage } from 'element-plus'
import type { ContentGenerateDTO, KbVO } from '@/types/api'

const form = reactive<ContentGenerateDTO>({
  topic: '',
  platform: 'XIAOHONGSHU',
  tone: 'casual',
  wordCount: 500,
  enableGuardrails: true,
  temperature: 0.7,
  templateId: undefined,
  kbId: undefined,
})

/* ===================== Multi-Agent 协作模式相关 ===================== */
/** 是否启用 Multi-Agent 协作生成 */
const useMultiAgent = ref(false)
/** Multi-Agent 最大轮数 */
const maxRounds = ref(3)
/** Multi-Agent 阶段事件列表（用于展示状态指示器） */
const multiAgentStages = ref<{ stage: string; message: string; time: string }[]>([])

const body = ref('')
const stage = ref('')

/** Markdown 渲染器 */
const md = new MarkdownIt({ html: false, breaks: true, linkify: true })
/** 渲染模式：preview=Markdown预览，source=源码编辑 */
const viewMode = ref<'preview' | 'source'>('preview')
/** 渲染后的 HTML */
const renderedMarkdown = computed(() => md.render(body.value || ''))
const warnings = ref<string[]>([])
const { streaming, start, stop } = useSse()

const platforms = [
  { value: 'XIAOHONGSHU', label: '小红书' },
  { value: 'DOUYIN', label: '抖音' },
  { value: 'WECHAT_MOMENT', label: '朋友圈' },
  { value: 'WECHAT_MP', label: '公众号' },
]
const tones = [
  { value: 'casual', label: '轻松' },
  { value: 'professional', label: '专业' },
  { value: 'humorous', label: '幽默' },
  { value: 'inspiring', label: '鼓舞' },
]

/** 模板列表 */
const templateList = ref<TemplateVO[]>([])
async function loadTemplates() {
  try { templateList.value = await templateApi.list(form.platform) } catch { templateList.value = [] }
}

/** 知识库列表 */
const kbList = ref<KbVO[]>([])
async function loadKbList() {
  try { kbList.value = await kbApi.list() } catch { kbList.value = [] }
}

watch(() => form.platform, () => { form.templateId = undefined; loadTemplates() })
onMounted(() => { loadTemplates(); loadKbList() })

/* ===================== AI 智能配图 ===================== */

/** 图片预览 */
const previewImg = ref('')
const previewVisible = ref(false)

/** AI 配图提示词（中文） */
const aiImagePrompt = ref('')
/** 提示词提取中 */
const promptLoading = ref(false)
/** 是否已提取提示词（控制面板显示） */
const showImagePanel = ref(false)

/** AI 文生图任务 */
const aiTaskId = ref<string | null>(null)
const aiGenerating = ref(false)
const aiImages = ref<string[]>([])
const aiError = ref('')
/** 轮询定时器 */
let pollTimer: ReturnType<typeof setInterval> | null = null
/** 轮询次数（用于计算进度条） */
const pollCount = ref(0)
const maxPollCount = 40

/** 已选图片 URL 集合 */
const selectedImageUrls = ref<Set<string>>(new Set())
/** 保存配图中 */
const saving = ref(false)
/** 保存进度文本 */
const saveProgress = ref('')
/** 已保存的 MediaAsset 列表 */
const savedMedia = ref<MediaAssetVO[]>([])
/** 最新生成的文案 ID */
const lastContentId = ref<string | number | null>(null)

/** 从文案中提取 AI 配图提示词 */
async function extractImagePrompt(text: string) {
  promptLoading.value = true
  showImagePanel.value = true
  aiImages.value = []
  aiError.value = ''
  selectedImageUrls.value = new Set()
  savedMedia.value = []
  try {
    const result = await imageApi.extractPrompt(text)
    aiImagePrompt.value = result.imagePrompt
  } catch {
    // 降级：用文案主题生成有意义的提示词，而不是写死万能废话
    const topic = form.topic.trim() || '社交媒体'
    aiImagePrompt.value = `${topic}相关的精美配图，高清画质，氛围感，适合社交媒体分享`
  } finally {
    promptLoading.value = false
  }
}

/** 提交 AI 文生图任务 */
async function startAiGenerate() {
  if (!aiImagePrompt.value.trim()) {
    ElMessage.warning('请输入配图提示词')
    return
  }
  aiGenerating.value = true
  aiImages.value = []
  aiError.value = ''
  pollCount.value = 0

  try {
    const result = await imageApi.generate(aiImagePrompt.value.trim())
    aiTaskId.value = result.taskId
    // 开始轮询任务状态
    startPollStatus()
  } catch (e: any) {
    aiGenerating.value = false
    aiError.value = e.message || '提交任务失败'
    ElMessage.error('提交文生图任务失败')
  }
}

/** 轮询 AI 文生图任务状态 */
function startPollStatus() {
  stopPollStatus()
  pollTimer = setInterval(async () => {
    if (!aiTaskId.value) { stopPollStatus(); return }
    pollCount.value++

    try {
      const status = await imageApi.getStatus(aiTaskId.value)

      if (status.status === 'SUCCEEDED') {
        aiImages.value = status.imageUrls || []
        aiGenerating.value = false
        stopPollStatus()
        ElMessage.success(`成功生成 ${aiImages.value.length} 张配图`)
      } else if (status.status === 'FAILED') {
        aiError.value = status.errorMessage || '图片生成失败'
        aiGenerating.value = false
        stopPollStatus()
        ElMessage.error('图片生成失败：' + aiError.value)
      }
      // PENDING / RUNNING → 继续轮询
    } catch {
      // 网络错误，继续轮询
    }

    // 超时保护（40 次 × 3s = 2 分钟）
    if (pollCount.value >= maxPollCount) {
      aiError.value = '生成超时，请重试'
      aiGenerating.value = false
      stopPollStatus()
    }
  }, 3000)
}

function stopPollStatus() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

/** 计算进度百分比（估算） */
function getProgressPercentage(): number {
  // 典型生成时间约 30-45 秒，按 40 次轮询计算
  return Math.min(Math.round(pollCount.value / maxPollCount * 100), 95)
}

/** 切换图片选中状态 */
function toggleImageUrl(url: string) {
  if (selectedImageUrls.value.has(url)) {
    selectedImageUrls.value.delete(url)
  } else {
    selectedImageUrls.value.add(url)
  }
  selectedImageUrls.value = new Set(selectedImageUrls.value)
}

/** 下载选中图片到 MinIO 并绑定到文案 */
async function saveSelectedImages() {
  if (selectedImageUrls.value.size === 0) {
    ElMessage.warning('请先勾选要保存的图片')
    return
  }
  saving.value = true
  saveProgress.value = ''

  try {
    const urls = [...selectedImageUrls.value]
    const assetIds: (string | number)[] = []
    const saved: MediaAssetVO[] = []

    // 根据文案主题和提示词生成标签
    const tags = buildImageTags()

    // 逐张下载保存
    for (let i = 0; i < urls.length; i++) {
      saveProgress.value = `正在保存 ${i + 1}/${urls.length}...`
      const asset = await imageApi.download(urls[i], tags)
      assetIds.push(asset.id)
      saved.push(asset)
    }

    // 绑定到文案
    if (lastContentId.value && assetIds.length > 0) {
      await post<void>(`/content/${lastContentId.value}/bindMedia`, assetIds)
    }

    savedMedia.value = saved
    ElMessage.success(`已保存 ${saved.length} 张 AI 配图`)
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e.message || ''))
  } finally {
    saving.value = false
    saveProgress.value = ''
  }
}

/**
 * 根据文案主题、平台、提示词自动生成图片标签。
 * 标签用于素材库的搜索和分类。
 */
function buildImageTags(): string {
  const tags: string[] = ['AI生成']

  // 1. 文案主题作为核心标签
  const topic = form.topic.trim()
  if (topic) {
    // 拆分主题词（用户可能输入"仙女裙穿搭"这样的复合词）
    tags.push(topic)
    // 尝试拆分为更细粒度的标签
    const parts = topic.split(/[,，\s、·]+/).filter(w => w.length >= 2)
    for (const p of parts) {
      if (p !== topic && !tags.includes(p)) tags.push(p)
    }
  }

  // 2. 平台标签
  const platformMap: Record<string, string> = {
    XIAOHONGSHU: '小红书', DOUYIN: '抖音',
    WECHAT_MOMENT: '朋友圈', WECHAT_MP: '公众号',
  }
  const platformLabel = platformMap[form.platform]
  if (platformLabel) tags.push(platformLabel)

  // 3. 从绘图提示词中提取关键场景词
  const prompt = aiImagePrompt.value
  if (prompt) {
    const sceneWords = [
      '仙女', '裙子', '连衣裙', '穿搭', '时尚', '街拍',
      '美食', '火锅', '咖啡', '奶茶', '甜品', '蛋糕',
      '旅行', '风景', '城市', '夜景', '海滩', '山水', '日落',
      '护肤', '美妆', '口红', '香水',
      '健身', '瑜伽', '运动',
      '猫咪', '狗狗', '宠物',
      '花园', '森林', '阳光', '樱花',
      '婚礼', '派对', '节日',
    ]
    for (const w of sceneWords) {
      if (prompt.includes(w) && !tags.includes(w)) {
        tags.push(w)
        if (tags.length >= 8) break
      }
    }
  }

  return tags.slice(0, 8).join(',')
}

function openPreview(url: string) {
  previewImg.value = url
  previewVisible.value = true
}

async function onGenerate() {
  if (!form.topic.trim()) return
  body.value = ''
  warnings.value = []
  stage.value = ''
  multiAgentStages.value = []
  showImagePanel.value = false
  aiImages.value = []
  selectedImageUrls.value = new Set()
  savedMedia.value = []
  lastContentId.value = null
  stopPollStatus()

  /* 根据模式选择 SSE 端点和请求体 */
  const sseUrl = useMultiAgent.value
    ? '/api/v1/content/generate-multi-agent'
    : '/api/v1/content/generate-stream'
  const requestBody = useMultiAgent.value
    ? { ...form, maxRounds: maxRounds.value }
    : form

  await start(sseUrl, requestBody, {
    onMessage: (data) => {
      const d = data as { token?: string; clear?: boolean }
      if (d?.clear) { body.value = '' }  // Multi-Agent 优化轮次时清空旧内容
      else if (d?.token) { body.value += d.token }
    },
    onStage: (data) => {
      stage.value = data.message || data.stage
      /* Multi-Agent 模式下收集阶段事件用于展示 */
      if (useMultiAgent.value) {
        multiAgentStages.value.push({
          stage: data.stage,
          message: data.message || data.stage,
          time: new Date().toLocaleTimeString(),
        })
      }
    },
    onGuardrail: (data) => { warnings.value.push(data.message) },
    onDone: async () => {
      // 检查是否有实际内容生成——防止空内容直接结束
      if (!body.value.trim()) {
        stage.value = '生成失败：未收到任何内容，请重试'
        ElMessage.warning('生成失败：AI 未返回内容，可能是模型正在思考时连接中断，请重试')
        return
      }
      stage.value = '生成完成'
      // AI 智能提取配图提示词
      extractImagePrompt(body.value)
      // 获取刚保存的文案 ID（取最新一条）
      try {
        const { contentApi } = await import('@/api/content')
        const list = await contentApi.list({ pageNum: 1, pageSize: 1 })
        if (list.records.length > 0) {
          lastContentId.value = list.records[0].id
        }
      } catch { /* ignore */ }
    },
    onError: (data) => {
      stage.value = `生成出错：${data.message}`
      ElMessage.error('生成出错：' + data.message)
    },
  })
}
</script>

<template>
  <div class="workspace-container">
    <el-row :gutter="20" style="height: 100%">
      <!-- 左栏：生成参数 -->
      <el-col :span="9">
        <el-card class="param-card">
          <div class="card-header-bar">
            <h3 class="card-title">AI 文案工作台</h3>
          </div>
          <el-form label-position="top" size="default">
            <el-form-item label="主题">
              <el-input v-model="form.topic" type="textarea" :rows="3" placeholder="描述你想写什么..." />
            </el-form-item>

            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="平台">
                  <el-select v-model="form.platform" style="width: 100%">
                    <el-option v-for="p in platforms" :key="p.value" :label="p.label" :value="p.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Prompt 模板">
                  <el-select v-model="form.templateId" placeholder="自动匹配" clearable style="width: 100%">
                    <el-option v-for="t in templateList" :key="t.id" :label="t.templateName" :value="t.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="关联知识库（RAG）">
                  <el-select v-model="form.kbId" placeholder="不使用" clearable filterable style="width: 100%">
                    <el-option v-for="kb in kbList" :key="kb.id" :label="`${kb.name}（${kb.docCount}篇）`" :value="kb.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="语气">
                  <el-select v-model="form.tone" style="width: 100%">
                    <el-option v-for="t in tones" :key="t.value" :label="t.label" :value="t.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="产品信息（可选）">
              <el-input v-model="form.productInfo" type="textarea" :rows="2" placeholder="产品名称、卖点..." />
            </el-form-item>

            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="字数">
                  <el-input-number v-model="form.wordCount" :min="50" :max="3000" :step="50" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="16">
                <el-form-item label="温度（越高越有创意）">
                  <el-slider v-model="form.temperature" :min="0" :max="1" :step="0.1" show-stops />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- Multi-Agent 协作模式开关 -->
            <el-divider style="margin: 12px 0" />
            <div style="margin-bottom: 12px">
              <el-switch
                v-model="useMultiAgent"
                active-text="Multi-Agent 协作生成"
                inactive-text="普通生成"
                style="margin-bottom: 8px"
              />
              <!-- Multi-Agent 参数（启用时展示） -->
              <div v-if="useMultiAgent" style="background: #f5f7fa; border-radius: 8px; padding: 12px; margin-top: 8px">
                <div style="font-size: 12px; color: #909399; margin-bottom: 8px">
                  多智能体协作模式：多个 AI Agent 协同完成内容生成，自动进行头脑风暴、撰写、评审和优化。
                </div>
                <el-form-item label="最大轮数" style="margin-bottom: 0">
                  <el-input-number
                    v-model="maxRounds"
                    :min="1"
                    :max="5"
                    :step="1"
                    style="width: 140px"
                  />
                  <span style="font-size: 12px; color: #909399; margin-left: 8px">（1-5 轮，默认 3）</span>
                </el-form-item>
              </div>
            </div>

            <div style="display: flex; gap: 8px">
              <el-button
                type="primary"
                :loading="streaming"
                @click="onGenerate"
                :disabled="!form.topic.trim()"
                class="generate-btn"
              >
                {{ streaming ? '生成中...' : (useMultiAgent ? 'Multi-Agent 生成' : '流式生成') }}
              </el-button>
              <el-button v-if="streaming" @click="stop">中断</el-button>
            </div>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右栏：生成结果 + 推荐配图 -->
      <el-col :span="15" style="height: 100%; display: flex; flex-direction: column">
        <!-- 生成结果 -->
        <el-card class="result-card" style="flex: 1; display: flex; flex-direction: column">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span class="card-title">生成结果</span>
              <el-tag v-if="stage" size="small" :type="stage.includes('错误') ? 'danger' : stage.includes('完成') ? 'success' : 'warning'" effect="light">
                {{ stage }}
              </el-tag>
            </div>
          </template>

          <!-- Multi-Agent 阶段状态指示器 -->
          <div v-if="useMultiAgent && multiAgentStages.length > 0" style="margin-bottom: 10px">
            <div style="display: flex; flex-wrap: wrap; gap: 6px">
              <el-tag
                v-for="(s, i) in multiAgentStages"
                :key="i"
                :type="i === multiAgentStages.length - 1 && streaming ? '' : 'success'"
                size="small"
                effect="plain"
              >
                {{ s.message }}
                <span style="font-size: 11px; color: #c0c4cc; margin-left: 4px">{{ s.time }}</span>
              </el-tag>
            </div>
          </div>

          <div v-if="warnings.length" style="margin-bottom: 8px">
            <el-alert v-for="(w, i) in warnings" :key="i" type="warning" :title="w" :closable="false" style="margin-bottom: 4px" />
          </div>

          <!-- 渲染模式切换 -->
          <div v-if="body" style="margin-bottom: 8px; display: flex; gap: 8px">
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button value="preview">预览</el-radio-button>
              <el-radio-button value="source">源码</el-radio-button>
            </el-radio-group>
          </div>

          <!-- Markdown 预览模式 -->
          <div
            v-if="viewMode === 'preview' && body"
            class="markdown-body"
            v-html="renderedMarkdown"
            style="flex: 1; overflow-y: auto; max-height: 60vh; padding: 16px; background: #fff; border: 1px solid #dcdfe6; border-radius: 4px; line-height: 1.8"
          />

          <!-- 源码编辑模式 -->
          <el-input
            v-else-if="body"
            v-model="body"
            type="textarea"
            :autosize="{ minRows: 14, maxRows: 30 }"
            style="flex: 1"
          />

          <!-- 空状态引导 -->
          <div v-else class="empty-state">
            <div class="empty-icon">&#x2728;</div>
            <h3 class="empty-title">输入主题，开始 AI 创作</h3>
            <p class="empty-desc">在左侧填写主题和参数，点击生成按钮，AI 将为你撰写专业文案并自动配图</p>
            <div class="empty-tags">
              <el-tag size="small" effect="plain" round>小红书种草</el-tag>
              <el-tag size="small" effect="plain" round>抖音脚本</el-tag>
              <el-tag size="small" effect="plain" round>旅行攻略</el-tag>
              <el-tag size="small" effect="plain" round>美食探店</el-tag>
              <el-tag size="small" effect="plain" round>产品测评</el-tag>
              <el-tag size="small" effect="plain" round>穿搭分享</el-tag>
            </div>
          </div>
        </el-card>

        <!-- ==================== AI 智能配图面板 ==================== -->
        <el-card v-if="showImagePanel" style="margin-top: 12px">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: 600">🎨 AI 智能配图</span>
              <div style="display: flex; align-items: center; gap: 8px">
                <el-tag v-if="selectedImageUrls.size > 0 && savedMedia.length === 0" type="success" size="small">
                  已选 {{ selectedImageUrls.size }} 张
                </el-tag>
                <el-button
                  v-if="selectedImageUrls.size > 0 && savedMedia.length === 0"
                  type="primary"
                  size="small"
                  :loading="saving"
                  @click="saveSelectedImages"
                >
                  {{ saveProgress || '下载并保存配图' }}
                </el-button>
                <el-tag v-if="savedMedia.length > 0" type="success" size="small">
                  已保存 {{ savedMedia.length }} 张
                </el-tag>
              </div>
            </div>
          </template>

          <!-- 提示词输入区 -->
          <div v-if="!savedMedia.length" style="margin-bottom: 12px">
            <div style="margin-bottom: 6px; font-size: 13px; color: #606266">
              中文绘图提示词（AI 自动从文案提取，可手动编辑后生成）
            </div>
            <div style="display: flex; gap: 8px">
              <el-input
                v-model="aiImagePrompt"
                type="textarea"
                :rows="2"
                :loading="promptLoading"
                placeholder="AI 正在分析文案内容..."
                style="flex: 1"
              />
              <el-button
                type="primary"
                :loading="aiGenerating || promptLoading"
                :disabled="!aiImagePrompt.trim()"
                @click="startAiGenerate"
                style="align-self: flex-end; height: 60px"
              >
                {{ aiGenerating ? '生成中...' : '生成配图' }}
              </el-button>
            </div>
          </div>

          <!-- AI 生成进度条 -->
          <div v-if="aiGenerating" style="margin-bottom: 12px">
            <el-progress
              :percentage="getProgressPercentage()"
              :stroke-width="16"
              :text-inside="true"
              status="warning"
            />
            <div style="text-align: center; color: #909399; font-size: 12px; margin-top: 4px">
              AI 正在创作配图，预计需要 30-60 秒...
            </div>
          </div>

          <!-- 错误提示 -->
          <el-alert
            v-if="aiError"
            :title="aiError"
            type="error"
            show-icon
            :closable="true"
            @close="aiError = ''"
            style="margin-bottom: 12px"
          />

          <!-- AI 生成的图片网格 -->
          <div v-if="aiImages.length > 0 && savedMedia.length === 0" class="media-recommend-grid">
            <div
              v-for="(url, idx) in aiImages"
              :key="url"
              class="media-recommend-item"
              :class="{ selected: selectedImageUrls.has(url) }"
              @click="toggleImageUrl(url)"
            >
              <img :src="url" :alt="'AI 配图 ' + (idx + 1)" />
              <div v-if="selectedImageUrls.has(url)" class="media-check-badge">✓</div>
              <div class="media-recommend-tags">AI 生成 #{{ idx + 1 }}</div>
            </div>
          </div>

          <!-- 已保存的配图 -->
          <div v-if="savedMedia.length > 0" class="media-recommend-grid">
            <div
              v-for="m in savedMedia"
              :key="m.id"
              class="media-recommend-item"
              @click="openPreview(m.fileUrl)"
            >
              <img :src="m.fileUrl" :alt="m.fileName" />
              <div class="media-recommend-tags">{{ m.tags }}</div>
            </div>
          </div>

          <!-- 空状态提示 -->
          <div v-if="!aiGenerating && aiImages.length === 0 && savedMedia.length === 0 && !aiError && !promptLoading" style="text-align: center; padding: 20px; color: #909399">
            编辑提示词后点击"生成配图"，AI 将为你创作 4 张配图
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图片预览 -->
    <el-dialog v-model="previewVisible" title="素材预览" width="60%" align-center>
      <img :src="previewImg" style="width: 100%; border-radius: 8px" />
    </el-dialog>
  </div>
</template>

<style scoped>
.workspace-container {
  height: calc(100vh - 120px);
}

/* 卡片标题带渐变条纹 */
.card-header-bar {
  position: relative;
  padding-left: 14px;
}
.card-header-bar::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 4px;
  border-radius: 2px;
  background: linear-gradient(180deg, #667eea, #764ba2);
}
.card-title {
  font-weight: 600;
  font-size: 16px;
  color: #1a1a2e;
  margin: 0;
}

.param-card {
  height: 100%;
  overflow-y: auto;
}

.result-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 生成按钮渐变 */
.generate-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  border: none !important;
  font-weight: 600;
  letter-spacing: 0.5px;
  padding: 10px 28px !important;
}
.generate-btn:hover {
  opacity: 0.9;
}
.generate-btn:disabled {
  opacity: 0.5;
}

/* 空状态引导 */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  animation: float 3s ease-in-out infinite;
}
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}
.empty-title {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 8px;
}
.empty-desc {
  font-size: 14px;
  color: #909399;
  margin: 0 0 24px;
  text-align: center;
  max-width: 360px;
  line-height: 1.6;
}
.empty-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.empty-tags .el-tag {
  cursor: default;
  color: #667eea;
  border-color: #667eea40;
}
.media-recommend-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.media-recommend-item {
  cursor: pointer;
  border-radius: 6px;
  overflow: hidden;
  transition: transform 0.2s;
}
.media-recommend-item:hover {
  transform: scale(1.03);
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.media-recommend-item img {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  display: block;
}
.media-recommend-tags {
  padding: 4px 6px;
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.media-recommend-item.selected {
  outline: 3px solid #409eff;
  border-radius: 6px;
}
.media-check-badge {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 28px;
  height: 28px;
  background: #409eff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.media-recommend-item {
  position: relative;
}

/* Markdown 渲染样式 */
.markdown-body h1, .markdown-body h2, .markdown-body h3, .markdown-body h4 {
  margin: 16px 0 8px;
  font-weight: 600;
  color: #303133;
}
.markdown-body h1 { font-size: 22px; border-bottom: 1px solid #ebeef5; padding-bottom: 8px; }
.markdown-body h2 { font-size: 18px; }
.markdown-body h3 { font-size: 16px; }
.markdown-body h4 { font-size: 15px; }
.markdown-body p { margin: 8px 0; }
.markdown-body ul, .markdown-body ol { padding-left: 20px; margin: 8px 0; }
.markdown-body li { margin: 4px 0; }
.markdown-body strong { color: #303133; }
.markdown-body em { color: #606266; }
.markdown-body blockquote { border-left: 4px solid #409eff; padding: 8px 16px; margin: 8px 0; background: #f5f7fa; color: #606266; }
.markdown-body code { background: #f5f7fa; padding: 2px 6px; border-radius: 3px; font-size: 13px; color: #e6a23c; }
.markdown-body pre { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px; overflow-x: auto; }
.markdown-body pre code { background: none; color: inherit; padding: 0; }
.markdown-body hr { border: none; border-top: 1px solid #ebeef5; margin: 16px 0; }
.markdown-body a { color: #409eff; text-decoration: none; }
.markdown-body table { border-collapse: collapse; width: 100%; margin: 8px 0; }
.markdown-body th, .markdown-body td { border: 1px solid #ebeef5; padding: 8px 12px; text-align: left; }
.markdown-body th { background: #f5f7fa; font-weight: 600; }
</style>
