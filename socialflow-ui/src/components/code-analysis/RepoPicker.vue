<!--
  RepoPicker.vue —— 级联下拉：选凭证 → 选该凭证下的仓库
  用于项目概览 / 提交审查 / 对比分析 三个页面

  props:
    modelValue: { gitUrl, branch, credentialId? }
  emits:
    update:modelValue
-->
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { RepoAuthCredential, RepoCredentialProject } from '@/types/codeAnalysis'

interface PickValue {
  gitUrl: string
  branch?: string
  credentialId?: string
}

const props = defineProps<{ modelValue: PickValue; showBranch?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [PickValue] }>()

const router = useRouter()
const credentials = ref<RepoAuthCredential[]>([])
const projects = ref<RepoCredentialProject[]>([])
const selectedCredId = ref<string>('')
const selectedProjectId = ref<string>('')
const manualUrl = ref<string>('')
const manualBranch = ref<string>('main')
const mode = ref<'picker' | 'manual'>('picker')

async function loadCreds() {
  try {
    credentials.value = await codeAnalysisApi.listCredentials()
    // 初始化：如果传入了 credentialId 或 gitUrl，尝试预选
    if (props.modelValue.credentialId) {
      selectedCredId.value = props.modelValue.credentialId
      await loadProjects()
    } else if (props.modelValue.gitUrl) {
      // 没传 credentialId 但传了 URL，切到 manual 模式
      mode.value = 'manual'
      manualUrl.value = props.modelValue.gitUrl
      manualBranch.value = props.modelValue.branch || 'main'
    }
  } catch { /* 忽略 */ }
}

async function loadProjects() {
  if (!selectedCredId.value) {
    projects.value = []
    return
  }
  try {
    projects.value = await codeAnalysisApi.listCredentialProjects(selectedCredId.value)
    // 如果 modelValue.gitUrl 能在新列表里匹配到，自动选中
    if (props.modelValue.gitUrl) {
      const hit = projects.value.find(p => p.gitUrl === props.modelValue.gitUrl)
      if (hit) selectedProjectId.value = hit.id
    }
  } catch (e: any) {
    ElMessage.error('加载仓库失败：' + (e?.message || ''))
  }
}

// 选凭证时自动加载仓库
watch(selectedCredId, () => {
  selectedProjectId.value = ''
  projects.value = []
  loadProjects()
  emit('update:modelValue', { gitUrl: '', branch: 'main', credentialId: selectedCredId.value })
})

// 选仓库时把 gitUrl/branch 同步回父组件
watch(selectedProjectId, () => {
  const p = projects.value.find(x => x.id === selectedProjectId.value)
  if (p) {
    emit('update:modelValue', {
      gitUrl: p.gitUrl,
      branch: p.branch || 'main',
      credentialId: selectedCredId.value,
    })
  }
})

// 手动输入模式同步
watch([manualUrl, manualBranch], () => {
  if (mode.value === 'manual') {
    emit('update:modelValue', {
      gitUrl: manualUrl.value,
      branch: manualBranch.value,
    })
  }
})

// 切换模式时清空另一边的选择
watch(mode, (m) => {
  if (m === 'picker') {
    emit('update:modelValue', { gitUrl: '', branch: 'main', credentialId: selectedCredId.value })
  } else {
    emit('update:modelValue', { gitUrl: manualUrl.value, branch: manualBranch.value })
  }
})

const selectedProject = computed(() =>
  projects.value.find(p => p.id === selectedProjectId.value))

function goAddCredential() {
  router.push('/code-analysis/credentials')
}

function goAddProject() {
  if (!selectedCredId.value) {
    ElMessage.warning('请先选一个凭证')
    return
  }
  // 跳到凭证页并自动展开那个凭证（带参数）
  router.push({ path: '/code-analysis/credentials', query: { openCred: selectedCredId.value } })
}

onMounted(loadCreds)
</script>

