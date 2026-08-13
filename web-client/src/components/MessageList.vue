<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { MessageView } from '../types/api'

const props = defineProps<{
  messages: MessageView[]
  loading: boolean
  conversationSelected: boolean
}>()

const listElement = ref<HTMLElement | null>(null)

watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    listElement.value?.scrollTo({ top: listElement.value.scrollHeight, behavior: 'smooth' })
  },
)

function displayTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <section ref="listElement" class="message-list" aria-live="polite">
    <div v-if="loading" class="message-placeholder">
      <span class="loading-orbit"></span>
      <p>正在读取历史消息…</p>
    </div>
    <div v-else-if="!conversationSelected" class="message-placeholder hero-empty">
      <div class="hero-symbol">IA</div>
      <h2>开始一段保险知识对话</h2>
      <p>新建或选择会话后，可以咨询保险条款、等待期与保费估算。</p>
    </div>
    <div v-else-if="messages.length === 0" class="message-placeholder hero-empty">
      <div class="hero-symbol">✦</div>
      <h2>这个会话还是空的</h2>
      <p>输入你的第一个问题，AI 回答会通过 Java 业务链保存。</p>
    </div>
    <article
      v-for="message in messages"
      v-else
      :key="message.messageId"
      class="message-row"
      :class="message.role === 'USER' ? 'from-user' : 'from-assistant'"
    >
      <div class="message-avatar">{{ message.role === 'USER' ? '你' : 'AI' }}</div>
      <div class="message-bubble">
        <div class="message-meta">
          <strong>{{ message.role === 'USER' ? '你' : 'Insurance AI' }}</strong>
          <time :datetime="message.createdAt">{{ displayTime(message.createdAt) }}</time>
        </div>
        <p>{{ message.content }}</p>
      </div>
    </article>
  </section>
</template>
