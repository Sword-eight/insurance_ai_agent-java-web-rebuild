<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { sendChatMessage } from '../api/chat'
import {
  createConversation,
  listConversations,
  listMessages,
} from '../api/conversation'
import { formatApiError } from '../api/http'
import { authState, clearSession } from '../auth/session'
import ConversationList from '../components/ConversationList.vue'
import MessageList from '../components/MessageList.vue'
import ChatInput from '../components/ChatInput.vue'
import router from '../router'
import type { ChatResponse, ConversationView, MessageView } from '../types/api'

const conversations = ref<ConversationView[]>([])
const messages = ref<MessageView[]>([])
const activeConversationId = ref<string | null>(null)
const draft = ref('')
const loadingConversations = ref(true)
const loadingMessages = ref(false)
const creatingConversation = ref(false)
const sending = ref(false)
const errorMessage = ref('')
const traceId = ref<string | null>(null)

const activeConversation = computed(() => conversations.value.find(
  (conversation) => conversation.conversationId === activeConversationId.value,
) ?? null)

const sendDisabled = computed(() => sending.value
  || !activeConversationId.value
  || draft.value.trim().length === 0)

function clearError(): void {
  errorMessage.value = ''
  traceId.value = null
}

function showError(error: unknown): void {
  const problem = formatApiError(error)
  errorMessage.value = problem.message
  traceId.value = problem.traceId
}

async function loadConversationList(): Promise<void> {
  loadingConversations.value = true
  clearError()
  try {
    const page = await listConversations()
    conversations.value = page.items
    if (!activeConversationId.value && page.items.length > 0) {
      const first = page.items[0]
      if (first) await selectConversation(first.conversationId)
    }
  } catch (error) {
    showError(error)
  } finally {
    loadingConversations.value = false
  }
}

async function selectConversation(conversationId: string): Promise<void> {
  if (loadingMessages.value || conversationId === activeConversationId.value) return
  activeConversationId.value = conversationId
  loadingMessages.value = true
  clearError()
  try {
    const page = await listMessages(conversationId)
    if (activeConversationId.value === conversationId) messages.value = page.items
  } catch (error) {
    showError(error)
  } finally {
    loadingMessages.value = false
  }
}

async function createNewConversation(): Promise<void> {
  if (creatingConversation.value) return
  creatingConversation.value = true
  clearError()
  try {
    const created = await createConversation({ title: '新会话' })
    conversations.value = [created, ...conversations.value]
    activeConversationId.value = created.conversationId
    messages.value = []
  } catch (error) {
    showError(error)
  } finally {
    creatingConversation.value = false
  }
}

function appendFallbackMessages(result: ChatResponse, originalMessage: string): void {
  const createdAt = new Date().toISOString()
  messages.value.push(
    {
      messageId: result.userMessageId,
      requestId: result.requestId,
      role: 'USER',
      content: originalMessage,
      createdAt,
    },
    {
      messageId: result.assistantMessageId,
      requestId: result.requestId,
      role: 'ASSISTANT',
      content: result.answer,
      createdAt,
    },
  )
}

async function refreshMessagesAfterRequest(conversationId: string): Promise<boolean> {
  try {
    const page = await listMessages(conversationId)
    if (activeConversationId.value === conversationId) messages.value = page.items
    return true
  } catch {
    return false
  }
}

async function send(): Promise<void> {
  const conversationId = activeConversationId.value
  const message = draft.value.trim()
  if (sending.value || !conversationId || !message) return

  sending.value = true
  clearError()
  const idempotencyKey = crypto.randomUUID()
  try {
    const result = await sendChatMessage({ conversationId, message }, idempotencyKey)
    draft.value = ''
    const refreshed = await refreshMessagesAfterRequest(conversationId)
    if (!refreshed && activeConversationId.value === conversationId) {
      appendFallbackMessages(result, message)
      errorMessage.value = '消息已发送，但历史刷新失败；重新选择会话可再次加载。'
    }
  } catch (error) {
    showError(error)
    await refreshMessagesAfterRequest(conversationId)
  } finally {
    sending.value = false
  }
}

async function logout(): Promise<void> {
  clearSession()
  await router.replace({ name: 'login' })
}

async function openDocuments(): Promise<void> {
  await router.push({ name: 'documents' })
}

onMounted(loadConversationList)
</script>

<template>
  <main class="chat-shell">
    <ConversationList
      :conversations="conversations"
      :active-id="activeConversationId"
      :loading="loadingConversations"
      :creating="creatingConversation"
      @select="selectConversation"
      @create="createNewConversation"
    />

    <section class="chat-workspace">
      <header class="chat-header">
        <div>
          <p class="eyebrow accent">INSURANCE AI</p>
          <h1>{{ activeConversation?.title || '智能保险顾问' }}</h1>
        </div>
        <div class="user-menu">
          <span class="status-dot"></span>
          <span>{{ authState.user.username }}</span>
          <button type="button" class="header-link" @click="openDocuments">知识库</button>
          <button type="button" @click="logout">退出</button>
        </div>
      </header>

      <div v-if="errorMessage" class="workspace-error" role="alert">
        <span>!</span>
        <div>
          <strong>{{ errorMessage }}</strong>
          <small v-if="traceId">TraceId：{{ traceId }}</small>
        </div>
        <button type="button" aria-label="关闭错误提示" @click="clearError">×</button>
      </div>

      <MessageList
        :messages="messages"
        :loading="loadingMessages"
        :conversation-selected="activeConversationId !== null"
      />

      <footer class="composer-area">
        <ChatInput
          v-model="draft"
          :disabled="sendDisabled"
          :sending="sending"
          @send="send"
        />
        <p>AI 回答仅供参考，请以正式保险条款为准 · 请求不会自动重发</p>
      </footer>
    </section>
  </main>
</template>
