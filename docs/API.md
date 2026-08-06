# Insurance AI Platform API 契约

> 文档版本：v1.0
> 状态：Phase 2 v1.0 已冻结；自 2026-08-01 起生效
> 架构依据：[ARCHITECTURE.md](./ARCHITECTURE.md)
> 关联文档：[DATABASE.md](./DATABASE.md) / [REDIS.md](./REDIS.md) / [DECISIONS.md](./DECISIONS.md)
> 范围：冻结 v1 的 HTTP 路径、字段、错误码、上下文、幂等和容错默认值；不表示接口已经实现
> 客户端说明：Phase 9.5/10.5 规划的 Vue 只消费 Java 公共 API；本次规划调整不修改 v1 契约

## 1. 当前事实和约束

Phase 2 冻结本契约时，在线入口仍是 Streamlit `app.py`，Java Backend、FastAPI Router、DTO、
VO 和 HTTP Client 尚未实现。此后仓库已形成 FastAPI 与 Java 阶段性基础，但完整业务接口、
JWT、MySQL/Redis 链路和 Vue Web Client 仍须按后续 Phase 落地。本文件不能单独用来宣称某个
接口当前已经可调用。

固定调用方向：

```text
Web Client → Java public API → Service → AgentClient/KnowledgeClient
           → Python internal API → Facade → existing Graph/KnowledgeService
```

- 公共 API 基础路径：`/api/v1`。
- Python 内部 API 基础路径：`/internal/v1`，不向 Web Client 暴露。
- JSON 使用 UTF-8；时间使用 UTC ISO-8601，例如 `2026-08-01T12:30:00Z`。
- 业务 ID 和 requestId 在 HTTP 中使用标准 UUID 字符串；数据库内部主键不进入 API。
- `userId` 来自 Java 认证上下文，不接受请求体伪造。
- 第一版聊天是同步 POST，不实现 `task_id`、轮询、SSE 或 MQ。
- Vue 只解析 Java 公共 Envelope 与公共 DTO，不解析 Python `InternalEnvelope`；统一 Envelope
  不表示所有 `data` 共用同一个 DTO。
- Java 返回 `UNKNOWN` 时，Vue 只能明确展示“结果暂时无法确认”，不得自动重发、改写为
  `FAILED` 或自行恢复状态；用户明确发起的新请求使用新的幂等键。

## 2. Header 契约

| Header | 公共 API | 内部 API | 规则 |
|---|---:|---:|---|
| `Content-Type` | JSON 或指定 multipart | JSON 或指定 multipart | 不匹配返回 415 |
| `Authorization` | Phase 9 起受保护接口必填 | 不使用用户 JWT | `Bearer <JWT>`；当前只冻结边界 |
| `X-Trace-Id` | 可选 | 必填 | 16～64 位 `[A-Za-z0-9_-]`；Java 对缺失/非法值重新生成 |
| `Idempotency-Key` | 聊天 POST 必填 | 不使用 | UUID；同一用户范围内唯一，Java 映射到内部 requestId |

Java 生成或规范化 `X-Trace-Id`，写入 MDC，并向 Python 传递相同值。TraceId 只用于日志关联，不作为用户身份、主键或幂等键。

## 3. 统一公共响应