<template>
  <div class="repo-picker">
    <!-- 模式切换 -->
    <div class="mode-tabs">
      <div class="tab" :class="{ active: mode === 'picker' }" @click="mode = 'picker'">
        📚 从凭证库选择
      </div>
      <div class="tab" :class="{ active: mode === 'manual' }" @click="mode = 'manual'">
        ✏️ 手动输入 URL
      </div>
    </div>

    <!-- Picker 模式：级联下拉 -->
    <div v-if="mode === 'picker'" class="pick-area">
      <!-- 选凭证 -->
      <div class="row">
        <label class="label">① 选择凭证</label>
        <el-select v-model="selectedCredId" placeholder="选一个凭证..." style="width: 100%" clearable>
          <el-option
            v-for="c in credentials"
            :key="c.id"
            :label="`${c.nickname}（${c.gitHost}）`"
            :value="c.id">
            <span style="float: left">{{ c.nickname }}</span>
            <span style="float: right; color: #8a8a8a; font-size: 13px">{{ c.gitHost }} · {{ c.authType === 'PASSWORD' ? '密码' : 'Token' }}</span>
          </el-option>
          <template #empty>
            <div style="padding: 10px; text-align: center; color: #9ca3af">
              还没有凭证，<el-link type="primary" @click="goAddCredential">去添加</el-link>
            </div>
          </template>
        </el-select>
        <el-link v-if="credentials.length === 0" type="primary" @click="goAddCredential" style="margin-top: 6px">
          + 去添加凭证
        </el-link>
      </div>

      <!-- 选仓库 -->
      <div v-if="selectedCredId" class="row">
        <label class="label">② 选择要分析的仓库</label>
        <el-select v-model="selectedProjectId" placeholder="选一个仓库..." style="width: 100%" clearable>
          <el-option
            v-for="p in projects"
            :key="p.id"
            :label="p.nickname || p.gitUrl"
            :value="p.id">
            <div style="display: flex; justify-content: space-between">
              <span>{{ p.nickname || '（未命名）' }}</span>
              <span style="color: #9ca3af; font-size: 12px">🔀 {{ p.branch }}</span>
            </div>
            <div style="color: #9ca3af; font-size: 11px; font-family: monospace">{{ p.gitUrl }}</div>
          </el-option>
          <template #empty>
            <div style="padding: 10px; text-align: center; color: #9ca3af">
              该凭证下还没有仓库<br>
              <el-link type="primary" @click="goAddProject">+ 去凭证页添加仓库</el-link>
            </div>
          </template>
        </el-select>
        <el-link v-if="projects.length === 0" type="primary" @click="goAddProject" style="margin-top: 6px">
          + 添加新仓库到此凭证
        </el-link>
      </div>

      <!-- 已选摘要 -->
      <div v-if="selectedProject" class="selected-summary">
        ✅ 将分析：<strong>{{ selectedProject.gitUrl }}</strong>
        <span class="br">🔀 {{ selectedProject.branch }}</span>
      </div>
    </div>

    <!-- Manual 模式：手动输入 -->
    <div v-else class="manual-area">
      <div class="row">
        <label class="label">Git 仓库 URL</label>
        <el-input v-model="manualUrl" placeholder="https://github.com/user/repo.git" clearable />
      </div>
      <div v-if="showBranch !== false" class="row">
        <label class="label">分支</label>
        <el-input v-model="manualBranch" placeholder="main" />
      </div>
      <div class="manual-hint">
        💡 公开仓库可以直接分析；私有仓库需要先在「仓库凭证」里加对应 Host 的 Token。
      </div>
    </div>
  </div>
</template>

<style scoped>
.repo-picker { display: flex; flex-direction: column; gap: 10px; }

.mode-tabs {
  display: flex; gap: 6px; background: #f3f4f6;
  padding: 3px; border-radius: 8px;
}
.tab {
  flex: 1; padding: 7px 10px; border-radius: 6px; text-align: center;
  font-size: 13px; color: #6b7280; cursor: pointer; transition: all 0.2s;
}
.tab.active { background: #fff; color: #6d28d9; font-weight: 600; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }

.pick-area, .manual-area { display: flex; flex-direction: column; gap: 12px; }
.row { display: flex; flex-direction: column; gap: 6px; }
.label { font-size: 13px; font-weight: 500; color: #374151; }

.selected-summary {
  background: #f0fdf4; border: 1px solid #bbf7d0; color: #059669;
  padding: 8px 12px; border-radius: 6px; font-size: 13px;
}
.selected-summary .br { margin-left: 8px; color: #6b7280; font-size: 12px; }

.manual-hint { color: #9ca3af; font-size: 12px; line-height: 1.55; }
</style>
