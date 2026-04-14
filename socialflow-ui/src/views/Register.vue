<!--
  Register.vue —— 用户注册页面
  与 Login.vue 同风格左右分栏设计
-->

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'

const router = useRouter()
const form = reactive({ email: '', password: '', nickname: '' })
const loading = ref(false)

async function onRegister() {
  loading.value = true
  try {
    await authApi.register(form)
    ElMessage.success('注册成功，请登录')
    router.replace('/login')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 左侧品牌区（与 Login 一致） -->
    <div class="login-brand">
      <div class="brand-content">
        <div class="brand-logo">S</div>
        <h1 class="brand-title">SocialFlow</h1>
        <p class="brand-slogan">AI 驱动的社交媒体内容创作平台</p>

        <div class="brand-features">
          <div class="feature-item">
            <div class="feature-icon">&#x270D;</div>
            <div>
              <div class="feature-name">AI 文案生成</div>
              <div class="feature-desc">多平台适配，一键生成高质量文案</div>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">&#x1F4DA;</div>
            <div>
              <div class="feature-name">知识库 RAG</div>
              <div class="feature-desc">基于专属知识库增强生成效果</div>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">&#x1F3A8;</div>
            <div>
              <div class="feature-name">AI 智能配图</div>
              <div class="feature-desc">文案自动匹配 AI 生成精美配图</div>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">&#x1F680;</div>
            <div>
              <div class="feature-name">多平台分发</div>
              <div class="feature-desc">小红书、抖音、朋友圈一键分发</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧注册表单区 -->
    <div class="login-form-area">
      <div class="form-container">
        <h2 class="form-title">创建账号</h2>
        <p class="form-subtitle">开始你的 AI 内容创作之旅</p>

        <el-form @keyup.enter="onRegister" class="login-form">
          <el-form-item>
            <el-input v-model="form.email" placeholder="邮箱地址" size="large" prefix-icon="Message" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.nickname" placeholder="昵称" size="large" prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码（至少 8 位）"
              size="large"
              show-password
              prefix-icon="Lock"
            />
          </el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            size="large"
            class="login-btn"
            @click="onRegister"
          >
            注册
          </el-button>
        </el-form>

        <div class="form-footer">
          <span style="color: #909399">已有账号？</span>
          <router-link to="/login" class="register-link">去登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page { display: flex; height: 100vh; overflow: hidden; }

.login-brand {
  flex: 5;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex; align-items: center; justify-content: center;
  padding: 60px; position: relative; overflow: hidden;
}
.login-brand::before {
  content: ''; position: absolute; width: 400px; height: 400px; border-radius: 50%;
  background: rgba(255,255,255,0.05); top: -100px; right: -100px;
}
.login-brand::after {
  content: ''; position: absolute; width: 300px; height: 300px; border-radius: 50%;
  background: rgba(255,255,255,0.05); bottom: -80px; left: -80px;
}
.brand-content { color: #fff; position: relative; z-index: 1; max-width: 440px; }
.brand-logo {
  width: 64px; height: 64px; background: rgba(255,255,255,0.2); border-radius: 16px;
  display: flex; align-items: center; justify-content: center;
  font-size: 32px; font-weight: 700; backdrop-filter: blur(10px); margin-bottom: 24px;
}
.brand-title { font-size: 40px; font-weight: 700; margin: 0 0 12px; }
.brand-slogan { font-size: 18px; opacity: 0.9; margin: 0 0 48px; }
.brand-features { display: flex; flex-direction: column; gap: 20px; }
.feature-item {
  display: flex; align-items: center; gap: 16px; padding: 14px 18px;
  background: rgba(255,255,255,0.1); border-radius: 12px; backdrop-filter: blur(10px);
}
.feature-icon { font-size: 24px; width: 40px; text-align: center; flex-shrink: 0; }
.feature-name { font-size: 15px; font-weight: 600; margin-bottom: 2px; }
.feature-desc { font-size: 13px; opacity: 0.75; }

.login-form-area {
  flex: 4; display: flex; align-items: center; justify-content: center;
  background: #fff; padding: 60px;
}
.form-container { width: 100%; max-width: 380px; }
.form-title { font-size: 28px; font-weight: 700; color: #1a1a2e; margin: 0 0 8px; }
.form-subtitle { font-size: 15px; color: #909399; margin: 0 0 36px; }
.login-form :deep(.el-input__wrapper) { border-radius: 8px; padding: 4px 12px; }
.login-btn {
  width: 100%; border-radius: 8px !important; font-size: 16px; height: 44px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  border: none !important; font-weight: 600; letter-spacing: 1px; margin-top: 8px;
}
.login-btn:hover { opacity: 0.9; }
.form-footer { text-align: center; margin-top: 24px; font-size: 14px; }
.register-link { color: #667eea; font-weight: 500; margin-left: 4px; }
.register-link:hover { color: #764ba2; }
</style>
