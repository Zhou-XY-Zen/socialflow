<!--
  ============================================================
  Register.vue —— 用户注册页面
  ============================================================
  与 Login.vue 结构类似的全屏独立页面，包含：
    - 邮箱、昵称、密码输入框
    - 注册按钮
    - 跳转登录页面的链接
  注册成功后自动跳转到登录页。
  ============================================================
-->

<script setup lang="ts">
/**
 * 【本页面功能概述】
 * 1. 用户填写邮箱、昵称、密码
 * 2. 点击"注册"按钮调用后端注册接口
 * 3. 注册成功后提示并跳转到登录页
 */

import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'

const router = useRouter()

/** 注册表单响应式对象（包含邮箱、密码、昵称三个字段） */
const form = reactive({ email: '', password: '', nickname: '' })
/** 注册按钮的加载状态 */
const loading = ref(false)

/**
 * onRegister —— 注册处理函数。
 * 流程：
 *   1. 显示 loading
 *   2. 调用后端注册接口
 *   3. 成功：弹出成功提示，跳转到登录页
 *   4. finally：关闭 loading
 */
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
  <!-- 全屏居中容器 -->
  <div style="display:flex; justify-content:center; align-items:center; height:100vh">
    <el-card style="width:360px">
      <h2 style="text-align:center; margin-top:0">SocialFlow 注册</h2>
      <el-form>
        <!-- 邮箱输入 —— v-model 双向绑定到 form.email -->
        <el-form-item><el-input v-model="form.email" placeholder="邮箱" /></el-form-item>
        <!-- 昵称输入 -->
        <el-form-item><el-input v-model="form.nickname" placeholder="昵称" /></el-form-item>
        <!-- 密码输入 -->
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码（至少 8 位）" show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width:100%" @click="onRegister">
          注册
        </el-button>
        <div style="text-align:center; margin-top:12px">
          <router-link to="/login">已有账号？去登录</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
