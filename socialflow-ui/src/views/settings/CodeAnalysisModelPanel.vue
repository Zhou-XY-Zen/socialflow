<!--
  CodeAnalysisModelPanel —— 代码分析模块的 LLM 配置
  后端表：code_analysis_config（每用户一条）。未配置时接口返回 null，展示"系统默认"。
  保存后下次分析自动生效，不需要重启后端。
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { CodeAnalysisLlmConfig, CodeAnalysisLlmConfigDTO } from '@/types/codeAnalysis'

type Provider = 'DEEPSEEK' | 'QWEN' | 'GLM' | 'OPENAI' | 'CLAUDE'

/** 可选模型清单 —— 按 provider 联动 */
const MODEL_OPTIONS: Record<Provider, { label: string; value: string; tag?: string }[]> = {
  DEEPSEEK: [
    { label: 'DeepSeek V4 Pro', value: 'deepseek-v4-pro', tag: '旗舰' },
    { label: 'DeepSeek V4 Flash', value: 'deepseek-v4-flash', tag: '性价比' },
    { label: 'DeepSeek Chat (旧 V3.2，2026-07-24 停用)', value: 'deepseek-chat', tag: '停用中' },
    { label: 'DeepSeek Reasoner (旧 R1，2026-07-24 停用)', value: 'deepseek-reasoner', tag: '停用中' },
  ],
  QWEN: [
    { label: '通义千问 Max', value: 'qwen-max' },
    { label: '通义千问 Plus', value: 'qwen-plus' },
    { label: '通义千问 Turbo', value: 'qwen-turbo' },
  ],
  GLM: [
    { label: 'GLM-4 Plus', value: 'glm-4-plus' },
    { label: 'GLM-4 Flash', value: 'glm-4-flash' },
  ],
  OPENAI: [
    { label: 'GPT-4o', value: 'gpt-4o' },
    { label: 'GPT-4o Mini', value: 'gpt-4o-mini' },
  ],
  CLAUDE: [
    { label: 'Claude Sonnet 4.6', value: 'claude-sonnet-4-6' },
    { label: 'Claude Haiku 4', value: 'claude-haiku-4' },
  ],
}

const PROVIDER_OPTIONS = [
  { label: 'DeepSeek', value: 'DEEPSEEK' as Provider },
  { label: '通义千问 (Qwen)', value: 'QWEN' as Provider },
  { label: '智谱 (GLM)', value: 'GLM' as Provider },
  { label: 'OpenAI', value: 'OPENAI' as Provider },
  { label: 'Claude', value: 'CLAUDE' as Provider },
]

/** 系统默认（展示用）—— 和 application.yml 的 socialflow.code-analysis.* 对应 */
const SYSTEM_DEFAULT = { provider: 'DEEPSEEK' as Provider, model: 'deepseek-v4-pro', temperature: 0.3 }

const current = ref<CodeAnalysisLlmConfig | null>(null)
const form = ref<CodeAnalysisLlmConfigDTO>({ ...SYSTEM_DEFAULT })
const loading = ref(false)
const saving = ref(false)

const modelChoices = computed(() => MODEL_OPTIONS[form.value.provider] || [])
const isUsingDefault = computed(() => current.value == null)

async function load() {
  loading.value = true
  try {
    const cfg = await codeAnalysisApi.getLlmConfig()
    current.value = cfg
    if (cfg) {
      form.value = {
        provider: cfg.provider,
        model: cfg.model,
        temperature: Number(cfg.temperature) || 0.3,
      }
    } else {
      form.value = { ...SYSTEM_DEFAULT }
    }
  } catch (e: any) {
    ElMessage.error('加载配置失败：' + (e?.message || ''))
  } finally {
    loading.value = false
  }
}

function onProviderChange() {
  // 切 provider 时如果当前 model 不在新 provider 的列表里，取第一个
  const opts = MODEL_OPTIONS[form.value.provider] || []
  if (!opts.find(o => o.value === form.value.model)) {
    form.value.model = opts[0]?.value || ''
  }
}

async function save() {
  if (!form.value.model) { ElMessage.warning('请选择模型'); return }
  saving.value = true
  try {
    current.value = await codeAnalysisApi.saveLlmConfig(form.value)
    ElMessage.success('配置已保存，下次分析自动生效')
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || ''))
  } finally {
    saving.value = false
  }
}

