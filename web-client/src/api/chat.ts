import { http, unwrap } from './http'
import type { ApiResponse, ChatRequest, ChatResponse } from '../types/api'

export async function sendChatMessage(
  request: ChatRequest,
  idempotencyKey: string,
): Promise<ChatResponse> {
  const response = await http.post<ApiResponse<ChatResponse>>('/chat/messages', request, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return unwrap(response.data)
}
