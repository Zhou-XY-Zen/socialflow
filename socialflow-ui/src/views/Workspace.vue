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

/** 推荐配图 */
const recommendedMedia = ref<MediaAssetVO[]>([])
const mediaLoading = ref(false)
const previewImg = ref('')
const previewVisible = ref(false)

async function searchMatchingMedia(generatedText: string) {
  mediaLoading.value = true
  recommendedMedia.value = []
  try {
    const seen = new Set<string | number>()
    const results: MediaAssetVO[] = []

    // 从生成的文案内容中提取关键词（而不是只用主题）
    const keywords = extractKeywords(generatedText, form.topic.trim())

    // 逐个关键词搜索，合并结果
    for (const kw of keywords) {
      if (results.length >= 8) break
      const r = await mediaApi.list({ pageNum: 1, pageSize: 4, fileType: 'IMAGE', keyword: kw })
      for (const item of r.records) {
        if (!seen.has(item.id) && results.length < 8) {
          seen.add(item.id)
          results.push(item)
        }
      }
    }

    // 兜底：用通用词搜索
    if (results.length < 4) {
      const fallbackWords = ['产品', '美食', '风景', '时尚', '生活', '美妆', '护肤']
      for (const fw of fallbackWords) {
        if (results.length >= 8) break
        const r = await mediaApi.list({ pageNum: 1, pageSize: 4, fileType: 'IMAGE', keyword: fw })
        for (const item of r.records) {
          if (!seen.has(item.id) && results.length < 8) {
            seen.add(item.id)
            results.push(item)
          }
        }
      }
    }

    recommendedMedia.value = results
  } catch { recommendedMedia.value = [] }
  finally { mediaLoading.value = false }
}

/**
 * 从文案内容中提取关键词用于搜索素材
 * 策略：提取高频名词短语 + 主题词拆分
 */
function extractKeywords(text: string, topic: string): string[] {
  const keywords: string[] = []
  const added = new Set<string>()
  const addWord = (w: string) => { if (w.length >= 2 && !added.has(w)) { added.add(w); keywords.push(w) } }

  // 1. 主题词优先级最高（用户明确想要的内容）
  addWord(topic)
  topic.split(/[,，\s、·]+/).forEach(addWord)
  splitChinese(topic).forEach(addWord)

  // 2. 从文案标题（前50字）提取品类词——标题最能代表文案核心
  const titleArea = text.substring(0, Math.min(text.length, 200))
  const categoryWords = [
    '口红', '唇膏', '唇釉', '唇彩',
    '护肤', '面霜', '精华', '防晒', '面膜', '洗面奶', '乳液', '眼霜',
    '香水', '香氛', '美妆', '化妆品', '彩妆', '粉底', '眼影', '腮红',
    '穿搭', '连衣裙', '裙子', '仙女裙', '碎花裙', '半裙', '长裙', '短裙',
    '牛仔裤', '西装', '大衣', '毛衣', '衬衫', '卫衣', '外套', '风衣',
    '球鞋', '高跟鞋', '运动鞋', '包包', '手表', '项链', '耳环', '墨镜',
    '汉服', '旗袍', '丝巾', '女装', '男装', '童装',
    '咖啡', '奶茶', '拿铁', '饮品', '果汁',
    '蛋糕', '甜品', '面包', '烘焙', '巧克力', '冰淇淋',
    '火锅', '烧烤', '寿司', '拉面', '牛排', '披萨', '沙拉', '饺子',
    '手机', '电脑', '耳机', '相机', '键盘', '平板',
    '猫', '猫咪', '狗', '狗狗', '宠物', '萌宠',
    '健身', '瑜伽', '跑步', '游泳', '骑行',
    '海滩', '山峰', '城市', '森林', '日落', '樱花', '雪山', '湖泊',
    '绿植', '蜡烛', '书架', '沙发', '厨房', '卧室',
    '跑车', '汽车', '摩托车',
    '婚礼', '烟花', '圣诞', '生日',
    '玫瑰', '向日葵', '郁金香', '花束',
    '婴儿', '亲子', '家庭',
  ]

  // 先从标题区域提取（权重最高）
  for (const word of categoryWords) {
    if (titleArea.includes(word)) addWord(word)
  }

  // 再从全文提取（补充）
  for (const word of categoryWords) {
    if (text.includes(word)) addWord(word)
  }

  return keywords.slice(0, 10)
}

/** 把中文文本每2个字拆一次（简易分词） */
function splitChinese(text: string): string[] {
  const words: string[] = []
  const clean = text.replace(/[^\u4e00-\u9fff]/g, '')
  for (let i = 0; i < clean.length - 1; i += 2) {
    words.push(clean.substring(i, i + 2))
  }
  return words
}

/** 勾选的配图 ID 列表 */
const selectedMediaIds = ref<Set<string | number>>(new Set())
/** 最新生成的文案 ID（用于绑定配图） */
const lastContentId = ref<string | number | null>(null)
/** 保存中 */
const saving = ref(false)

