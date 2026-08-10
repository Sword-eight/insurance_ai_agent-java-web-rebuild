# Phase 9.5 Call Graph

## 登录与 401

```text
LoginView
  -> auth.login
  -> Axios http POST /api/v1/auth/login
  -> Vite proxy / production same-origin proxy
  -> Java AuthController -> AuthService -> MySQL -> JWT
  <- ApiResponse<LoginView>
  -> session.saveSession
  -> Router /chat

any protected request -> HTTP 401
  -> Axios response interceptor
  -> clearSession
  -> unauthorized handler
  -> Router /login
```

## 会话与历史

```text
ChatView.onMounted
  -> conversationApi.listConversations
  -> Java ConversationController -> Service -> MySQL
  <- PageResponse<ConversationView>
  -> select first conversation
  -> conversationApi.listMessages
  -> Java MessageService -> MySQL
  <- PageResponse<MessageView>
  -> MessageList
```

## 同步聊天

```text
ChatInput submit
  -> ChatView checks sending / conversation / trimmed message
  -> crypto.randomUUID() as Idempotency-Key
  -> sending=true, disable duplicate submit
  -> chatApi.sendChatMessage
  -> Axios POST /api/v1/chat/messages
  -> Java ChatController -> ChatService
     -> Redis rate limit / idempotency hint / recent cache
     -> MySQL request and message facts
     -> AgentClient -> Python FastAPI -> Graph/Tools/RAG
     -> MySQL final state
  <- ApiResponse<ChatResponse>
  -> reload messages from Java
  -> sending=false
```

## 重要异常

```text
400/403/409/429/502/503 -> stable Java Envelope -> visible message + traceId
504 AI_SERVICE_TIMEOUT  -> "结果暂时无法确认" -> no automatic resend
network failure         -> "无法连接 Java 服务" -> no Python fallback
history refresh failure -> show actual chat response temporarily -> ask user to reselect
```
