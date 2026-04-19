<!--
  RepoPicker.vue —— 级联下拉：选凭证 → 选该凭证下的仓库
  用于项目概览 / 提交审查 / 对比分析

  props: modelValue: { gitUrl, branch, credentialId? }
  emits: update:modelValue
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
const loading = ref(false)

async function loadCreds() {
  loading.value = true
  try {
    credentials.value = await codeAnalysisApi.listCredentials()
    if (props.modelValue.credentialId) {
      selectedCredId.value = props.modelValue.credentialId
      await loadProjects()
    } else if (props.modelValue.gitUrl) {
      // 反向查找：路由 query 带的 gitUrl → 在凭证库里找对应的项目
      await autoSelectByUrl(props.modelValue.gitUrl)
    }
  } catch { /* 忽略 */ }
  finally { loading.value = false }
}

async function loadProjects() {
  if (!selectedCredId.value) {
    projects.value = []
    return
  }
  try {
    projects.value = await codeAnalysisApi.listCredentialProjects(selectedCredId.value)
    if (props.modelValue.gitUrl) {
      const hit = projects.value.find(p => p.gitUrl === props.modelValue.gitUrl)
      if (hit) selectedProjectId.value = hit.id
    }
  } catch (e: any) {
    ElMessage.error('加载仓库失败：' + (e?.message || ''))
  }
}

/** 反向匹配：给定 gitUrl，去所有凭证里找该 URL 对应的 credential + project */
async function autoSelectByUrl(gitUrl: string) {
  for (const c of credentials.value) {
    try {
      const ps = await codeAnalysisApi.listCredentialProjects(c.id)
      const hit = ps.find(p => p.gitUrl === gitUrl)
      if (hit) {
        selectedCredId.value = c.id
        projects.value = ps
        selectedProjectId.value = hit.id
        return
      }
    } catch { /* 忽略 */ }
  }
}

// 选凭证时加载仓库
watch(selectedCredId, () => {
  selectedProjectId.value = ''
  projects.value = []
  loadProjects()
  emit('update:modelValue', { gitUrl: '', branch: 'main', credentialId: selectedCredId.value })
})

// 选仓库时同步到父组件
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

const selectedProject = computed(() =>
  projects.value.find(p => p.id === selectedProjectId.value))

function goManageCredentials() {
  router.push('/code-analysis/credentials')
}

function goAddProject() {
  if (!selectedCredId.value) {
    router.push('/code-analysis/credentials')
    return
  }
  router.push({ path: '/code-analysis/credentials', query: { openCred: selectedCredId.value } })
}

onMounted(loadCreds)
</script>

<template>
  <div class="repo-picker" v-loading="loading">
    <!-- 无凭证：引导加凭证 -->
    <div v-if="!loading && credentials.length === 0" class="empty-state">
      <div class="empty-icon">🔐</div>
      <div class="empty-title">还没有任何仓库凭证</div>
      <div class="empty-desc">
        分析任意 Git 仓库前，需要先在「仓库凭证」里配置 Token 和仓库地址
      </div>
      <el-button type="primary" size="large" @click="goManageCredentials">
        🔑 去「仓库凭证」添加
      </el-button>
    </div>

    <!-- 有凭证：级联下拉 -->
    <div v-else class="pick-area">
      <div class="row">
        <label class="label">① 选择凭证</label>
        <el-select v-model="selectedCredId" placeholder="选一个凭证..." style="width: 100%" clearable>
          <el-option
            v-for="c in credentials"
            :key="c.id"
            :label="`${c.nickname}（${c.gitHost}）`"
            :value="c.id">
            <span style="float: left">{{ c.nickname }}</span>
            <span style="float: right; color: #8a8a8a; font-size: 13px">
              {{ c.gitHost }} · {{ c.authType === 'PASSWORD' ? '密码' : 'Token' }}
            </span>
          </el-option>
        </el-select>
        <div class="row-action">
          <el-link type="primary" size="small" @click="goManageCredentials">+ 管理凭证</el-link>
        </div>
      </div>

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
            <div class="option-empty">
              该凭证下还没有仓库<br>
              <el-link type="primary" @click="goAddProject">+ 去添加仓库</el-link>
            </div>
          </template>
        </el-select>
        <div v-if="projects.length > 0" class="row-action">
          <el-link type="primary" size="small" @click="goAddProject">+ 添加新仓库到此凭证</el-link>
        </div>
        <div v-else-if="selectedCredId" class="row-action warn">
          ⚠️ 该凭证下还没有仓库，
          <el-link type="primary" size="small" @click="goAddProject">点此去添加</el-link>
        </div>
      </div>

      <div v-if="selectedProject" class="selected-summary">
        ✅ 将分析：<strong>{{ selectedProject.gitUrl }}</strong>
        <span class="br">🔀 {{ selectedProject.branch }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.repo-picker { display: flex; flex-direction: column; gap: 10px; min-height: 140px; }

.empty-state {
  text-align: center; padding: 30px 20px;
  background: #f9fafb; border: 1px dashed #d1d5db; border-radius: 10px;
  display: flex; flex-direction: column; align-items: center; gap: 10px;
}
.empty-icon { font-size: 48px; opacity: 0.5; }
.empty-title { font-size: 16px; font-weight: 600; color: #374151; }
.empty-desc { font-size: 13px; color: #6b7280; line-height: 1.6; max-width: 320px; }

.pick-area { display: flex; flex-direction: column; gap: 14px; }
.row { display: flex; flex-direction: column; gap: 6px; }
.label { font-size: 13px; font-weight: 500; color: #374151; }
.row-action { font-size: 12px; }
.row-action.warn { background: #fffbeb; border: 1px solid #fde68a; color: #b45309; padding: 6px 10px; border-radius: 6px; }

.selected-summary {
  background: #f0fdf4; border: 1px solid #bbf7d0; color: #059669;
  padding: 8px 12px; border-radius: 6px; font-size: 13px;
}
.selected-summary .br { margin-left: 8px; color: #6b7280; font-size: 12px; }

.option-empty { padding: 10px; text-align: center; color: #9ca3af; font-size: 13px; line-height: 1.8; }
</style>
