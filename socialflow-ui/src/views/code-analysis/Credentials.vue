<!--
  Credentials.vue —— Git 仓库凭证管理
  功能：
    - 列表展示（token 掩码 / 默认徽章 / 测试状态）
    - 添加 / 编辑 / 删除 / 测试连接
    - 凭证按 git_host 划分，触发分析时自动匹配
-->
<script setup lang="ts">
import { onMounted, ref, computed, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { RepoAuthCredential } from '@/types/codeAnalysis'

const list = ref<RepoAuthCredential[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const testingId = ref<string | null>(null)
const showToken = ref(false)

const form = reactive<{
  id?: string | number
  nickname: string
  gitHost: string
  authType: 'TOKEN' | 'PASSWORD'
  username: string
  token: string
  isDefault: number
}>({
  nickname: '',
  gitHost: 'github.com',
  authType: 'TOKEN',
  username: '',
  token: '',
  isDefault: 0,
})

const isEdit = computed(() => form.id != null)

/** GitHub 自 2021-08-13 起禁用密码克隆，选 PASSWORD + github.com 时警告 */
const githubPasswordWarn = computed(() =>
  form.authType === 'PASSWORD' && /^github\.com$/i.test(form.gitHost.trim()))

const COMMON_HOSTS = [
  'github.com',
  'gitee.com',
  'gitlab.com',
  'bitbucket.org',
  'codeup.aliyun.com',
  'e.coding.net',
]

async function load() {
  loading.value = true
  try {
    list.value = await codeAnalysisApi.listCredentials()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openAdd() {
  form.id = undefined
  form.nickname = ''
  form.gitHost = 'github.com'
  form.authType = 'TOKEN'
  form.username = ''
  form.token = ''
  form.isDefault = 0
  showToken.value = false
  dialogVisible.value = true
}

function openEdit(c: RepoAuthCredential) {
  form.id = c.id
  form.nickname = c.nickname
  form.gitHost = c.gitHost
  form.authType = (c.authType as 'TOKEN' | 'PASSWORD') || 'TOKEN'
  form.username = c.username
  form.token = ''  // 编辑时留空 = 不修改
  form.isDefault = c.isDefault || 0
  showToken.value = false
  dialogVisible.value = true
}

async function save() {
  if (!form.nickname || !form.gitHost || !form.username) {
    ElMessage.warning('昵称、Host、用户名必填')
    return
  }
  if (!isEdit.value && !form.token) {
    ElMessage.warning(form.authType === 'PASSWORD' ? '新建凭证必须填写密码' : '新建凭证必须填写 Token')
    return
  }
  // GitHub 密码登录在 2021-08-13 起被 GitHub 官方禁用
  if (githubPasswordWarn.value) {
    try {
      await ElMessageBox.confirm(
        'GitHub 自 2021-08-13 起已停止支持密码克隆，必须用 Personal Access Token。\n确定仍然使用密码保存吗？（大概率会认证失败）',
        '⚠️ 认证方式可能不兼容',
        { type: 'warning', confirmButtonText: '仍然保存', cancelButtonText: '改用 Token' }
      )
    } catch { return }
  }
  try {
    await codeAnalysisApi.saveCredential({
      id: form.id,
      nickname: form.nickname,
      gitHost: form.gitHost,
      authType: form.authType,
      username: form.username,
      token: form.token || undefined,  // 空串不传
      isDefault: form.isDefault,
    })
    ElMessage.success(isEdit.value ? '已更新' : '已添加')
    dialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function remove(c: RepoAuthCredential) {
  await ElMessageBox.confirm(`确定删除凭证「${c.nickname}」？`, '警告', {
    type: 'warning',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger',
  })
  await codeAnalysisApi.deleteCredential(c.id)
  ElMessage.success('已删除')
  load()
}

async function test(c: RepoAuthCredential) {
  testingId.value = c.id
  try {
    const updated = await codeAnalysisApi.testCredential(c.id)
    const idx = list.value.findIndex(x => x.id === c.id)
    if (idx >= 0) list.value[idx] = updated
    if (updated.testStatus === 'SUCCESS') {
      ElMessage.success('测试成功：' + (updated.testMessage || '连接正常'))
    } else {
      ElMessage.error('测试失败：' + (updated.testMessage || '未知错误'))
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '测试请求失败')
  } finally {
    testingId.value = null
  }
}

const statusMeta: Record<string, { label: string; color: string; icon: string }> = {
  SUCCESS: { label: '连接正常', color: '#10b981', icon: '✅' },
  FAILED:  { label: '测试失败', color: '#ef4444', icon: '❌' },
  UNKNOWN: { label: '未测试',   color: '#9ca3af', icon: '❓' },
}
</script>

<template>
  <div class="cred-page" v-loading="loading">
    <!-- 顶部说明 -->
    <div class="top-card">
      <div class="top-left">
        <div class="page-title">🔐 仓库凭证</div>
        <div class="page-sub">
          添加你的 GitHub/Gitee/GitLab 等 Token，分析时会按 Git URL 的 host 自动匹配，
          用于访问 <strong>私有仓库 / 公司内部仓库</strong>。
          Token 使用 AES-256-GCM 加密存储，前端只能看到掩码。
        </div>
      </div>
      <el-button type="primary" size="large" @click="openAdd">+ 添加凭证</el-button>
    </div>

    <!-- 使用说明 -->
    <el-alert v-if="list.length === 0" type="info" :closable="false" style="margin: 16px 0">
      <template #title>
        <span style="font-size: 14px; font-weight: 600">还没有任何凭证</span>
      </template>
      <div style="font-size: 13px; line-height: 1.8; margin-top: 6px">
        点击右上角「+ 添加凭证」开始配置。获取 Token 的方式：<br>
        🐙 GitHub：<a href="https://github.com/settings/tokens" target="_blank">Settings → Developer settings → Personal access tokens</a><br>
        🦊 GitLab：User Settings → Access Tokens<br>
        🔶 Gitee：设置 → 私人令牌<br>
        🏢 公司自建：联系你们 DevOps 管理员
      </div>
    </el-alert>

    <!-- 卡片网格 -->
    <div v-else class="cred-grid">
      <div v-for="c in list" :key="c.id" class="cred-card">
        <div class="card-header">
          <div class="card-title-row">
            <span class="card-title">{{ c.nickname }}</span>
            <span v-if="c.isDefault === 1" class="default-badge">⭐ 默认</span>
          </div>
          <div class="card-status" :style="{ color: statusMeta[c.testStatus || 'UNKNOWN'].color }">
            {{ statusMeta[c.testStatus || 'UNKNOWN'].icon }} {{ statusMeta[c.testStatus || 'UNKNOWN'].label }}
          </div>
        </div>

        <div class="card-body">
          <div class="card-row"><span class="k">🌐 Host</span><span class="v">{{ c.gitHost }}</span></div>
          <div class="card-row">
            <span class="k">🔐 方式</span>
            <span class="v">
              <span class="auth-tag" :class="c.authType === 'PASSWORD' ? 'auth-pwd' : 'auth-token'">
                {{ c.authType === 'PASSWORD' ? '账号密码' : 'Token' }}
              </span>
            </span>
          </div>
          <div class="card-row"><span class="k">👤 用户</span><span class="v">{{ c.username }}</span></div>
          <div class="card-row">
            <span class="k">{{ c.authType === 'PASSWORD' ? '🔒 密码' : '🔑 Token' }}</span>
            <span class="v mono">{{ c.tokenHint || '—' }}</span>
          </div>
          <div v-if="c.lastUsedAt" class="card-row"><span class="k">🕐 最近</span><span class="v">{{ new Date(c.lastUsedAt).toLocaleString('zh-CN') }}</span></div>
          <div v-if="c.testMessage" class="card-msg" :style="{ color: statusMeta[c.testStatus || 'UNKNOWN'].color }">
            {{ c.testMessage }}
          </div>
        </div>

        <div class="card-actions">
          <el-button size="small" :loading="testingId === c.id" @click="test(c)">🔄 测试</el-button>
          <el-button size="small" type="primary" plain @click="openEdit(c)">✏️ 编辑</el-button>
          <el-button size="small" type="danger" plain @click="remove(c)">🗑️ 删除</el-button>
        </div>
      </div>
    </div>

    <!-- 添加/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑凭证' : '添加新凭证'" width="560px">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="昵称" required>
          <el-input v-model="form.nickname" placeholder="例如：我的 GitHub 个人、公司 GitLab" />
        </el-form-item>

        <el-form-item label="Git Host" required>
          <el-select v-model="form.gitHost" filterable allow-create default-first-option
                     placeholder="选择或输入 host" style="width: 100%">
            <el-option v-for="h in COMMON_HOSTS" :key="h" :label="h" :value="h" />
          </el-select>
          <div class="form-hint">
            仅填 host（例：github.com / gitlab.company.com / 10.0.0.5:3000），不要带 https://
          </div>
        </el-form-item>

        <el-form-item label="认证方式" required>
          <el-radio-group v-model="form.authType">
            <el-radio-button value="TOKEN">🔑 Personal Access Token（推荐）</el-radio-button>
            <el-radio-button value="PASSWORD">🔐 账号密码</el-radio-button>
          </el-radio-group>
          <div class="form-hint">
            <template v-if="form.authType === 'TOKEN'">
              Token 认证更安全：可设权限范围、可单独撤销，即使泄漏影响面小。
            </template>
            <template v-else>
              账号密码方式更简单但安全性差：很多 Git 平台已弃用（GitHub 2021.8 / Bitbucket 2022.3）。
              仅推荐公司内部老版本 GitLab/Gitea 使用。
            </template>
          </div>
        </el-form-item>

        <el-alert v-if="githubPasswordWarn" type="error" :closable="false" style="margin-bottom: 14px">
          <template #title>GitHub 已禁用密码克隆</template>
          <div style="font-size: 12px; line-height: 1.6">
            GitHub 自 2021-08-13 起强制要求使用 PAT，密码认证会直接 401。
            推荐切换到 Token：Settings → Developer settings → Personal access tokens → Generate new token。
          </div>
        </el-alert>

        <el-form-item label="用户名" required>
          <el-input v-model="form.username"
                    :placeholder="form.authType === 'PASSWORD' ? 'Git 账号用户名' : 'Git 用户名（可与 Token 所属账号一致）'" />
        </el-form-item>

        <el-form-item
          :label="form.authType === 'PASSWORD'
            ? (isEdit ? '密码（留空 = 不修改）' : '账号密码')
            : (isEdit ? 'Token（留空 = 不修改）' : 'Personal Access Token')"
          :required="!isEdit">
          <el-input
            v-model="form.token"
            :type="showToken ? 'text' : 'password'"
            :placeholder="form.authType === 'PASSWORD'
              ? (isEdit ? '如需更换密码请填入新值' : '账号密码')
              : (isEdit ? '如需更换 Token 请填入新值' : 'ghp_xxxxxxxxxxxxxx / glpat-xxxx / xxx')"
            :show-password="!showToken"
          >
            <template #append>
              <el-button @click="showToken = !showToken">
                {{ showToken ? '🙈' : '👁️' }}
              </el-button>
            </template>
          </el-input>
          <div class="form-hint">
            存储时会用 AES-256-GCM 加密，前端列表只显示掩码。
          </div>
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="form.isDefault" :true-value="1" :false-value="0">
            设为此 Host 的默认凭证（同 host 下其他凭证会自动取消默认）
          </el-checkbox>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cred-page { padding: 20px; max-width: 1200px; margin: 0 auto; }

.top-card {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; border-radius: 12px; padding: 24px;
  display: flex; justify-content: space-between; align-items: center;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}
.top-left { flex: 1; margin-right: 20px; }
.page-title { font-size: 20px; font-weight: 700; margin-bottom: 6px; }
.page-sub { font-size: 13px; line-height: 1.65; opacity: 0.92; }

.cred-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 16px; margin-top: 18px;
}

.cred-card {
  background: #fff; border-radius: 12px; padding: 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.2s, transform 0.2s;
  display: flex; flex-direction: column;
}
.cred-card:hover { box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08); transform: translateY(-2px); }

.card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.card-title-row { display: flex; align-items: center; gap: 8px; }
.card-title { font-size: 15px; font-weight: 600; color: #111827; }
.default-badge {
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
  color: #fff; font-size: 11px; padding: 2px 8px; border-radius: 10px;
}
.card-status { font-size: 12px; font-weight: 600; }

.card-body { flex: 1; margin-bottom: 14px; }
.card-row { display: flex; font-size: 13px; margin-bottom: 5px; }
.card-row .k { color: #6b7280; width: 68px; flex-shrink: 0; }
.card-row .v { color: #111827; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-row .v.mono { font-family: 'SF Mono', Menlo, monospace; color: #6d28d9; }
.card-msg {
  margin-top: 8px; padding: 6px 10px; background: #f9fafb;
  border-radius: 6px; font-size: 12px; line-height: 1.5;
}

.card-actions { display: flex; gap: 6px; justify-content: flex-end; }

.form-hint { color: #9ca3af; font-size: 12px; margin-top: 4px; line-height: 1.5; }

.auth-tag { padding: 1px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.auth-tag.auth-token { background: #ede9fe; color: #6d28d9; }
.auth-tag.auth-pwd   { background: #fef3c7; color: #b45309; }
</style>
