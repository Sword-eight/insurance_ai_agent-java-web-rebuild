# Insurance AI Platform Redis 设计

> 文档版本：v1.0
> 状态：Phase 2 v1.0 已冻结；Phase 8 已实现；Phase 12 已使用 Redis 7.4 验收
> 架构依据：[ARCHITECTURE.md](./ARCHITECTURE.md)
> 关联文档：[API.md](./API.md) / [DATABASE.md](./DATABASE.md) / [DECISIONS.md](./DECISIONS.md)
> 当前状态：[PROJECT_STATUS.md](./PROJECT_STATUS.md)
> 范围：冻结 v1 Key、Value、TTL、读写方、失效和降级规则；实现不得偏离本契约

## 1. 边界

- Redis 只属于 Java Backend；Python 不读取或写入该 Redis。
- MySQL 是完整聊天记录和幂等状态的长期事实来源。
- Redis 只保存可过期、可重建的近期消息、聊天限流计数和幂等加速数据。
- Redis 丢失不能删除消息、改变文档索引业务状态或迫使 Python 恢复历史。
- v1 不使用 Redis 保存 JWT、密码、完整 PDF、Embedding 或 FAISS 数据。

当前仓库已实现近期消息 cache-aside、用户级聊天限流和聊天幂等加速。MySQL 仍是长期事实源；
Phase 12 在真实 Redis 7.4 中验证了幂等状态和 TTL，本文件继续作为实现必须遵守的冻结契约。

## 2. Key 命名

统一格式：

```text
iap:{env}:v1:{capability}:{purpose}:{dimensions...}
```

- `env` 只允许部署配置给出的短名称，如 `dev`、`test`、`prod`。
- 所有 Key 使用 ASCII 小写；业务 UUID 保持标准小写字符串。
- Key 不包含用户名、消息正文、文件名、JWT、API Key 或密码。
- v1 是 Key Schema 版本，不等同于 API 发布版本；改变 Value 不兼容结构时升级 Key 版本。
- 禁止业务代码使用 `KEYS *`；运维扫描只能使用受限前缀和 `SCAN`。

## 3. Key 契约总表

| 用例 | Key | Value | TTL | 写入/读取方 | 权威来源 |
|---|---|---|---:|---|---|
| 近期消息 | `iap:{env}:v1:chat:recent:{conversationId}` | JSON MessageSummary 数组 | 30 分钟 | `ChatService` | MySQL |
| 聊天限流 | `iap:{env}:v1:rate:chat:{userId}:{minuteEpoch}` | 十进制计数 | 120 秒 | Java 限流组件 | Redis 窗口状态 |
| 聊天幂等加速 | `iap:{env}:v1:idem:chat:{userId}:{keyHash}` | JSON IdempotencySummary | 24 小时 | `ChatService` | MySQL `iap_chat_request` |

`keyHash` 为客户端 `Idempotency-Key` UUID 文本的 SHA-256 小写十六进制，不把原 Key 直接拼入 Redis 日志或运维输出。

## 4. 近期消息缓存

### 4.1 Value

最多 10 条，按 `sequenceNo` 升序：

```json
[
  {
    "messageId": "e3c526e2-5dc5-4d8c-a79f-d39c5df49368",
    "requestId": "f86fb8c9-8293-49b5-9cd2-e870a25d6582",
    "role": "ASSISTANT",
    "content": "回答内容",
    "sequenceNo": 12
  }
]
```

- 只缓存 `USER`/`ASSISTANT` 的稳定业务消息，不缓存 system/tool/monitor 数据。
- 单条 content 上限沿用 API 4000 字符，总历史传 Python 前还必须满足 12000 字符预算。
- JSON Schema 不匹配、反序列化失败或消息归属不一致时视为 cache miss，记录脱敏 WARNING 并回源 MySQL。

### 4.2 Cache-Aside

读取：

1. Service 校验会话归属；
2. 查询 Redis；
3. 未命中或无效时从 MySQL 查询最新 10 条；
4. 回填 30 分钟 TTL；
5. 组装给 Python 的有限历史。

写入：

1. 先提交 MySQL 消息事务；
2. 提交成功后删除近期消息 Key；
3. 下一次读取从 MySQL 回填。

v1 选择“写后删除”而不是原地更新数组，减少并发追加导致的乱序和覆盖。删除失败不回滚 MySQL；记录指标，等待 TTL 或后续读取纠正。

## 5. 聊天限流

v1 使用用户级固定窗口：

- 维度：认证 `userId` + chat capability；
- 阈值：每用户每自然分钟最多 20 次聊天请求；
- 原子操作：首次 `INCR` 时设置 120 秒 TTL；具体 Lua/事务实现到 Phase 8 编码；
- 超限：HTTP 429、`RATE_LIMIT_EXCEEDED`，响应 Header `Retry-After` 为当前分钟剩余秒数（最少 1）；
- 限流发生在创建 chat request 和用户消息之前，不产生持久消息，也不调用 Python。

固定窗口可能在边界出现短时突发，这是 v1 为简单性接受的限制。若真实压测证明不足，再通过独立决策改为滑动窗口或令牌桶。

