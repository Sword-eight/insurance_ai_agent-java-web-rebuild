<script setup lang="ts">
const props = defineProps<{
  modelValue: string
  disabled: boolean
  sending: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
}>()

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (!props.disabled) emit('send')
  }
}
</script>

<template>
  <form class="chat-composer" @submit.prevent="$emit('send')">
    <textarea
      :value="modelValue"
      rows="2"
      maxlength="4000"
      placeholder="输入保险相关问题，Enter 发送，Shift + Enter 换行"
      :disabled="sending"
      aria-label="聊天消息"
      @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
      @keydown="handleKeydown"
    ></textarea>
    <button class="send-button" type="submit" :disabled="disabled">
      <span>{{ sending ? '等待回答' : '发送' }}</span>
      <span aria-hidden="true">↗</span>
    </button>
  </form>
</template>