所有 Java 公共 API 使用同一 Envelope：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "01J4EXAMPLETRACE01",
  "timestamp": "2026-08-01T12:30:00Z"
}
```

规则：

- `code` 是稳定业务码；成功统一为 `OK`。
- `message` 面向调用方，不包含堆栈、SDK 类型、SQL、文件绝对路径或密钥。
- `data` 成功时为对应 VO，失败时为 `null`。
- `traceId` 与响应头 `X-Trace-Id` 一致。
- HTTP 状态表达协议结果，业务码表达稳定业务语义；错误不统一伪装成 HTTP 200。
- 分页响应的 `data` 固定为 `{items, page, size, total}`；`page` 从 1 开始，默认 1；`size` 默认 20，范围 1～100。

## 4. Java 公共 API

### 4.1 能力清单

| Method | Path | 用途 | 目标阶段 |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | 注册 | Phase 9 |
| `POST` | `/api/v1/auth/login` | 登录 | Phase 9 |
| `POST` | `/api/v1/conversations` | 创建会话 | Phase 7 |
| `GET` | `/api/v1/conversations` | 分页查询当前用户会话 | Phase 7 |
| `GET` | `/api/v1/conversations/{conversationId}` | 查询会话详情 | Phase 7 |
| `GET` | `/api/v1/conversations/{conversationId}/messages` | 分页查询完整聊天记录 | Phase 7 |
| `POST` | `/api/v1/chat/messages` | 同步发送聊天消息 | Phase 6/7 |
| `POST` | `/api/v1/documents` | 上传单个 PDF 并同步请求索引 | Phase 10 |
| `GET` | `/api/v1/documents` | 分页查询当前用户文档 | Phase 10 |
| `GET` | `/api/v1/documents/{documentId}` | 查询文档和索引状态 | Phase 10 |

本表冻结能力和路径，不要求在一个 Phase 一次实现全部 Controller。

### 4.2 创建会话

`POST /api/v1/conversations`

请求：

```json
{
  "title": "车险咨询"
}
```

- `title` 可空；非空时 trim 后 1～100 个字符。

成功：HTTP 201。

```json
{
  "conversationId": "b4765c86-21bc-4dc1-a968-d20f54280319",
  "title": "车险咨询",
  "status": "ACTIVE",
  "createdAt": "2026-08-01T12:30:00Z"
}
```

### 4.3 同步聊天

`POST /api/v1/chat/messages`

请求 Header 必须包含 `Idempotency-Key`。请求体：

```json
{
  "conversationId": "b4765c86-21bc-4dc1-a968-d20f54280319",
  "message": "等待期一般有多久？"
}
```

校验：

- `conversationId` 必填且为 UUID；Java Service 校验会话属于当前用户。
- `message` trim 后 1～4000 个字符；保存和传递 trim 后文本。
- 同一用户重复使用相同 `Idempotency-Key` 且请求摘要相同：处理中返回 `CHAT_REQUEST_IN_PROGRESS`；成功时返回第一次结果；失败/未知时返回已记录状态，不自动再次调用 Python。
- 相同 Key 的 conversation/message 摘要不同：返回 `CHAT_IDEMPOTENCY_CONFLICT`。

成功：HTTP 200。

```json
{
  "conversationId": "b4765c86-21bc-4dc1-a968-d20f54280319",
  "requestId": "f86fb8c9-8293-49b5-9cd2-e870a25d6582",
  "userMessageId": "1dcfa476-57f5-468a-8b41-5603509bb045",
  "assistantMessageId": "e3c526e2-5dc5-4d8c-a79f-d39c5df49368",
  "answer": "不同产品的等待期不同，请以具体条款为准。",
  "sources": []
}
```

`sources` 允许为空。只有 Python 真实返回可追溯来源时才填写，不允许用固定文案、虚构页码或 `N/A` 分数冒充来源。公开来源项：

```json
{
  "documentName": "保险条款.pdf",
  "page": 3,
  "snippet": "……",
  "score": 0.87
}
```

- `page`、`score` 可空；`snippet` 最多 500 字符。
- `toolCalls`、`tool_args`、`agent_monitor`、内部 prompt 和完整检索上下文不进入公共响应。

### 4.4 查询消息

`GET /api/v1/conversations/{conversationId}/messages?page=1&size=20`

每个 `items` 元素：

```json
{
  "messageId": "e3c526e2-5dc5-4d8c-a79f-d39c5df49368",
  "requestId": "f86fb8c9-8293-49b5-9cd2-e870a25d6582",
  "role": "ASSISTANT",
  "content": "回答内容",
  "createdAt": "2026-08-01T12:30:10Z"
}
```

按 `sequenceNo` 升序返回；Service 必须先校验资源归属。

### 4.5 文档上传与状态

`POST /api/v1/documents` 使用 `multipart/form-data`：

- `file`：必填，单个 PDF；大小上限 20 MiB；扩展名、声明 MIME 和文件签名必须同时校验。
- 当前版本不接受批量上传。
- Java 保存受控原文件、创建 MySQL 元数据，再同步调用 `KnowledgeClient`。
- 成功索引返回 HTTP 201；Python 失败/超时则返回对应错误，MySQL 保留 `FAILED` 或 `UNKNOWN` 状态，原文件不丢失。

成功 `data`：

```json
{
  "documentId": "cd2d8624-5876-4204-a504-7dff001e7873",
  "originalFilename": "保险条款.pdf",
  "sizeBytes": 123456,
  "indexStatus": "INDEXED",
  "createdAt": "2026-08-01T12:30:00Z"
}
```

## 5. Java → Python 内部 API

### 5.1 内部统一成功/失败结构

成功：

```json
{
  "success": true,
  "data": {},
  "traceId": "01J4EXAMPLETRACE01"
}
```

失败：

```json
{
  "success": false,
  "error": {
    "code": "AI_VALIDATION_ERROR",
    "type": "VALIDATION",
    "message": "message must not be blank",
    "retryable": false
  },
  "traceId": "01J4EXAMPLETRACE01"
}
```

`type` 只允许 `VALIDATION`、`CONFLICT`、`DEPENDENCY`、`INTERNAL`。内部错误不返回 Python 堆栈。

### 5.2 Agent 同步聊天

`POST /internal/v1/agent/chat`

```json
{
  "requestId": "f86fb8c9-8293-49b5-9cd2-e870a25d6582",
  "sessionId": "b4765c86-21bc-4dc1-a968-d20f54280319",
  "message": "等待期一般有多久？",
  "history": [
    {"role": "user", "content": "我想了解医疗险"},
    {"role": "assistant", "content": "你想了解哪一方面？"}
  ]
}
```

字段规则：

| 字段 | 规则 |
|---|---|
| `requestId` | 必填 UUID；与 Java chat request 一致 |
| `sessionId` | 必填 UUID；只用于本次 Graph 关联，不允许 Python 回查业务库 |
| `message` | trim 后 1～4000 字符 |
| `history` | 必填数组，可为空；只允许 `user`/`assistant`；按最旧→最新排列 |
| 单条 history content | trim 后 1～4000 字符 |
| history 总预算 | 最多 10 条且总计不超过 12000 字符 |

`history` 必须排除当前 `requestId` 刚持久化的 USER 消息；当前问题只通过独立 `message` 字段传递一次。Java 从 Redis/MySQL 读取历史时都按此规则过滤，避免 Graph 收到重复用户输入。

Java 按最新消息优先裁剪，保留不超过 5 轮的完整 user/assistant 对；Python 再次校验相同限制。HTTP 历史不包含 `system`、`tool` 或内部 ToolMessage，因此不会破坏当前 `safe_truncate_messages()` 的 Tool 配对规则。Facade 负责把有限历史转换为 LangChain 消息并调用现有 Graph；不得把 `InMemorySaver` 当作生产长期历史。

成功 `data`：

```json
{
  "requestId": "f86fb8c9-8293-49b5-9cd2-e870a25d6582",
  "answer": "不同产品的等待期不同，请以具体条款为准。",
  "sources": [],
  "durationMs": 1530
}
```

- `durationMs` 是 Python Facade 真实测得的端到端毫秒数，非模型 token 统计。
- 暂不把 `toolCalls` 放入稳定响应；当前源码只能提供适合调试的 Tool 参数和截断结果。
- Python 使用进程内、容量受限的 10 分钟 requestId 登记表做单实例防重：相同请求进行中返回 `AI_REQUEST_IN_PROGRESS`，已完成返回缓存结果，请求摘要不同返回 `AI_REQUEST_CONFLICT`。它不是长期会话事实源；进程重启后仍由 Java 的 MySQL 幂等记录兜底。

### 5.3 知识库索引

`POST /internal/v1/knowledge/documents/index` 使用 `multipart/form-data`：

| Part | 类型 | 规则 |
|---|---|---|
| `metadata` | JSON | 包含 UUID `requestId`、UUID `documentId`、原始文件名和 SHA-256 |
| `file` | binary | 单个、已由 Java 校验且不超过 20 MiB 的 PDF |

Java 以流式 multipart 发送受控内容，不传本机绝对路径，不依赖共享目录。Python 仍需验证大小、PDF 签名和 SHA-256。

成功 `data`：

```json
{
  "requestId": "4aaf804e-b98e-4f65-b8f9-73cc78cfcde8",
  "documentId": "cd2d8624-5876-4204-a504-7dff001e7873",
  "indexStatus": "INDEXED"
}
```

知识库管理补充接口：

| Method | Path | 规则 |
|---|---|---|
| `POST` | `/internal/v1/knowledge/rebuild` | 同步重建；默认不自动重试 |
| `GET` | `/internal/v1/knowledge/status` | 返回加载状态，不返回 FAISS 文件路径 |
| `GET` | `/internal/v1/health/live` | 仅证明进程存活 |
| `GET` | `/internal/v1/health/ready` | 检查服务是否可接收 AI 请求；细项在 Phase 11 实现 |

第一版不提供内部删除索引 API；删除文档与索引一致性在 Phase 10 单独实现并评审。

## 6. 错误码

### 6.1 Java 公共错误码

| HTTP | code | 场景 | 可否原请求自动重试 |
|---:|---|---|---:|
| 400 | `VALIDATION_ERROR` | 字段为空、格式或长度非法 | 否 |
| 401 | `AUTH_UNAUTHORIZED` | 缺少或无效认证 | 否 |
| 403 | `AUTH_FORBIDDEN` | 无权限 | 否 |
| 403 | `CONVERSATION_ACCESS_DENIED` | 会话不属于当前用户 | 否 |
| 404 | `CONVERSATION_NOT_FOUND` | 会话不存在 | 否 |
| 404 | `DOCUMENT_NOT_FOUND` | 文档不存在 | 否 |
| 409 | `CHAT_IDEMPOTENCY_CONFLICT` | 同 Key 对应不同请求摘要 | 否 |
| 409 | `CHAT_REQUEST_IN_PROGRESS` | 相同请求正在处理 | 客户端稍后查询/重试同 Key |
| 413 | `FILE_TOO_LARGE` | 超过 20 MiB | 否 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 不是合法 PDF | 否 |
| 429 | `RATE_LIMIT_EXCEEDED` | 超过限流阈值 | 按 `Retry-After` 新请求 |
| 503 | `RATE_LIMIT_SERVICE_UNAVAILABLE` | Redis 限流保护不可用 | 否 |
| 502 | `AI_EXECUTION_FAILED` | Python 明确返回执行失败 | 否，除非用户发起新请求 |
| 502 | `DOCUMENT_INDEX_FAILED` | Python 明确返回索引失败 | 否，后续显式重建 |
| 503 | `AI_SERVICE_UNAVAILABLE` | 连接失败或熔断打开 | 仅满足幂等规则时有限处理 |
| 504 | `AI_SERVICE_TIMEOUT` | 读取超时，结果未知 | 否 |
| 500 | `INTERNAL_ERROR` | Java 未预期异常 | 否 |

### 6.2 Python 内部错误码

| HTTP | code | type | retryable |
|---:|---|---|---:|
| 400 | `AI_VALIDATION_ERROR` | VALIDATION | false |
| 409 | `AI_REQUEST_IN_PROGRESS` | CONFLICT | true |
| 409 | `AI_REQUEST_CONFLICT` | CONFLICT | false |
| 503 | `AI_LLM_UNAVAILABLE` | DEPENDENCY | true |
| 504 | `AI_LLM_TIMEOUT` | DEPENDENCY | false |
| 500 | `AI_TOOL_ERROR` | INTERNAL | false |
| 500 | `AI_RAG_ERROR` | INTERNAL | false |
| 400 | `KNOWLEDGE_INVALID_DOCUMENT` | VALIDATION | false |
| 500 | `KNOWLEDGE_INDEX_FAILED` | INTERNAL | false |
| 500 | `AI_INTERNAL_ERROR` | INTERNAL | false |

`AgentClient`/`KnowledgeClient` 负责把内部错误映射为 Java Client 异常，Service 决定聊天请求或文档状态，全局异常处理器再映射公共错误。

当前 `_tools_node` 会把部分 Tool 异常转换为文本结果，尚不能稳定区分所有 Tool 失败。`AI_TOOL_ERROR` 只在未来 Facade 获得明确失败信号时发送；禁止依赖中文字符串猜测错误并伪造稳定分类。该实现差距保留为后续 WARNING，不修改本阶段 Python 核心代码。

## 7. 超时、重试和熔断默认值

| 调用 | 连接超时 | 读取超时 | 自动重试 |
|---|---:|---:|---|
| Java → Python chat | 3 秒 | 60 秒 | 默认 0；仅确认请求未送达时最多 1 次，复用 requestId |
| Java → Python knowledge | 3 秒 | 300 秒 | 0 |
| Java → Python health | 1 秒 | 2 秒 | 最多 1 次 |
| Python → DeepSeek | 3 秒 | 50 秒 | 0；总耗时必须留在 Java 60 秒预算内 |

读取超时表示结果未知，不能自动发起第二次聊天或索引。配置可由环境变量覆盖，但必须满足：正数、Python 下游预算小于 Java 上游预算、禁止无限超时。

`AgentClient` 与 `KnowledgeClient` 使用独立熔断实例，默认值：

- count-based sliding window：20 次；
- minimum calls：10 次；
- failure rate threshold：50%；
- open state：30 秒；
- half-open permitted calls：3 次；
- 统计网络错误、超时和 5xx；不统计参数/权限类 4xx。

这些是实现默认值，Phase 11 必须通过故障测试验证后才能声称生产可用。

## 8. 安全与日志

- 公共和内部响应均不得返回异常堆栈、API Key、JWT、密码、SQL 或绝对路径。
- 日志记录 traceId、requestId、状态、耗时和错误码；默认不记录完整用户消息或 PDF 内容。
- 文件名只作展示；存储名由 Java 生成，禁止直接拼接用户文件名形成路径。
- OpenAPI 只公开 Java 公共 API；内部 Python 文档只在受控内部环境开放。

## 9. 正常与异常流程验收

### 正常聊天

1. Java 校验请求和会话归属；
2. 根据 Idempotency-Key 创建唯一 chat request 和用户消息；
3. 读取 MySQL/Redis 中排除当前 requestId 的最多 10 条有限历史；
4. 使用同一 requestId 和 traceId 调用 Python；
5. Python Facade 调现有 Graph，返回真实 answer/sources；
6. Java 持久化助手消息和 `SUCCEEDED`，失效近期缓存后返回 VO。

### 异常聊天

- 参数错误不调用 Python；
- 连接失败只有在明确未送达时才允许最多一次连接级重试；
- 读取超时写 `UNKNOWN`，不重试、不伪造助手消息；
- 相同幂等键不重复写消息或调用 Tool；
- 熔断打开快速返回 `AI_SERVICE_UNAVAILABLE`。

## 10. 冻结结论

本文件与 `docs/ARCHITECTURE.md v1.0` 一致，未改变 Java/Python 服务边界。后续实现若需要修改路径、稳定字段、错误码、历史预算、幂等语义或容错默认值，必须先更新决策记录并经过 Review；若同时改变服务职责或事实来源，则按架构变更流程处理。
