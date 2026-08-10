<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { register } from '../api/auth'
import { formatApiError } from '../api/http'

const router = useRouter()
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const traceId = ref<string | null>(null)

async function submit(): Promise<void> {
  if (submitting.value) return
  errorMessage.value = ''
  traceId.value = null
  if (password.value !== confirmPassword.value) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }
  submitting.value = true
  try {
    const user = await register({ username: username.value, password: password.value })
    await router.replace({ name: 'login', query: { username: user.username } })
  } catch (error) {
    const problem = formatApiError(error)
    errorMessage.value = problem.message
    traceId.value = problem.traceId
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="brand-panel" aria-label="平台注册说明">
      <div class="brand-mark">IA</div>
      <p class="eyebrow">CREATE YOUR ACCOUNT</p>
      <h1>一个账号，连接<br />完整保险 AI 链路</h1>
      <p class="brand-copy">密码只以 BCrypt 摘要保存；客户端永远不会接触 Python 内部地址。</p>
      <ul class="feature-list">
        <li>JWT 无状态认证</li>
        <li>会话资源归属保护</li>
        <li>请求 TraceId 可追踪</li>
      </ul>
    </section>

    <section class="auth-card">
      <div>
        <p class="eyebrow accent">GET STARTED</p>
        <h2>创建账号</h2>
        <p class="muted">用户名支持字母、数字、点、下划线和连字符</p>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <label>
          <span>用户名</span>
          <input v-model="username" autocomplete="username" minlength="3" maxlength="64" required />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="72"
            required
          />
        </label>
        <label>
          <span>确认密码</span>
          <input
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="72"
            required
          />
        </label>

        <div v-if="errorMessage" class="error-banner" role="alert">
          <strong>{{ errorMessage }}</strong>
          <small v-if="traceId">TraceId：{{ traceId }}</small>
        </div>

        <button class="primary-button" type="submit" :disabled="submitting">
          {{ submitting ? '正在创建…' : '创建账号' }}
        </button>
      </form>

      <p class="auth-switch">已有账号？<RouterLink to="/login">返回登录</RouterLink></p>
    </section>
  </main>
</template>
