# Phase 7 Call Graph

## 1. 会话与消息查询

```text
POST /api/v1/conversations
→ ConversationController
→ ConversationService
→ CurrentUserProvider.requireUserId
→ UserMapper.findActiveInternalId
→ ConversationMapper.insert
→ ApiResponse<ConversationView>

GET /api/v1/conversations[/{id}]
→ ConversationService
→ ownership-scoped ConversationMapper queries
→ PageResponse / ConversationView

GET /api/v1/conversations/{id}/messages
→ MessageService
→ ownership check
→ ChatMessageMapper + ChatRequestMapper
→ PageResponse<MessageView>
```

## 2. 首次成功聊天

```text
ChatController
→ ChatService.chat
→ ChatPersistenceService.prepare                    [transaction A]
   → resolve current user
   → lock ACTIVE owned conversation
   → insert iap_chat_request(PROCESSING)
   → insert USER message
   → load up to 5 prior successful pairs
→ commit
→ AgentClient.chat                                  [no DB transaction]
→ validate requestId / answer / sources
→ ChatPersistenceService.completeSuccess            [transaction B]
   → lock conversation
   → insert ASSISTANT message
   → persist sources_json
   → request = SUCCEEDED
→ ChatResponse
```

## 3. 幂等重放与冲突

```text
same user + same Idempotency-Key
→ find existing request
→ compare SHA-256 request_hash
   ├─ different → 409 CHAT_IDEMPOTENCY_CONFLICT
   ├─ PROCESSING → 409 CHAT_REQUEST_IN_PROGRESS
   ├─ SUCCEEDED → rebuild original ChatResponse from MySQL
   └─ FAILED / UNKNOWN → replay recorded public error
```

以上分支均不再次调用 AgentClient。

## 4. 失败状态

```text
connect-before-send / explicit remote rejection
→ completeFailure(FAILED, stable ErrorCode)
→ no ASSISTANT

read timeout / uncertain delivery / remote still processing
→ completeFailure(UNKNOWN, stable ErrorCode)
→ no ASSISTANT
→ no automatic retry
```
