<!--
  ApiKeysPanel —— LLM Provider API Key 管理（添加/删除/设默认）
  从原 src/views/Settings.vue 拆出。
-->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiKeyApi, type ApiKeyItem, type SaveApiKeyDTO } from '@/api/apikey'

const apiKeys = ref<ApiKeyItem[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const formData = ref<SaveApiKeyDTO>({ provider: 'DEEPSEEK', apiKey: '', baseUrl: '', isDefault: false })

const providerOptions = [
  { label: 'DeepSeek', value: 'DEEPSEEK' },
  { label: '通义千问 (Qwen)', value: 'QWEN' },
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'Claude', value: 'CLAUDE' },
  { label: '智谱 (GLM)', value: 'GLM' },
]

async function loadApiKeys() {
  loading.value = true
  try { apiKeys.value = await apiKeyApi.list() }
  catch (e) { console.error('加载 API Key 列表失败', e) }
  finally { loading.value = false }
}

function openAddDialog() {
  formData.value = { provider: 'DEEPSEEK', apiKey: '', baseUrl: '', isDefault: false }
  dialogVisible.value = true
}

async function handleSave() {
  if (!formData.value.apiKey) { ElMessage.warning('请输入 API Key'); return }
  try {
    await apiKeyApi.save(formData.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadApiKeys()
  } catch (e) { console.error('保存 API Key 失败', e) }
}

async function handleDelete(provider: string) {
  try {
    await ElMessageBox.confirm(`确定要删除 ${provider} 的 API Key 吗？`, '确认删除', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning',
    })
    await apiKeyApi.delete(provider)
    ElMessage.success('删除成功')
    await loadApiKeys()
  } catch (e) { if (e !== 'cancel') console.error('删除 API Key 失败', e) }
}

function getProviderLabel(value: string) {
  return providerOptions.find(o => o.value === value)?.label || value
}

onMounted(loadApiKeys)
</script>

<template>
  <div class="panel">
    <header class="panel-header">
      <div>
        <h2>🔑 API Key 管理</h2>
        <p class="panel-desc">配置你自己的 LLM Provider Key，系统会优先使用你的配额</p>
      </div>
      <el-button type="primary" @click="openAddDialog">添加 API Key</el-button>
    </header>

    <el-table :data="apiKeys" v-loading="loading" empty-text="暂未配置 API Key" style="width: 100%">
      <el-table-column label="供应商" prop="provider" width="180">
        <template #default="{ row }">{{ getProviderLabel(row.provider) }}</template>
      </el-table-column>
      <el-table-column label="API Key" prop="maskedKey" min-width="200">
        <template #default="{ row }">
          <span style="color: #909399; font-family: monospace">{{ row.maskedKey }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Base URL" prop="baseUrl" min-width="200">
        <template #default="{ row }">
          <span style="color: #909399">{{ row.baseUrl || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag>
          <el-tag v-else type="info" size="small">已配置</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button type="danger" text size="small" @click="handleDelete(row.provider)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="hint">
      <el-icon><InfoFilled /></el-icon>
      <span>配置自己的 API Key 后，系统将使用你的个人配额调用 AI 服务。标记为"默认"的供应商将优先使用。</span>
    </div>

    <el-dialog v-model="dialogVisible" title="添加 API Key" width="500px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="供应商">
          <el-select v-model="formData.provider" style="width: 100%">
            <el-option v-for="opt in providerOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="formData.apiKey" placeholder="请输入 API Key" type="password" show-password />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="formData.baseUrl" placeholder="可选，自定义接口地址" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-checkbox v-model="formData.isDefault">将此供应商设为默认</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel { display: flex; flex-direction: column; gap: 16px; }
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.panel-header h2 { margin: 0 0 4px; font-size: 20px; color: #111827; }
.panel-desc { margin: 0; color: #6b7280; font-size: 13px; }
.hint { display: flex; gap: 6px; align-items: center; color: #6b7280; font-size: 13px; padding: 10px 14px; background: #f9fafb; border-radius: 6px; }
</style>
