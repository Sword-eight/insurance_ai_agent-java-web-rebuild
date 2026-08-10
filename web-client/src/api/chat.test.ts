import { describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { sendChatMessage } from './chat'

describe('chat API', () => {
  it('sends the UUID idempotency key only to the Java public endpoint', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({
      data: {
        code: 'OK',
        message: 'success',
        data: {
          conversationId: '22222222-2222-4222-8222-222222222222',
          requestId: '33333333-3333-4333-8333-333333333333',
          userMessageId: '44444444-4444-4444-8444-444444444444',
          assistantMessageId: '55555555-5555-4555-8555-555555555555',
          answer: '请以具体条款为准。',
          sources: [],
        },
        traceId: 'trace-chat',
        timestamp: '2026-08-10T00:00:00Z',
      },
    })
    const key = '66666666-6666-4666-8666-666666666666'

    await sendChatMessage({
      conversationId: '22222222-2222-4222-8222-222222222222',
      message: '等待期多久？',
    }, key)

    expect(post).toHaveBeenCalledWith('/chat/messages', expect.any(Object), {
      headers: { 'Idempotency-Key': key },
    })
  })
})
