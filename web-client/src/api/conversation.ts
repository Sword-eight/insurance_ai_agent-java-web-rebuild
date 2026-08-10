import { http, unwrap } from './http'
import type {
  ApiResponse,
  ConversationView,
  CreateConversationRequest,
  MessageView,
  PageResponse,
} from '../types/api'

export async function listConversations(): Promise<PageResponse<ConversationView>> {
  const response = await http.get<ApiResponse<PageResponse<ConversationView>>>('/conversations', {
    params: { page: 1, size: 100 },
  })
  return unwrap(response.data)
}

export async function createConversation(
  request: CreateConversationRequest,
): Promise<ConversationView> {
  const response = await http.post<ApiResponse<ConversationView>>('/conversations', request)
  return unwrap(response.data)
}

export async function listMessages(
  conversationId: string,
): Promise<PageResponse<MessageView>> {
  const response = await http.get<ApiResponse<PageResponse<MessageView>>>(
    `/conversations/${conversationId}/messages`,
    { params: { page: 1, size: 100 } },
  )
  return unwrap(response.data)
}
