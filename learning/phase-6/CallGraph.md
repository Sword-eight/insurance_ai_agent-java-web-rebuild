# Phase 6 Call Graph

## 1. 正常同步聊天

```text
Web caller
→ POST /api/v1/chat/messages
   Headers: Idempotency-Key, X-Trace-Id
   Body: conversationId, message
→ TraceIdFilter
→ ChatController
→ ChatService
   → derive stable requestId/userMessageId/assistantMessageId
   → build AgentChatRequest(sessionId=conversationId, history=[])
→ HttpAgentClient
   → shared HTTP/1.1 RestClient
   → POST Python /internal/v1/agent/chat
      Header: X-Trace-Id
→ FastAPI Agent router
→ AgentFacade
→ RequestRegistry
→ GraphBuilder.invoke
→ InternalEnvelope<AgentChatData>
→ HttpAgentClient validates status/envelope/traceId
→ ChatService validates answer and maps real sources
→ ChatController returns ApiResponse<ChatResponse>
```

## 2. 同键同载荷

```text
same Idempotency-Key + same conversation/message
→ same stable requestId
→ Python RequestRegistry finds same fingerprint
→ cached AgentChatResult
→ Java returns same business data
```

公共 Envelope 的 `timestamp` 可以变化，幂等比较对象是业务 data，不是整段响应字节。

## 3. 同键不同载荷

```text
same Idempotency-Key + different message
→ same requestId + different fingerprint
→ Python AI_REQUEST_CONFLICT
→ AgentClientException(REJECTED)
→ ChatService maps CHAT_IDEMPOTENCY_CONFLICT
→ HTTP 409 public Envelope
```

## 4. 依赖故障

```text
connect failure → AgentClient UNAVAILABLE → 503 AI_SERVICE_UNAVAILABLE
read timeout    → AgentClient TIMEOUT     → 504 AI_SERVICE_TIMEOUT
bad JSON/trace/envelope/answer/source
                → PROTOCOL/validation     → 502 AI_EXECUTION_FAILED
```

没有自动重试，避免在 Python 仍执行时重复触发同一聊天。
