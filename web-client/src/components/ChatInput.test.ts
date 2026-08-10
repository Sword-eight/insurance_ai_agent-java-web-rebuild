import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ChatInput from './ChatInput.vue'

describe('ChatInput', () => {
  it('emits one send action for Enter and preserves Shift+Enter', async () => {
    const wrapper = mount(ChatInput, {
      props: { modelValue: '等待期多久？', disabled: false, sending: false },
    })
    const textarea = wrapper.get('textarea')

    await textarea.trigger('keydown', { key: 'Enter', shiftKey: true })
    expect(wrapper.emitted('send')).toBeUndefined()

    await textarea.trigger('keydown', { key: 'Enter', shiftKey: false })
    expect(wrapper.emitted('send')).toHaveLength(1)
  })

  it('disables both editing and submitting while sending', () => {
    const wrapper = mount(ChatInput, {
      props: { modelValue: '消息', disabled: true, sending: true },
    })

    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('等待回答')
  })
})
