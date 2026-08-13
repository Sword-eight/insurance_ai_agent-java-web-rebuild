<script setup lang="ts">
import type { ConversationView } from '../types/api'

defineProps<{
  conversations: ConversationView[]
  activeId: string | null
  loading: boolean
  creating: boolean
}>()

defineEmits<{
  select: [conversationId: string]
  create: []
}>()

function displayTitle(conversation: ConversationView): string {
  return conversation.title || '未命名会话'
}

function displayDate(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value))
}
</script>

<template>
  <aside class="conversation-panel">
    <div class="conversation-heading">
      <div>
        <p class="eyebrow accent">CONVERSATIONS</p>
        <h2>我的会话</h2>
      </div>
      <button
        class="icon-button"
        type="button"
        :disabled="creating"
        aria-label="新建会话"
        @click="$emit('create')"
      >
        {{ creating ? '…' : '+' }}
      </button>
    </div>

    <div class="conversation-scroll" aria-live="polite">
      <p v-if="loading" class="empty-state">正在加载会话…</p>
      <p v-else-if="conversations.length === 0" class="empty-state">
        暂无会话<br /><small>点击右上角 + 开始</small>
      </p>
      <button
        v-for="conversation in conversations"
        v-else
        :key="conversation.conversationId"
        class="conversation-item"
        :class="{ active: conversation.conversationId === activeId }"
        type="button"
        @click="$emit('select', conversation.conversationId)"
      >
        <span class="conversation-icon">✦</span>
        <span class="conversation-copy">
          <strong>{{ displayTitle(conversation) }}</strong>
          <small>{{ displayDate(conversation.createdAt) }}</small>
        </span>
      </button>
    </div>
  </aside>
</template>
