<!--
  Credentials.vue —— Git 仓库凭证管理（父-子树形）
  父：凭证（Token / 密码 + Host + Username）
  子：此凭证下的 Git 仓库（一对多）
-->
<script setup lang="ts">
import { onMounted, ref, computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { RepoAuthCredential, RepoCredentialProject } from '@/types/codeAnalysis'

const router = useRouter()
const list = ref<RepoAuthCredential[]>([])
const loading = ref(false)

/** 凭证 id → 其下的仓库列表（展开时加载） */
const projectsMap = ref<Record<string, RepoCredentialProject[]>>({})
const expanded = ref<Set<string>>(new Set())

const dialogVisible = ref(false)
const testingId = ref<string | null>(null)
const showToken = ref(false)

// ===== 凭证表单 =====
const form = reactive<{
  id?: string | number
  nickname: string
  authType: 'TOKEN' | 'PASSWORD'
  username: string
  token: string
  seedRepoUrl: string   // 新建凭证时可选：顺手录入第一个仓库
}>({
  nickname: '',
  authType: 'TOKEN',
  username: '',
  token: '',
  seedRepoUrl: '',
})
const isEdit = computed(() => form.id != null)
const autoHost = computed(() => extractHost(form.seedRepoUrl))
const githubPasswordWarn = computed(() =>
  form.authType === 'PASSWORD' && /^github\.com$/i.test(autoHost.value))

// ===== 项目（仓库）对话框 =====
const projectDialog = reactive<{
  visible: boolean
  credentialId: string | null
  id?: string | number
  nickname: string
  gitUrl: string
  branch: string
}>({
  visible: false,
  credentialId: null,
  nickname: '',
  gitUrl: '',
  branch: 'main',
})

function extractHost(url: string): string {
  if (!url) return ''
  const u = url.trim()
  if (u.startsWith('git@')) {
    const colon = u.indexOf(':', 4)
    return colon > 4 ? u.slice(4, colon).toLowerCase() : ''
  }
  const m = u.match(/^https?:\/\/([^\/]+)/i)
  return m ? m[1].toLowerCase() : ''
}

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

// ===== 凭证 CRUD =====

function openAdd() {
  form.id = undefined
  form.nickname = ''
  form.authType = 'TOKEN'
  form.username = ''
  form.token = ''
  form.seedRepoUrl = ''
  showToken.value = false
  dialogVisible.value = true
}

function openEdit(c: RepoAuthCredential) {
  form.id = c.id
  form.nickname = c.nickname
  form.authType = (c.authType as 'TOKEN' | 'PASSWORD') || 'TOKEN'
  form.username = c.username
  form.token = ''
  form.seedRepoUrl = c.gitHost ? `https://${c.gitHost}/` : ''
  showToken.value = false
  dialogVisible.value = true
}

async function save() {
  if (!form.nickname || !form.username) {
    ElMessage.warning('昵称、用户名必填')
    return
  }
  if (!isEdit.value && !form.seedRepoUrl) {
    ElMessage.warning('请填写第一个 Git 仓库 URL（Host 会自动识别）')
    return
  }
  if (!isEdit.value && !form.token) {
    ElMessage.warning(form.authType === 'PASSWORD' ? '新建凭证必须填写密码' : '新建凭证必须填写 Token')
    return
  }
  if (githubPasswordWarn.value) {
    try {
      await ElMessageBox.confirm(
        'GitHub 自 2021-08-13 起已停止支持密码克隆，必须用 Personal Access Token。\n确定仍然使用密码保存吗？',
        '⚠️ 认证方式可能不兼容',
        { type: 'warning', confirmButtonText: '仍然保存', cancelButtonText: '改用 Token' }
      )
    } catch { return }
  }
  try {
    const host = autoHost.value || extractHost(form.seedRepoUrl)
    const saved = await codeAnalysisApi.saveCredential({
      id: form.id,
      nickname: form.nickname,
      gitHost: host,
      authType: form.authType,
      username: form.username,
      token: form.token || undefined,
      defaultRepoUrl: form.seedRepoUrl || undefined,
      isDefault: 1,
    })
    if (!isEdit.value && form.seedRepoUrl) {
      try {
        await codeAnalysisApi.saveCredentialProject(saved.id, {
          gitUrl: form.seedRepoUrl,
          branch: 'main',
        })
      } catch { /* 忽略，可手动补 */ }
    }
    ElMessage.success(isEdit.value ? '已更新' : '已添加')
    dialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function remove(c: RepoAuthCredential) {
  await ElMessageBox.confirm(`确定删除凭证「${c.nickname}」？其下的仓库关联也会一起消失。`, '警告', {
    type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger',
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
    ElMessage[updated.testStatus === 'SUCCESS' ? 'success' : 'error'](
      `${updated.testStatus === 'SUCCESS' ? '✅' : '❌'} ${updated.testMessage}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '测试请求失败')
  } finally {
    testingId.value = null
  }
}

// ===== 项目（子仓库）操作 =====

async function toggleProjects(c: RepoAuthCredential) {
  if (expanded.value.has(c.id)) {
    expanded.value.delete(c.id)
    return
  }
  expanded.value.add(c.id)
  if (!projectsMap.value[c.id]) await loadProjects(c.id)
}

async function loadProjects(credentialId: string) {
  try {
    projectsMap.value[credentialId] = await codeAnalysisApi.listCredentialProjects(credentialId)
  } catch (e: any) {
    ElMessage.error('加载仓库列表失败：' + (e?.message || ''))
  }
}

function openAddProject(c: RepoAuthCredential) {
  projectDialog.visible = true
  projectDialog.credentialId = c.id
  projectDialog.id = undefined
  projectDialog.nickname = ''
  projectDialog.gitUrl = ''
  projectDialog.branch = 'main'
}

function openEditProject(credentialId: string, p: RepoCredentialProject) {
  projectDialog.visible = true
  projectDialog.credentialId = credentialId
  projectDialog.id = p.id
  projectDialog.nickname = p.nickname || ''
  projectDialog.gitUrl = p.gitUrl
  projectDialog.branch = p.branch || 'main'
}

async function saveProject() {
  if (!projectDialog.gitUrl) {
    ElMessage.warning('请填写 Git 仓库 URL')
    return
  }
  try {
    await codeAnalysisApi.saveCredentialProject(projectDialog.credentialId!, {
      id: projectDialog.id,
      nickname: projectDialog.nickname || undefined,
      gitUrl: projectDialog.gitUrl,
      branch: projectDialog.branch,
    })
    ElMessage.success(projectDialog.id ? '已更新' : '已添加')
    projectDialog.visible = false
    await loadProjects(projectDialog.credentialId!)
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function deleteProject(credentialId: string, p: RepoCredentialProject) {
  await ElMessageBox.confirm(`删除仓库「${p.nickname || p.gitUrl}」？`, '警告', { type: 'warning' })
  await codeAnalysisApi.deleteCredentialProject(p.id)
  ElMessage.success('已删除')
  await loadProjects(credentialId)
}

function analyzeProject(p: RepoCredentialProject) {
  router.push({
    path: '/code-analysis/project',
    query: { git: p.gitUrl, branch: p.branch || 'main' },
  })
}

const statusMeta: Record<string, { label: string; color: string; icon: string }> = {
  SUCCESS: { label: '连接正常', color: '#10b981', icon: '✅' },
  FAILED:  { label: '测试失败', color: '#ef4444', icon: '❌' },
  UNKNOWN: { label: '未测试',   color: '#9ca3af', icon: '❓' },
}
</script>

<template>
  <div class="cred-page" v-loading="loading">
    <div class="top-card">
      <div class="top-left">
        <div class="page-title">🔐 仓库凭证</div>
        <div class="page-sub">
          按"一个凭证 ↔ 多个仓库"组织：添加一次 Token，下面挂该凭证能访问的全部仓库。
          Token 用 AES-256-GCM 加密存储，前端只看得到掩码。
        </div>
      </div>
      <el-button type="primary" size="large" @click="openAdd">+ 添加凭证</el-button>
    </div>

    <el-alert v-if="list.length === 0" type="info" :closable="false" style="margin: 16px 0">
      <template #title>还没有任何凭证</template>
      <div style="font-size: 13px; line-height: 1.8; margin-top: 6px">
        🐙 GitHub：<a href="https://github.com/settings/tokens" target="_blank">Settings → Developer settings → PAT</a><br>
        🦊 GitLab：User Settings → Access Tokens<br>
        🔶 Gitee：设置 → 私人令牌
      </div>
    </el-alert>

    <div v-else class="cred-list">
      <div v-for="c in list" :key="c.id" class="cred-card">
        <div class="cred-header">
          <div class="header-left">
            <div class="title-row">
              <span class="cred-title">{{ c.nickname }}</span>
              <span class="auth-tag" :class="c.authType === 'PASSWORD' ? 'auth-pwd' : 'auth-token'">
                {{ c.authType === 'PASSWORD' ? '账号密码' : 'Token' }}
              </span>
              <span v-if="c.isDefault === 1" class="default-badge">⭐</span>
            </div>
            <div class="meta-row">
              🌐 {{ c.gitHost }} · 👤 {{ c.username }} · 🔑 <span class="mono">{{ c.tokenHint }}</span>
              <span class="status-inline" :style="{ color: statusMeta[c.testStatus || 'UNKNOWN'].color }">
                · {{ statusMeta[c.testStatus || 'UNKNOWN'].icon }} {{ statusMeta[c.testStatus || 'UNKNOWN'].label }}
              </span>
            </div>
          </div>
          <div class="header-actions">
            <el-button size="small" @click="toggleProjects(c)">
              📁 仓库 ({{ projectsMap[c.id] ? projectsMap[c.id].length : '…' }})
              {{ expanded.has(c.id) ? '▼' : '▶' }}
            </el-button>
            <el-button size="small" :loading="testingId === c.id" @click="test(c)">🔄 测试</el-button>
            <el-button size="small" type="primary" plain @click="openEdit(c)">✏️ 编辑</el-button>
            <el-button size="small" type="danger" plain @click="remove(c)">🗑️</el-button>
          </div>
        </div>

        <div v-if="expanded.has(c.id)" class="cred-projects">
          <div v-if="!projectsMap[c.id]" class="project-loading">加载中...</div>
          <template v-else>
            <div v-if="projectsMap[c.id].length === 0" class="project-empty">
              💡 该凭证下还没有仓库，点下方「+ 添加仓库」开始
            </div>
            <div v-for="p in projectsMap[c.id]" :key="p.id" class="project-row">
              <div class="p-main">
                <span class="p-name">{{ p.nickname || '（未命名）' }}</span>
                <span class="p-url mono">{{ p.gitUrl }}</span>
                <span class="p-branch">🔀 {{ p.branch }}</span>
              </div>
              <div class="p-actions">
                <el-button size="small" type="success" plain @click="analyzeProject(p)">📖 分析</el-button>
                <el-button size="small" link @click="openEditProject(c.id, p)">✏️</el-button>
                <el-button size="small" type="danger" link @click="deleteProject(c.id, p)">🗑️</el-button>
              </div>
            </div>
            <div class="add-project-row">
              <el-button size="small" type="primary" plain @click="openAddProject(c)">+ 添加仓库</el-button>
            </div>
          </template>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑凭证' : '添加新凭证'" width="560px">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="昵称" required>
          <el-input v-model="form.nickname" placeholder="例如：我的 GitHub 个人、公司 GitLab" />
        </el-form-item>

        <el-form-item v-if="!isEdit" label="第一个 Git 仓库 URL" required>
          <el-input v-model="form.seedRepoUrl"
                    placeholder="https://github.com/your-org/your-repo.git" clearable />
          <div class="auto-host" v-if="autoHost">
            ✓ 自动识别 Host：<span class="host-chip">{{ autoHost }}</span>
            <span class="host-tip">（此凭证会用于访问 {{ autoHost }} 下的所有仓库）</span>
          </div>
          <div v-else class="form-hint">
            保存后凭证下会自动建一条仓库记录，后续还能继续在该凭证下追加
          </div>
        </el-form-item>

        <el-form-item label="认证方式" required>
          <el-radio-group v-model="form.authType">
            <el-radio-button value="TOKEN">🔑 Personal Access Token（推荐）</el-radio-button>
            <el-radio-button value="PASSWORD">🔐 账号密码</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-alert v-if="githubPasswordWarn" type="error" :closable="false" style="margin-bottom: 14px">
          <template #title>GitHub 已禁用密码克隆</template>
          <div style="font-size: 12px">GitHub 2021-08-13 起强制 PAT，密码认证会直接 401。</div>
        </el-alert>

        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="Git 账号用户名" />
        </el-form-item>

        <el-form-item
          :label="form.authType === 'PASSWORD'
            ? (isEdit ? '密码（留空 = 不修改）' : '账号密码')
            : (isEdit ? 'Token（留空 = 不修改）' : 'Personal Access Token')"
          :required="!isEdit">
          <el-input v-model="form.token"
                    :type="showToken ? 'text' : 'password'"
                    :placeholder="form.authType === 'PASSWORD' ? '账号密码' : 'ghp_xxxx / glpat-xxxx'"
                    :show-password="!showToken">
            <template #append>
              <el-button @click="showToken = !showToken">{{ showToken ? '🙈' : '👁️' }}</el-button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="projectDialog.visible" :title="projectDialog.id ? '编辑仓库' : '添加仓库'" width="500px">
      <el-form label-position="top">
        <el-form-item label="Git 仓库 URL" required>
          <el-input v-model="projectDialog.gitUrl" placeholder="https://github.com/user/repo.git" clearable />
        </el-form-item>
        <el-form-item label="分支">
          <el-input v-model="projectDialog.branch" placeholder="main / develop / release/v2" />
        </el-form-item>
        <el-form-item label="昵称（可选）">
          <el-input v-model="projectDialog.nickname" placeholder="留空默认取仓库名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="projectDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveProject">保存</el-button>
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
}
.top-left { flex: 1; margin-right: 20px; }
.page-title { font-size: 20px; font-weight: 700; margin-bottom: 6px; }
.page-sub { font-size: 13px; line-height: 1.65; opacity: 0.92; }

.cred-list { display: flex; flex-direction: column; gap: 12px; margin-top: 18px; }

.cred-card {
  background: #fff; border-radius: 12px; padding: 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06); overflow: hidden;
}
.cred-card:hover { box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08); }

.cred-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; gap: 16px;
}
.header-left { flex: 1; min-width: 0; }
.title-row { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
.cred-title { font-size: 16px; font-weight: 600; color: #111827; }
.meta-row { color: #6b7280; font-size: 12px; }
.mono { font-family: 'SF Mono', Menlo, monospace; color: #6d28d9; }
.status-inline { margin-left: 4px; font-weight: 500; }

.auth-tag { padding: 1px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.auth-tag.auth-token { background: #ede9fe; color: #6d28d9; }
.auth-tag.auth-pwd   { background: #fef3c7; color: #b45309; }
.default-badge { background: linear-gradient(135deg, #fbbf24, #f59e0b); color: #fff; font-size: 11px; padding: 2px 8px; border-radius: 10px; }

.header-actions { display: flex; gap: 6px; flex-shrink: 0; }

.cred-projects { background: #f9fafb; border-top: 1px solid #e5e7eb; padding: 10px 20px; }
.project-loading, .project-empty { color: #9ca3af; font-size: 13px; padding: 10px 0; text-align: center; }

.project-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; background: #fff; border-radius: 6px; margin-bottom: 6px;
  border: 1px solid #e5e7eb;
}
.p-main { flex: 1; min-width: 0; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.p-name { font-weight: 600; color: #111827; font-size: 13px; }
.p-url { color: #0369a1 !important; font-size: 12px; overflow: hidden; text-overflow: ellipsis; max-width: 500px; }
.p-branch { color: #6b7280; font-size: 11px; }
.p-actions { display: flex; gap: 4px; flex-shrink: 0; }

.add-project-row { padding: 6px; text-align: center; }

.auto-host {
  margin-top: 8px; padding: 8px 12px; background: #f0fdf4;
  border: 1px solid #bbf7d0; border-radius: 6px; color: #059669; font-size: 13px;
}
.host-chip { font-family: monospace; background: #fff; padding: 2px 8px; border-radius: 4px; color: #6d28d9; font-weight: 600; }
.host-tip  { color: #6b7280; margin-left: 6px; font-size: 12px; }
.form-hint { color: #9ca3af; font-size: 12px; margin-top: 4px; line-height: 1.5; }
</style>
