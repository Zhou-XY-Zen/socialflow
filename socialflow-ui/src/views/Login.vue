<!--
  ============================================================
  Login.vue —— 用户登录页面
  ============================================================
  这是一个独立的全屏页面（不在 Layout 布局内），包含：
    - 一个居中的登录卡片
    - 邮箱和密码输入框
    - 登录按钮
    - 跳转注册页面的链接
  ============================================================
-->

<script setup lang="ts">
/**
 * 【本页面功能概述】
 * 1. 用户输入邮箱和密码
 * 2. 点击"登录"按钮或按回车键触发 onLogin 函数
 * 3. 调用后端登录接口，成功后保存 Token 和用户信息到 Store
 * 4. 跳转到工作台页面
 */

/**
 * reactive —— Vue 3 的另一种响应式 API（与 ref 类似但用于对象）。
 *
 * 【ref vs reactive 的区别】
 *   ref(值)       —— 适合包装基本类型（string、number、boolean）。访问时需要 .value。
 *   reactive(对象) —— 适合包装对象/数组。访问属性时不需要 .value，直接 obj.key 即可。
 *
 * 这里用 reactive 包装表单对象，因为表单有多个字段，
 * 在模板中绑定时可以直接写 v-model="form.email" 而不需要 form.value.email。
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
/** ElMessage —— Element Plus 的全局消息提示组件（顶部弹出的提示条） */
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/**
 * form —— 登录表单的响应式数据对象。
 * 通过 v-model 与 <el-input> 双向绑定，用户输入时自动更新。
 */
const form = reactive({ email: '', password: '' })

/** loading —— 登录按钮的加载状态（防止重复点击） */
const loading = ref(false)

/**
 * onLogin —— 登录处理函数。
 * 流程：
 *   1. 校验邮箱和密码是否为空
 *   2. 显示按钮 loading 状态
 *   3. 调用后端登录接口
 *   4. 成功：保存 Token + 用户信息到 Store，跳转到工作台
 *   5. finally：无论成功失败都关闭 loading
 */
async function onLogin() {
  if (!form.email || !form.password) {
    ElMessage.warning('请输入邮箱和密码')
    return
  }
  loading.value = true
  try {
    const data = await authApi.login(form)
    /* 登录成功：将 Token 和用户信息保存到 Pinia Store 和 localStorage */
    userStore.setSession(data.token, data.user)
    /* 跳转到登录前想访问的页面，没有则默认去工作台 */
    const redirect = (router.currentRoute.value.query.redirect as string) || '/workspace'
    router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <!-- 全屏居中容器 -->
  <div style="display:flex; justify-content:center; align-items:center; height:100vh; background:#f5f7fa">
    <!-- 登录卡片 -->
    <el-card style="width:360px">
      <h2 style="text-align:center; margin-top:0">SocialFlow 登录</h2>

      <!--
        el-form —— Element Plus 表单容器。
        @keyup.enter="onLogin" —— 监听键盘回车事件，按回车也能提交表单。
        v-model —— Vue 的双向数据绑定指令，将输入框的值与 form 对象的属性同步。
      -->
      <el-form @keyup.enter="onLogin">
        <el-form-item>
          <el-input v-model="form.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item>
          <!-- type="password" 让输入内容显示为圆点；show-password 显示"眼睛"图标切换明文 -->
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <!-- :loading="loading" —— 当 loading 为 true 时按钮显示旋转加载动画 -->
        <el-button type="primary" :loading="loading" style="width:100%" @click="onLogin">
          登录
        </el-button>
        <div style="text-align:center; margin-top:12px">
          <!-- router-link —— Vue Router 提供的导航组件，点击后跳转到指定路由 -->
          <router-link to="/register">没有账号？去注册</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
