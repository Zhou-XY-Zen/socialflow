<!--
  Settings.vue —— 代码分析偏好设置（存本地 localStorage）
-->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const KEY = 'socialflow.code-analysis.settings'

const settings = ref({
  provider: 'DEEPSEEK',
  temperature: 0.3,
  cloneDepth: 1,
  maxDiffKB: 40,
  excludeDirs: 'node_modules,target,dist,.git,.idea,.vscode,build,out',
  notifyOnComplete: true,
})

onMounted(() => {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) Object.assign(settings.value, JSON.parse(raw))
  } catch { /* ignore */ }
})

function save() {
  localStorage.setItem(KEY, JSON.stringify(settings.value))
  ElMessage.success('设置已保存（本地）')
}

function reset() {
  localStorage.removeItem(KEY)
  location.reload()
}
</script>

<template>
  <div class="settings-page">
    <div class="card">
      <div class="card-title">⚙️ 代码分析偏好设置</div>
      <div class="hint">这些设置仅保存在当前浏览器本地，影响分析默认参数。</div>

      <el-form label-width="140px" label-position="left">
        <el-divider content-position="left">LLM 参数</el-divider>
        <el-form-item label="默认 Provider">
          <el-select v-model="settings.provider" style="width: 240px">
            <el-option label="DeepSeek" value="DEEPSEEK" />
            <el-option label="Qwen（通义千问）" value="QWEN" />
            <el-option label="GLM（智谱）" value="GLM" />
          </el-select>
        </el-form-item>
        <el-form-item label="审查温度">
          <el-slider v-model="settings.temperature" :min="0" :max="1" :step="0.1"
                     show-input style="width: 400px" />
          <div class="sub-hint">较低（0.1-0.3）更严谨，较高更有创造性</div>
        </el-form-item>

        <el-divider content-position="left">Git 参数</el-divider>
        <el-form-item label="默认克隆深度">
          <el-input-number v-model="settings.cloneDepth" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="Diff 截断阈值(KB)">
          <el-input-number v-model="settings.maxDiffKB" :min="10" :max="200" :step="10" />
          <div class="sub-hint">超过此大小 diff 会被截断，避免 token 溢出</div>
        </el-form-item>
        <el-form-item label="默认排除目录">
          <el-input v-model="settings.excludeDirs" type="textarea" :rows="2"
                    placeholder="逗号分隔，例如 node_modules,target,dist" />
        </el-form-item>

        <el-divider content-position="left">通知</el-divider>
        <el-form-item label="分析完成通知">
          <el-switch v-model="settings.notifyOnComplete" />
          <div class="sub-hint">完成后在浏览器内弹一个 toast</div>
        </el-form-item>

        <el-divider />
        <el-form-item>
          <el-button type="primary" @click="save">💾 保存设置</el-button>
          <el-button @click="reset">↺ 恢复默认</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card">
      <div class="card-title">📊 关于代码分析模块</div>
      <ul class="about">
        <li>分析基于 <strong>阿里巴巴 Java 开发手册（嵩山版）</strong> 的 100+ 条规约</li>
        <li>支持语言：Java / Kotlin / Vue / TypeScript / Python / SQL / Go / Rust</li>
        <li>Git 操作：Eclipse JGit（纯 Java，无需系统 git）</li>
        <li>LLM 分析：DeepSeek / Qwen / GLM 三选一</li>
        <li>每次分析独立临时克隆，完成后自动清理</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.settings-page { padding: 20px; max-width: 900px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
.card { background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); }
.card-title { font-size: 16px; font-weight: 600; color: #111827; margin-bottom: 6px; }
.hint { color: #6b7280; font-size: 13px; margin-bottom: 18px; }
.sub-hint { color: #9ca3af; font-size: 12px; margin-top: 4px; }
.about { color: #4b5563; line-height: 2; padding-left: 20px; }
</style>
