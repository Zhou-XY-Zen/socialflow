<!--
  Login.vue —— 用户登录页面
  左右分栏设计：左侧品牌展示区（BrandPanel）+ 右侧登录表单
-->

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import BrandPanel from '@/components/BrandPanel.vue'

const router = useRouter()
const userStore = useUserStore()
const form = reactive({ email: '', password: '' })
const loading = ref(false)

async function onLogin() {
  if (!form.email || !form.password) {
    ElMessage.warning('请输入邮箱和密码')
    return
  }
  loading.value = true
  try {
    const data = await authApi.login(form)
    userStore.setSession(data.token, data.user)
    // 登录后默认跳欢迎页；若有 query.redirect 则优先使用（用户被拦截前想去的地方）
    const redirect = (router.currentRoute.value.query.redirect as string) || '/welcome'
    router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <BrandPanel />

    <div class="login-form-area">
      <div class="form-container">
        <h2 class="form-title">欢迎回来</h2>
        <p class="form-subtitle">登录你的 SocialFlow 账号</p>

        <el-form @keyup.enter="onLogin" class="login-form">
          <el-form-item>
            <el-input
              v-model="form.email"
              placeholder="邮箱地址"
              size="large"
              prefix-icon="Message"
            />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
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
            @click="onLogin"
          >
            登录
          </el-button>
        </el-form>

        <div class="form-footer">
          <span style="color: #909399">还没有账号？</span>
          <router-link to="/register" class="register-link">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.login-form-area {
  flex: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 60px;
}

.form-container {
  width: 100%;
  max-width: 380px;
}

.form-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 8px;
}

.form-subtitle {
  font-size: 15px;
  color: #909399;
  margin: 0 0 36px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  padding: 4px 12px;
}

.login-btn {
  width: 100%;
  border-radius: 8px !important;
  font-size: 16px;
  height: 44px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  border: none !important;
  font-weight: 600;
  letter-spacing: 1px;
  margin-top: 8px;
}

.login-btn:hover {
  opacity: 0.9;
}

.form-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
}

.register-link {
  color: #667eea;
  font-weight: 500;
  margin-left: 4px;
}

.register-link:hover {
  color: #764ba2;
}
</style>
