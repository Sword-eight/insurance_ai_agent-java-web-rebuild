# Phase 8 调用图

## 1. 正常新请求与缓存未命中

```text
ChatController
  -> ChatService.chat
     -> CurrentUserProvider.requireUserId
     -> RedisChatRateLimiter.acquire
        -> Redis Lua: INCR + first-write EXPIRE + TTL
     -> RedisChatIdempotencyCache.find        # miss/failure => Optional.empty
     -> ChatPersistenceService.prepare        # Transaction 1
        -> MySQL confirm idempotency
        -> lock and verify owned conversation
        -> insert PROCESSING request + USER message
        -> commit
     -> RedisChatIdempotencyCache.put(PROCESSING)
     -> RedisRecentMessageCache.evict         # after commit
     -> DefaultChatHistoryService.load
        -> RedisRecentMessageCache.find       # miss/bad JSON
        -> ChatPersistenceService.loadRecentMessages
        -> MySQL latest five complete pairs
        -> RedisRecentMessageCache.put        # max 10, TTL 30m
     -> AgentClient.chat                      # no DB transaction
     -> ChatPersistenceService.completeSuccess # Transaction 2
        -> insert ASSISTANT message
        -> update request SUCCEEDED
        -> commit
     -> RedisChatIdempotencyCache.put(SUCCEEDED)
     -> RedisRecentMessageCache.evict         # after commit
  <- ChatResponse
```

## 2. 幂等重放

```text
ChatService
  -> rate limiter
  -> Redis idempotency summary (requestId hint)
  -> ChatPersistenceService.prepare
     -> MySQL find by userId + requestId
     -> verify original idempotency key, request hash, conversation and status
     -> read original USER/ASSISTANT messages
  -> refresh Redis summary
  <- original ChatResponse
```

Redis 命中不直接产生重放结果；MySQL 查询和唯一约束仍拥有最终裁决权。

## 3. 拒绝与故障分支

```text
21st request in minute
  -> RateLimitExceededException
  -> GlobalExceptionHandler
  <- HTTP 429 + Retry-After

rate-limit Redis unavailable
  -> RATE_LIMIT_SERVICE_UNAVAILABLE
  <- HTTP 503 before MySQL/Python

recent/idempotency Redis unavailable
  -> log warning
  -> recent falls back to MySQL / idempotency falls back to MySQL

Agent failure
  -> ChatPersistenceService.completeFailure (FAILED or UNKNOWN, commit)
  -> refresh idempotency summary
  -> evict recent cache
  <- frozen public error
```
