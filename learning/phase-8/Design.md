# Phase 8 Design：Redis 缓存、限流与幂等加速

> 状态：实现完成，待阶段提交。本文记录 Java 端口、数据契约和已验证的故障语义。

## 1. 调用边界

```text
ChatController
  -> ChatService
       -> ChatRateLimiter
       -> ChatPersistenceService -> MySQL
       -> ChatIdempotencyCache    -> Redis
       -> ChatHistoryService
            -> RecentMessageCache -> Redis
            -> ChatPersistenceService -> MySQL fallback
       -> AgentClient             -> Python AI Service
```

- Controller 不直接访问 Redis、Mapper 或 Python Client。
- MySQL 保持聊天记录和幂等状态的唯一事实来源。
- Redis 端口只由 Java 业务服务调用；Python 目录和调用关系不变。

## 2. 端口与实现

| 端口 | 职责 | 故障语义 |
|---|---|---|
| `ChatRateLimiter` | 每用户固定窗口计数 | Redis 故障时 fail-closed，映射为 503 |
| `RecentMessageCache` | 最近 10 条消息的 Cache-Aside 读写与失效 | 缓存异常时回源 MySQL |
| `ChatIdempotencyCache` | 保存幂等摘要，缩小 MySQL 查询范围 | Redis 异常时回退 MySQL |
| `RedisKeyFactory` | 统一环境、版本、域和标识符 | 禁止原始消息或凭据进入 Key |
| `ChatHistoryService` | 先缓存、后 MySQL 的历史读取编排 | 所有权校验完成后才允许读缓存 |

## 3. 数据契约

- `CachedMessageSummary`：严格对应冻结 Value 数组中的 messageId、requestId、role、
  content 和 sequenceNo；Schema 版本由 Key 中的 `v1` 表达。
- `IdempotencySummary`：严格对应冻结 Value，携带 requestId、requestHash、状态和更新时间；
  Schema 版本由 Key 中的 `v1` 表达。
- `RateLimitDecision`：只表达是否允许及拒绝后的 `Retry-After` 秒数。
- `RateLimitExceededException`：将限流决定传递给 HTTP 异常映射层，不让 Controller 处理限流细节。

## 4. 实现约束与落地结果

1. `ChatRateLimiter` 使用单个 Lua 脚本原子执行计数和首次 TTL 设置。
2. `RecentMessageCache` 的坏 JSON、版本错误和归属不匹配均视为 miss。
3. `ChatHistoryService.evict` 只在 MySQL 事务提交后调用，失败不能回滚 MySQL。
4. `ChatIdempotencyCache` 命中后仍需从 MySQL 确认，MySQL 唯一约束保留最终裁决权。
5. 所有 Redis Adapter 均为 Spring 单例 Bean；未修改 Python、数据库迁移和公共聊天 DTO。
6. Testcontainers 固定为 1.21.4，以兼容当前 Docker Engine 29；该依赖仅用于测试。
