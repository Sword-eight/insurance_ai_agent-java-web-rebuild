<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { login } from '../api/auth'
import { formatApiError } from '../api/http'
import { saveSession } from '../auth/session'

const router = useRouter()
const route = useRoute()
const username = ref(typeof route.query.username === 'string' ? route.query.username : '')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const traceId = ref<string | null>(null)

async function submit(): Promise<void> {
  if (submitting.value) return
  errorMessage.value = ''
  traceId.value = null
  submitting.value = true
  try {
    const result = await login({ username: username.value, password: password.value })
    saveSession(result)
    const redirect = typeof route.query.redirect === 'string'
      && route.query.redirect.startsWith('/')
      && !route.query.redirect.startsWith('//')
      ? route.query.redirect
      : '/chat'
    await router.replace(redirect)
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
    <section class="brand-panel" aria-label="平台简介">
      <div class="brand-mark">IA</div>
      <p class="eyebrow">INSURANCE AI PLATFORM</p>
      <h1>让保险问答<br />清晰、可信、可追踪</h1>
      <p class="brand-copy">通过 Java 业务层安全访问 AI，保留会话事实与完整 TraceId。</p>
      <div class="brand-flow"><span>Vue</span><i></i><span>Java</span><i></i><span>Python AI</span></div>
    </section>

    <section class="auth-card">
      <div>
        <p class="eyebrow accent">WELCOME BACK</p>
        <h2>登录平台</h2>
        <p class="muted">继续你的保险知识对话</p>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <label>
          <span>用户名</span>
          <input v-model="username" name="username" autocomplete="username" maxlength="64" required />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="password"
            name="password"
            type="password"
            autocomplete="current-password"
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
          {{ submitting ? '正在登录…' : '登录' }}
        </button>
      </form>

      <p class="auth-switch">还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
    </section>
  </main>
</template>
