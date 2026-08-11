export interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
  traceId: string
  timestamp: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface UserView {
  userId: string
  username: string
  createdAt: string
}

export interface LoginView {
  accessToken: string
  tokenType: 'Bearer'
  expiresInSeconds: number
  user: UserView
}

export interface ConversationView {
  conversationId: string
  title: string | null
  status: string
  createdAt: string
}

export interface MessageView {
  messageId: string
  requestId: string
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface ChatSource {
  documentName: string
  page: number | null
  snippet: string
  score: number | null
}

export interface ChatResponse {
  conversationId: string
  requestId: string
  userMessageId: string
  assistantMessageId: string
  answer: string
  sources: ChatSource[]
}

export type DocumentIndexStatus =
  | 'UPLOADED'
  | 'INDEXING'
  | 'INDEXED'
  | 'FAILED'
  | 'UNKNOWN'
  | 'DELETED'

export interface DocumentData {
  documentId: string
  originalFilename: string
  sizeBytes: number
  indexStatus: DocumentIndexStatus
  createdAt: string
}

export interface RegisterRequest {
  username: string
  password: string
}

export interface LoginRequest extends RegisterRequest {}

export interface CreateConversationRequest {
  title: string | null
}

export interface ChatRequest {
  conversationId: string
  message: string
}