function toggleMedia(id: string | number) {
  if (selectedMediaIds.value.has(id)) {
    selectedMediaIds.value.delete(id)
  } else {
    selectedMediaIds.value.add(id)
  }
  // 触发响应式更新
  selectedMediaIds.value = new Set(selectedMediaIds.value)
}

function isSelected(id: string | number) {
  return selectedMediaIds.value.has(id)
}

/** 是否已保存配图 */
const mediaSaved = ref(false)

async function saveWithMedia() {
  if (!lastContentId.value || selectedMediaIds.value.size === 0) {
    ElMessage.warning('请先生成文案并勾选配图')
    return
  }
  saving.value = true
  try {
    const ids = [...selectedMediaIds.value]
    await post<void>(`/content/${lastContentId.value}/bindMedia`, ids)
    ElMessage.success(`已保存，关联了 ${ids.length} 张配图`)
    // 保存后只保留已选图片，清除未选的
    recommendedMedia.value = recommendedMedia.value.filter(m => selectedMediaIds.value.has(m.id))
    mediaSaved.value = true
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e.message || ''))
  } finally {
    saving.value = false
  }
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
  recommendedMedia.value = []
  selectedMediaIds.value = new Set()
  lastContentId.value = null
  mediaSaved.value = false

  /* 根据模式选择 SSE 端点和请求体 */
  const sseUrl = useMultiAgent.value
    ? '/api/v1/content/generate-multi-agent'
    : '/api/v1/content/generate-stream'
  const requestBody = useMultiAgent.value
    ? { ...form, maxRounds: maxRounds.value }
    : form

  await start(sseUrl, requestBody, {
    onMessage: (data) => { const d = data as { token?: string }; if (d?.token) body.value += d.token },
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
      stage.value = '生成完成'
      // 搜索匹配素材
      searchMatchingMedia(body.value)
      // 获取刚保存的文案 ID（取最新一条）
      try {
        const { contentApi } = await import('@/api/content')
        const list = await contentApi.list({ pageNum: 1, pageSize: 1 })
        if (list.records.length > 0) {
          lastContentId.value = list.records[0].id
        }
      } catch { /* ignore */ }
    },
    onError: (data) => { stage.value = `错误：${data.message}` },
  })
}
</script>

<template>
  <div class="workspace-container">
    <el-row :gutter="20" style="height: 100%">
      <!-- 左栏：生成参数 -->
      <el-col :span="9">
        <el-card class="param-card">
          <h3 style="margin-top: 0">AI 文案工作台</h3>
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
              <el-button type="primary" :loading="streaming" @click="onGenerate" :disabled="!form.topic.trim()">
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
            <div style="display: flex; justify-content: space-between">
              <span>生成结果</span>
              <span v-if="stage" style="color: #999; font-size: 13px">{{ stage }}</span>
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

          <!-- 源码编辑模式 / 空状态 -->
          <el-input
            v-else
            v-model="body"
            type="textarea"
            :autosize="{ minRows: 14, maxRows: 30 }"
            placeholder="AI 生成的内容会出现在这里..."
            style="flex: 1"
          />
        </el-card>

        <!-- 推荐配图（可勾选 + 保存） -->
        <el-card v-if="recommendedMedia.length > 0 || mediaLoading" style="margin-top: 12px" v-loading="mediaLoading">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>{{ mediaSaved ? '已保存的配图' : '推荐配图（点击勾选，绑定到文案）' }}</span>
              <div v-if="!mediaSaved" style="display: flex; align-items: center; gap: 8px">
                <el-tag v-if="selectedMediaIds.size > 0" type="success" size="small">
                  已选 {{ selectedMediaIds.size }} 张
                </el-tag>
                <el-button
                  v-if="selectedMediaIds.size > 0"
                  type="primary"
                  size="small"
                  :loading="saving"
                  @click="saveWithMedia"
                >
                  保存配图
                </el-button>
              </div>
              <el-tag v-else type="success" size="small">已保存 {{ recommendedMedia.length }} 张</el-tag>
            </div>
          </template>
          <div class="media-recommend-grid">
            <div
              v-for="m in recommendedMedia"
              :key="m.id"
              class="media-recommend-item"
              :class="{ selected: isSelected(m.id) }"
              @click="mediaSaved ? openPreview(m.fileUrl) : toggleMedia(m.id)"
            >
              <img :src="m.fileUrl" :alt="m.fileName" />
              <div v-if="isSelected(m.id) && !mediaSaved" class="media-check-badge">
                <el-icon :size="20" color="#fff"><Select /></el-icon>
              </div>
              <div class="media-recommend-tags">{{ m.tags }}</div>
            </div>
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
.param-card {
  height: 100%;
  overflow-y: auto;
}
.result-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
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
