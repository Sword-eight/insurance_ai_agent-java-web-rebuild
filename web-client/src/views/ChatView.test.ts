import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../api/http'
import { authState, clearSession } from '../auth/session'
import ChatView from './ChatView.vue'

const mocks = vi.hoisted(() => ({
  listConversations: vi.fn(),
  listMessages: vi.fn(),
  createConversation: vi.fn(),
  sendChatMessage: vi.fn(),
  replace: vi.fn(),
}))

vi.mock('../api/conversation', () => ({
  listConversations: mocks.listConversations,
  listMessages: mocks.listMessages,
  createConversation: mocks.createConversation,
}))

vi.mock('../api/chat', () => ({ sendChatMessage: mocks.sendChatMessage }))
vi.mock('../router', () => ({ default: { replace: mocks.replace } }))

const conversation = {
  conversationId: '22222222-2222-4222-8222-222222222222',
  title: '医疗险咨询',
  status: 'ACTIVE',
  createdAt: '2026-08-10T00:00:00Z',
}

describe('ChatView', () => {
  beforeEach(() => {
    clearSession()
    authState.user = {
      userId: '11111111-1111-4111-8111-111111111111',
      username: 'candidate',
      createdAt: '2026-08-10T00:00:00Z',
    }
    mocks.listConversations.mockResolvedValue({ items: [conversation], page: 1, size: 100, total: 1 })
    mocks.listMessages.mockResolvedValue({ items: [], page: 1, size: 100, total: 0 })
    mocks.createConversation.mockResolvedValue(conversation)
  })

  it('blocks mechanical double submit while the synchronous chat is pending', async () => {
    let resolveChat!: (value: unknown) => void
    mocks.sendChatMessage.mockImplementation(() => new Promise((resolve) => {
      resolveChat = resolve
    }))
    const wrapper = mount(ChatView)
    await flushPromises()
    await wrapper.get('textarea').setValue('等待期一般有多久？')

    void wrapper.get('.chat-composer').trigger('submit')
    void wrapper.get('.chat-composer').trigger('submit')
    await flushPromises()

    expect(mocks.sendChatMessage).toHaveBeenCalledOnce()
    expect(wrapper.get('.send-button').attributes('disabled')).toBeDefined()
    resolveChat({
      conversationId: conversation.conversationId,
      requestId: '33333333-3333-4333-8333-333333333333',
      userMessageId: '44444444-4444-4444-8444-444444444444',
      assistantMessageId: '55555555-5555-4555-8555-555555555555',
      answer: '请以具体条款为准。',
      sources: [],
    })
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('')
  })

  it('shows UNKNOWN guidance and traceId without an automatic retry', async () => {
    mocks.sendChatMessage.mockRejectedValue(new ApiClientError(
      'AI_SERVICE_TIMEOUT',
      'upstream timeout',
      504,
      'trace-unknown-95',
    ))
    const wrapper = mount(ChatView)
    await flushPromises()
    await wrapper.get('textarea').setValue('这次请求会自动重试吗？')
    await wrapper.get('.chat-composer').trigger('submit')
    await flushPromises()

    expect(mocks.sendChatMessage).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('结果暂时无法确认')
    expect(wrapper.text()).toContain('trace-unknown-95')
  })
})