async function reset() {
  try {
    await ElMessageBox.confirm(
      '恢复系统默认后，将删除你的个人配置，下次分析使用系统默认（deepseek-v4-pro）。',
      '确认恢复默认', { type: 'warning' })
    await codeAnalysisApi.resetLlmConfig()
    ElMessage.success('已恢复系统默认')
    await load()
  } catch (e) { /* user cancel */ }
}

onMounted(load)
</script>

<template>
  <div class="panel" v-loading="loading">
    <header class="panel-header">
      <h2>🧠 代码分析 · 模型</h2>
      <p class="panel-desc">为"项目概览 / 提交审查 / 对比分析"选择 LLM 供应商和模型</p>
    </header>

    <div class="status-row">
      <el-tag v-if="isUsingDefault" type="info" effect="plain">当前使用：系统默认</el-tag>
      <el-tag v-else type="success" effect="plain">当前使用：个人配置</el-tag>
      <span class="status-now">
        {{ current?.provider || SYSTEM_DEFAULT.provider }} /
        <strong>{{ current?.model || SYSTEM_DEFAULT.model }}</strong>
        · temperature {{ Number(current?.temperature ?? SYSTEM_DEFAULT.temperature).toFixed(2) }}
      </span>
    </div>

    <el-form label-width="130px" style="max-width: 640px">
      <el-form-item label="LLM Provider">
        <el-select v-model="form.provider" style="width: 100%" @change="onProviderChange">
          <el-option v-for="o in PROVIDER_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>

      <el-form-item label="模型">
        <el-select v-model="form.model" style="width: 100%" placeholder="请选择模型">
          <el-option v-for="m in modelChoices" :key="m.value" :label="m.label" :value="m.value">
            <span>{{ m.label }}</span>
            <el-tag v-if="m.tag" size="small" class="model-tag"
                    :type="m.tag === '旗舰' ? 'success' : m.tag === '性价比' ? 'primary' : 'warning'"
                    effect="plain">{{ m.tag }}</el-tag>
          </el-option>
        </el-select>
        <div class="field-hint">不同 provider 的模型列表会自动联动切换</div>
      </el-form-item>

      <el-form-item label="Temperature">
        <el-slider v-model="form.temperature" :min="0" :max="1" :step="0.05" show-input
                   style="width: 420px" />
        <div class="field-hint">越低越严谨（推荐 0.2-0.4），越高越有创造性</div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">💾 保存</el-button>
        <el-button @click="reset" :disabled="isUsingDefault">↺ 恢复系统默认</el-button>
      </el-form-item>
    </el-form>

    <div class="info-card">
      <div class="info-title">ℹ️ 优先级说明</div>
      <ol class="info-list">
        <li>保存后的<strong>个人配置</strong>优先，每次触发代码分析时自动读取</li>
        <li>未配置（"系统默认"状态）时，回退到 <code>application.yml</code> 的 <code>socialflow.code-analysis.*</code>（当前 <code>deepseek-v4-pro</code>）</li>
        <li>API Key 仍走 <strong>"API Key 管理"页</strong>的配置；本页只管模型选择</li>
      </ol>
    </div>
  </div>
</template>

<style scoped>
.panel { display: flex; flex-direction: column; gap: 18px; }
.panel-header h2 { margin: 0 0 4px; font-size: 20px; color: #111827; }
.panel-desc { margin: 0; color: #6b7280; font-size: 13px; }

.status-row {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px; background: linear-gradient(135deg, rgba(102,126,234,0.05), rgba(118,75,162,0.05));
  border: 1px solid rgba(102,126,234,0.2); border-radius: 8px;
}
.status-now { color: #4b5563; font-size: 13px; font-variant-numeric: tabular-nums; }
.status-now strong { color: #6d28d9; }

.field-hint { color: #9ca3af; font-size: 12px; margin-top: 4px; }

.model-tag { margin-left: 8px; }

.info-card {
  background: #f9fafb; border: 1px solid #e5e7eb;
  padding: 14px 18px; border-radius: 8px;
}
.info-title { font-size: 13px; color: #374151; font-weight: 600; margin-bottom: 8px; }
.info-list { margin: 0; padding-left: 20px; color: #6b7280; font-size: 13px; line-height: 1.8; }
.info-list code { background: #e5e7eb; padding: 1px 6px; border-radius: 3px; font-size: 12px; color: #6d28d9; }
</style>
