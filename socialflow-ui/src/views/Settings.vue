<!--
  ============================================================
  Settings.vue —— 个人设置页面
  ============================================================
  展示当前用户的账号信息、资料编辑、头像上传
  和 API Key 管理功能。
  ============================================================
-->

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { apiKeyApi, type ApiKeyItem, type SaveApiKeyDTO } from '@/api/apikey'
import http from '@/api/http'
import { put } from '@/api/http'
import PageHeader from '@/components/PageHeader.vue'

/** 获取用户状态 */
const userStore = useUserStore()

/** API Key 列表 */
const apiKeys = ref<ApiKeyItem[]>([])
const loading = ref(false)

/** 添加对话框状态 */
const dialogVisible = ref(false)
const formData = ref<SaveApiKeyDTO>({
  provider: 'DEEPSEEK',
  apiKey: '',
  baseUrl: '',
  isDefault: false,
})

/** 供应商选项 */
const providerOptions = [
  { label: 'DeepSeek', value: 'DEEPSEEK' },
  { label: '通义千问 (Qwen)', value: 'QWEN' },
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'Claude', value: 'CLAUDE' },
  { label: '智谱 (GLM)', value: 'GLM' },
]

/* ===================== 编辑资料相关 ===================== */
/** 编辑资料弹窗可见性 */
const profileEditVisible = ref(false)
/** 编辑资料表单 */
const profileForm = ref({
  nickname: '',
})
/** 编辑资料保存中 */
const profileSaving = ref(false)

/* ===================== 头像上传相关 ===================== */
/** 头像上传中 */
const avatarUploading = ref(false)
/** 头像文件输入引用 */
const avatarInputRef = ref<HTMLInputElement | null>(null)

/** 加载 API Key 列表 */
async function loadApiKeys() {
  loading.value = true
  try {
    apiKeys.value = await apiKeyApi.list()
  } catch (e) {
    console.error('加载 API Key 列表失败', e)
  } finally {
    loading.value = false
  }
}

/** 打开添加对话框 */
function openAddDialog() {
  formData.value = { provider: 'DEEPSEEK', apiKey: '', baseUrl: '', isDefault: false }
  dialogVisible.value = true
}