### Redis 故障

聊天是有外部 LLM 成本的接口。限流 Redis 不可用时 v1 采用 fail-closed：返回 HTTP 503 `RATE_LIMIT_SERVICE_UNAVAILABLE`，不调用 Python。此错误必须在日志/指标中区分为 Redis 保护组件故障，不能伪装成 Python 故障。

## 6. 幂等加速

### 6.1 Value

```json
{
  "requestId": "f86fb8c9-8293-49b5-9cd2-e870a25d6582",
  "requestHash": "64-char-lowercase-sha256",
  "status": "PROCESSING",
  "updatedAt": "2026-08-01T12:30:00Z"
}
```

允许状态与 MySQL `iap_chat_request.status` 一致：`RECEIVED`、`PROCESSING`、`SUCCEEDED`、`FAILED`、`UNKNOWN`。Redis 不保存完整回答；成功重复请求按 requestId 从 MySQL 查询原助手消息。

### 6.2 处理规则

1. 根据 userId + keyHash 查询 Redis；
2. 命中后比较 requestHash，并从 MySQL 确认状态后返回；
3. 未命中时查询 MySQL 唯一键 `(user_id, idempotency_key)`；
4. 真正首次请求由 MySQL 唯一约束创建；事务提交后写 Redis；
5. 状态改变后覆盖 Value 并刷新为 24 小时 TTL。

Redis 只是加速层，不能用“SETNX 成功”替代 MySQL 唯一约束。Redis 故障时直接走 MySQL 幂等路径，业务仍可用且不会重复执行。

24 小时 TTL 覆盖常见客户端重试窗口；MySQL chat request 随会话长期保留，因此 TTL 过期后相同 Key 仍会被数据库识别。

## 7. 一致性矩阵

| 事件 | MySQL | Redis 近期消息 | Redis 幂等 | 顺序 |
|---|---|---|---|---|
| 首次接收聊天 | 创建 request + 用户消息 | 删除 | 写 PROCESSING | MySQL commit 后处理 Redis |
| Python 成功 | 写助手消息 + SUCCEEDED | 删除 | 写 SUCCEEDED | MySQL commit 后处理 Redis |
| Python 明确失败 | request → FAILED | 无需新增消息；可删除 | 写 FAILED | MySQL commit 后处理 Redis |
| Python 读取超时 | request → UNKNOWN | 无需新增消息；可删除 | 写 UNKNOWN | MySQL commit 后处理 Redis |
| 会话逻辑删除 | conversation → DELETED | 删除 | 自然过期；MySQL 仍拒绝新请求 | MySQL commit 后删缓存 |

所有 Redis 写入失败都记录 traceId/requestId 和 Key 类型，但不记录完整 Key Value 或用户正文。

## 8. 故障与降级

| 故障 | 行为 | 功能结果 |
|---|---|---|
| 近期消息读取失败 | 回源 MySQL，不回填或稍后回填 | 可用，性能下降 |
| 近期消息删除失败 | 记录指标；读取时仍校验/由 TTL 修复 | MySQL 事实不回滚 |
| 幂等读取/写入失败 | 使用 MySQL 唯一约束和状态 | 可用，性能下降 |
| 限流读取/写入失败 | fail-closed，返回 503 | 聊天暂不可用，避免失控调用 |
| Value 版本/JSON 非法 | 忽略并删除该 Key，回源 | 不信任污染缓存 |

Redis Client 的连接超时、命令超时和连接池大小在 Phase 8 根据实际客户端设置；禁止无限等待。Client/连接池由 Spring 作为单例 Bean 管理，不得每请求创建。

## 9. 数据和安全

- 不缓存密码、JWT、API Key、PDF 内容或 Python Tool 参数。
- 近期消息含用户内容，Redis 必须位于受控网络并启用访问认证；生产日志不输出 Value。
- 环境前缀必须从受控配置取得，禁止客户端传入。
- Key 删除只使用已解析的确定 Key，不使用广泛通配删除。
- v1 不引入 Redis 分布式锁；数据库唯一约束承担最终防重。

## 10. 已完成的可测试验收

Phase 8 已验证以下场景，Phase 12 又在真实 Redis 7.4 平台链中核对幂等状态与 TTL：

- cache hit、cache miss、坏 JSON 回源；
- MySQL 提交后删缓存，删缓存失败不丢消息；
- 第 20 次限流通过、第 21 次拒绝、窗口过期恢复；
- 相同 Key/相同 hash 不重复调用 Python；
- 相同 Key/不同 hash 返回冲突；
- Redis 幂等不可用时 MySQL 仍防重；
- Redis 限流不可用时 fail-closed；
- TTL 确实设置，不存在无过期短期 Key。

## 11. 冻结结论

v1 只冻结三类真实 Redis 用例，没有引入 Session Store、分布式锁或万能 JSON。后续实现可调整连接池等环境参数，但改变 Key Schema、业务 TTL、限流阈值/算法、缓存一致性或故障降级语义必须先更新本文件并 Review。
