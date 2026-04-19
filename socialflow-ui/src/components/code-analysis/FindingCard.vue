<!--
  FindingCard.vue —— 单条审查发现卡片
  可折叠展开 + 状态标注（已修复/忽略）+ 代码片段显示
-->
<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { CodeFinding, FindingStatus } from '@/types/codeAnalysis'
import { codeAnalysisApi } from '@/api/codeAnalysis'

const props = defineProps<{ finding: CodeFinding }>()
const emit = defineEmits<{ updated: [CodeFinding] }>()

const expanded = ref(false)
const updating = ref(false)

const levelMeta = {
  HIGH:   { color: '#ef4444', bg: '#fef2f2', label: '高风险', icon: '🔴' },
  MEDIUM: { color: '#f59e0b', bg: '#fffbeb', label: '中风险', icon: '🟡' },
  LOW:    { color: '#3b82f6', bg: '#eff6ff', label: '低风险', icon: '🔵' },
} as const

const statusMeta = {
  UNRESOLVED: { color: '#9ca3af', label: '未处理' },
  RESOLVED:   { color: '#10b981', label: '已修复' },
  IGNORED:    { color: '#6b7280', label: '已忽略' },
} as const

async function setStatus(newStatus: FindingStatus, askNote = false) {
  let note = ''
  if (askNote) {
    try {
      const res = await ElMessageBox.prompt('请输入备注（可选）', '修改状态', {
        inputType: 'textarea',
        inputValue: props.finding.resolutionNote || '',
      })
      note = res.value || ''
    } catch { return }
  }
  updating.value = true
  try {
    await codeAnalysisApi.updateFindingStatus(props.finding.id, {
      status: newStatus,
      resolutionNote: note,
    })
    ElMessage.success('状态已更新')
    emit('updated', { ...props.finding, status: newStatus, resolutionNote: note })
  } finally {
    updating.value = false
  }
}
</script>

<template>
  <div class="finding-card" :style="{ borderLeftColor: levelMeta[finding.level].color }">
    <div class="f-header" @click="expanded = !expanded">
      <div class="f-left">
        <span class="level-tag" :style="{ background: levelMeta[finding.level].bg, color: levelMeta[finding.level].color }">
          {{ levelMeta[finding.level].icon }} {{ levelMeta[finding.level].label }}
        </span>
        <span v-if="finding.category" class="cat-tag">{{ finding.category }}</span>
        <span class="title">{{ finding.title }}</span>
      </div>
      <div class="f-right">
        <span class="status-tag" :style="{ color: statusMeta[finding.status].color }">
          {{ statusMeta[finding.status].label }}
        </span>
        <el-icon :class="['expand-icon', { rotated: expanded }]"><ArrowDown /></el-icon>
      </div>
    </div>

    <div v-if="finding.file" class="f-file">
      📁 {{ finding.file }}<span v-if="finding.lineRange"> : L{{ finding.lineRange }}</span>
      <span v-if="finding.ruleRef" class="rule-ref">📖 {{ finding.ruleRef }}</span>
    </div>

    <div v-if="expanded" class="f-detail">
      <div v-if="finding.description" class="f-section">
        <div class="f-section-title">问题描述</div>
        <div class="f-section-body">{{ finding.description }}</div>
      </div>

      <div v-if="finding.codeSnippet" class="f-section">
        <div class="f-section-title">相关代码</div>
        <pre class="f-code">{{ finding.codeSnippet }}</pre>
      </div>

      <div v-if="finding.suggestion" class="f-section">
        <div class="f-section-title">💡 修复建议</div>
        <div class="f-section-body">{{ finding.suggestion }}</div>
      </div>

      <div v-if="finding.resolutionNote" class="f-section">
        <div class="f-section-title">📝 用户备注</div>
        <div class="f-section-body note">{{ finding.resolutionNote }}</div>
      </div>

      <div class="f-actions">
        <el-button size="small" :loading="updating"
          :type="finding.status === 'RESOLVED' ? 'success' : 'default'"
          @click="setStatus('RESOLVED', true)">✅ 已修复</el-button>
        <el-button size="small" :loading="updating"
          :type="finding.status === 'IGNORED' ? 'info' : 'default'"
          @click="setStatus('IGNORED', true)">🙈 忽略</el-button>
        <el-button size="small" :loading="updating"
          :disabled="finding.status === 'UNRESOLVED'"
          @click="setStatus('UNRESOLVED', false)">↺ 恢复未处理</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.finding-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-left: 4px solid;
  border-radius: 8px;
  padding: 14px 18px;
  margin-bottom: 10px;
  transition: box-shadow 0.2s;
}
.finding-card:hover { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }

.f-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  gap: 12px;
}
.f-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.level-tag {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.cat-tag {
  padding: 2px 8px;
  background: #f3f4f6;
  color: #4b5563;
  border-radius: 4px;
  font-size: 12px;
}
.title {
  font-weight: 500;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.f-right { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.expand-icon { transition: transform 0.2s; color: #9ca3af; }
.expand-icon.rotated { transform: rotate(180deg); }
.f-file {
  margin-top: 6px;
  color: #6b7280;
  font-size: 12px;
  font-family: 'SF Mono', Menlo, monospace;
}
.rule-ref {
  margin-left: 12px;
  color: #7c3aed;
  font-weight: 500;
}
.f-detail { margin-top: 12px; padding-top: 12px; border-top: 1px dashed #e5e7eb; }
.f-section { margin-bottom: 10px; }
.f-section-title {
  font-size: 13px;
  color: #374151;
  font-weight: 600;
  margin-bottom: 4px;
}
.f-section-body { color: #4b5563; line-height: 1.6; font-size: 13.5px; }
.f-section-body.note { background: #fffbeb; padding: 8px 10px; border-radius: 4px; }
.f-code {
  background: #1e293b;
  color: #e2e8f0;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 12.5px;
  overflow-x: auto;
  margin: 0;
  font-family: 'SF Mono', Menlo, monospace;
}
.f-actions { margin-top: 12px; display: flex; gap: 8px; }
</style>