/** 保存 API Key */
async function handleSave() {
  if (!formData.value.apiKey) {
    ElMessage.warning('请输入 API Key')
    return
  }
  try {
    await apiKeyApi.save(formData.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadApiKeys()
  } catch (e) {
    console.error('保存 API Key 失败', e)
  }
}

/** 删除 API Key */
async function handleDelete(provider: string) {
  try {
    await ElMessageBox.confirm(`确定要删除 ${provider} 的 API Key 吗？`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await apiKeyApi.delete(provider)
    ElMessage.success('删除成功')
    await loadApiKeys()
  } catch (e) {
    // 用户取消或删除失败
    if (e !== 'cancel') {
      console.error('删除 API Key 失败', e)
    }
  }
}

/** 获取供应商的显示名称 */
function getProviderLabel(value: string): string {
  const opt = providerOptions.find((o) => o.value === value)
  return opt ? opt.label : value
}

/**
 * 打开编辑资料弹窗
 */
function openProfileEdit() {
  profileForm.value.nickname = userStore.user?.nickname || ''
  profileEditVisible.value = true
}

/**
 * 保存资料编辑（PUT /auth/profile）
 */
async function handleProfileSave() {
  if (!profileForm.value.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  profileSaving.value = true
  try {
    const updated = await put<any>('/auth/profile', { nickname: profileForm.value.nickname })
    ElMessage.success('资料更新成功')
    profileEditVisible.value = false
    /* 更新本地用户信息 */
    if (userStore.user) {
      const newUser = { ...userStore.user, nickname: profileForm.value.nickname }
      if (updated && updated.nickname) {
        newUser.nickname = updated.nickname
      }
      userStore.setSession(userStore.token!, newUser)
    }
  } catch (e) {
    /* 错误已由拦截器处理 */
  } finally {
    profileSaving.value = false
  }
}

/**
 * 点击头像区域，触发文件选择
 */
function triggerAvatarUpload() {
  avatarInputRef.value?.click()
}

/**
 * 处理头像文件选择，上传到 POST /auth/avatar（multipart）
 */
async function handleAvatarChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  /* 校验文件类型和大小 */
  const validTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  if (!validTypes.includes(file.type)) {
    ElMessage.warning('请选择 JPG/PNG/GIF/WebP 格式的图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }

  avatarUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await http.post<any>('/auth/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    const data = res.data?.data
    ElMessage.success('头像上传成功')
    /* 更新本地用户头像 URL */
    if (userStore.user) {
      const avatarUrl = data?.avatarUrl || data || ''
      const newUser = { ...userStore.user, avatarUrl: typeof avatarUrl === 'string' ? avatarUrl : userStore.user.avatarUrl }
      userStore.setSession(userStore.token!, newUser)
    }
  } catch (e) {
    /* 错误已由拦截器处理 */
  } finally {
    avatarUploading.value = false
    /* 重置 input 以便选择同一文件时也能触发 */
    if (target) target.value = ''
  }
}

onMounted(() => {
  loadApiKeys()
})
</script>

<template>
  <div class="sf-page-container">
    <PageHeader
      title="设置"
      subtitle="管理账户信息和 API 密钥"
      icon="Setting"
    />
    <!-- 账号信息 -->
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <div style="display: flex; align-items: center">
            <el-icon style="margin-right: 8px"><User /></el-icon>
            <span>账号信息</span>
          </div>
          <el-button type="primary" size="small" @click="openProfileEdit">
            编辑资料
          </el-button>
        </div>
      </template>

      <div style="display: flex; flex-direction: column; gap: 20px; align-items: stretch">
        <!-- 头像区域（居中置顶） -->
        <div style="text-align: center; display: flex; flex-direction: column; align-items: center">
          <div
            class="avatar-wrapper"
            @click="triggerAvatarUpload"
            v-loading="avatarUploading"
          >
            <img
              v-if="userStore.user?.avatarUrl"
              :src="userStore.user.avatarUrl"
              alt="头像"
              class="avatar-img"
            />
            <div v-else class="avatar-placeholder">
              <el-icon :size="40" color="#c0c4cc"><Avatar /></el-icon>
            </div>
            <div class="avatar-overlay">
              <el-icon :size="20" color="#fff"><Camera /></el-icon>
            </div>
          </div>
          <div style="font-size: 12px; color: #909399; margin-top: 8px">点击更换头像</div>
          <!-- 隐藏的文件输入 -->
          <input
            ref="avatarInputRef"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            style="display: none"
            @change="handleAvatarChange"
          />
        </div>

        <!-- 账号详情 -->
        <div style="width: 100%">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户ID">
              {{ userStore.user?.id || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              {{ userStore.user?.email || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="昵称">
              {{ userStore.user?.nickname || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="账号状态">
              <el-tag :type="userStore.user?.status === 1 ? 'success' : 'danger'" size="small">
                {{ userStore.user?.status === 1 ? '正常' : '已禁用' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-card>

    <!-- API Key 管理 -->
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <div style="display: flex; align-items: center">
            <el-icon style="margin-right: 8px"><Setting /></el-icon>
            <span>API Key 管理</span>
          </div>
          <el-button type="primary" size="small" @click="openAddDialog">
            添加 API Key
          </el-button>
        </div>
      </template>

      <!-- API Key 列表 -->
      <el-table :data="apiKeys" v-loading="loading" style="width: 100%" empty-text="暂未配置 API Key">
        <el-table-column label="供应商" prop="provider" width="180">
          <template #default="{ row }">
            {{ getProviderLabel(row.provider) }}
          </template>
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
            <el-button type="danger" text size="small" @click="handleDelete(row.provider)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; color: #909399; font-size: 13px">
        <el-icon style="margin-right: 4px"><InfoFilled /></el-icon>
        配置自己的 API Key 后，系统将使用你的个人配额调用 AI 服务。标记为"默认"的供应商将优先使用。
      </div>
    </el-card>

    <!-- 添加 API Key 对话框 -->
    <el-dialog v-model="dialogVisible" title="添加 API Key" width="500px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="供应商">
          <el-select v-model="formData.provider" placeholder="请选择供应商" style="width: 100%">
            <el-option
              v-for="opt in providerOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="formData.apiKey"
            placeholder="请输入 API Key"
            type="password"
            show-password
          />
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

    <!-- ==================== 编辑资料弹窗 ==================== -->
    <el-dialog
      v-model="profileEditVisible"
      title="编辑资料"
      width="450px"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="昵称">
          <el-input
            v-model="profileForm.nickname"
            placeholder="请输入昵称"
            clearable
            maxlength="30"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="handleProfileSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 头像样式 */
.avatar-wrapper {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  border: 2px solid #ebeef5;
  transition: border-color 0.3s;
}
.avatar-wrapper:hover {
  border-color: #409eff;
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.avatar-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 28px;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
}
.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}
</style>
