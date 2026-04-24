<!--
  ProfilePanel —— 账号信息（头像/昵称/邮箱/状态）+ 编辑资料 + 头像上传
  从原 src/views/Settings.vue 拆出。
-->
<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import http, { put } from '@/api/http'

const userStore = useUserStore()

const profileEditVisible = ref(false)
const profileForm = ref({ nickname: '' })
const profileSaving = ref(false)
const avatarUploading = ref(false)
const avatarInputRef = ref<HTMLInputElement | null>(null)

function openProfileEdit() {
  profileForm.value.nickname = userStore.user?.nickname || ''
  profileEditVisible.value = true
}

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
    if (userStore.user) {
      const newUser = { ...userStore.user, nickname: profileForm.value.nickname }
      if (updated && updated.nickname) newUser.nickname = updated.nickname
      userStore.setSession(userStore.token!, newUser)
    }
  } finally {
    profileSaving.value = false
  }
}

function triggerAvatarUpload() { avatarInputRef.value?.click() }

async function handleAvatarChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  const validTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  if (!validTypes.includes(file.type)) { ElMessage.warning('请选择 JPG/PNG/GIF/WebP 格式的图片'); return }
  if (file.size > 5 * 1024 * 1024) { ElMessage.warning('图片大小不能超过 5MB'); return }
  avatarUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await http.post<any>('/auth/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    const data = res.data?.data
    ElMessage.success('头像上传成功')
    if (userStore.user) {
      const avatarUrl = data?.avatarUrl || data || ''
      const newUser = { ...userStore.user, avatarUrl: typeof avatarUrl === 'string' ? avatarUrl : userStore.user.avatarUrl }
      userStore.setSession(userStore.token!, newUser)
    }
  } finally {
    avatarUploading.value = false
    if (target) target.value = ''
  }
}
</script>

<template>
  <div class="panel">
    <header class="panel-header">
      <h2>👤 账号信息</h2>
      <p class="panel-desc">管理你的账户资料和头像</p>
    </header>

    <div class="account-box">
      <div class="avatar-col">
        <div class="avatar-wrapper" @click="triggerAvatarUpload" v-loading="avatarUploading">
          <img v-if="userStore.user?.avatarUrl" :src="userStore.user.avatarUrl" class="avatar-img" alt="头像" />
          <div v-else class="avatar-placeholder">
            <el-icon :size="40" color="#c0c4cc"><Avatar /></el-icon>
          </div>
          <div class="avatar-overlay">
            <el-icon :size="20" color="#fff"><Camera /></el-icon>
          </div>
        </div>
        <div class="avatar-hint">点击更换头像</div>
        <input ref="avatarInputRef" type="file"
               accept="image/jpeg,image/png,image/gif,image/webp"
               style="display: none" @change="handleAvatarChange" />
      </div>

      <div class="info-col">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户 ID">{{ userStore.user?.id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ userStore.user?.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ userStore.user?.nickname || '-' }}</el-descriptions-item>
          <el-descriptions-item label="账号状态">
            <el-tag :type="userStore.user?.status === 1 ? 'success' : 'danger'" size="small">
              {{ userStore.user?.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 16px">
          <el-button type="primary" @click="openProfileEdit">编辑资料</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="profileEditVisible" title="编辑资料" width="450px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="昵称">
          <el-input v-model="profileForm.nickname" placeholder="请输入昵称"
                    clearable maxlength="30" show-word-limit />
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
.panel { display: flex; flex-direction: column; gap: 20px; }
.panel-header h2 { margin: 0 0 4px; font-size: 20px; color: #111827; }
.panel-desc { margin: 0; color: #6b7280; font-size: 13px; }

.account-box { display: grid; grid-template-columns: auto 1fr; gap: 32px; align-items: start; padding-top: 12px; }
.avatar-col { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.avatar-wrapper {
  width: 96px; height: 96px; border-radius: 50%;
  overflow: hidden; cursor: pointer; position: relative;
  border: 2px solid #ebeef5; transition: border-color 0.2s;
}
.avatar-wrapper:hover { border-color: #409eff; }
.avatar-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.avatar-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: #f5f7fa; }
.avatar-overlay {
  position: absolute; bottom: 0; left: 0; right: 0; height: 28px;
  background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity 0.2s;
}
.avatar-wrapper:hover .avatar-overlay { opacity: 1; }
.avatar-hint { font-size: 12px; color: #909399; }

.info-col { min-width: 0; }
</style>
