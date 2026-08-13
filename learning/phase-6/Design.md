# Phase 6 Design Notes

## 1. 公共和内部契约隔离

公共请求只接受 `conversationId + message`，`Idempotency-Key` 位于 Header。Service 将其转换为 Python 冻结的 `requestId + sessionId + message + history`，再把内部响应转换为公共 `ChatResponse`。Controller 不接触 Python Envelope，Python 内部错误 message 也不会透传给 Web。

## 2. 分层职责

- `ChatController`：HTTP 绑定、校验、读取 Header、返回公共 Envelope。
- `ChatService`：用例编排、稳定 ID、响应校验、来源映射、错误翻译。
- `HttpAgentClient`：内部 HTTP、TraceId、Envelope 解析、超时和协议故障分类。
- Python `AgentFacade`：请求防重、Graph 调用和内部结果表达。

依赖方向保持 `Controller → Service → Client → Python`，没有 Controller→Client 旁路。

## 3. Phase 6 幂等边界

Java 根据 `Idempotency-Key` 生成稳定 `requestId`，同一 Key 因而跨 Java 重复调用保持相同内部请求标识。Python `RequestRegistry` 在当前进程内缓存同摘要结果，并拒绝同 requestId 的不同摘要。

该实现不写数据库，进程重启后状态丢失，不能宣称持久幂等。Phase 7 才由 MySQL 唯一约束和请求状态提供最终兜底。

## 4. HTTP 生命周期与兼容性

应用只创建一个共享 JDK `HttpClient` / `RestClient`，连接超时 3 秒，读取超时 60 秒，不自动重试。Client 固定 `HttpClient.Version.HTTP_1_1`；诊断证明默认 h2c 升级结合 chunked body 时，当前 Uvicorn 收到空 body。固定 HTTP/1.1 后真实双进程请求恢复正常。

这不是 API 或架构变化，只是 Java→Uvicorn 传输适配。

## 5. 严格 JSON 与校验访问器

`AgentChatRequest` 的两个 `@AssertTrue` 方法以 `is...` 开头，Jackson 会把它们视为属性。使用 `@JsonIgnore` 防止把校验状态序列化到 Python 的 `extra="forbid"` 契约中；回归测试断言内部 JSON 只能包含四个冻结字段。

## 6. 错误与来源映射

- 连接故障 → `AI_SERVICE_UNAVAILABLE`；
- 读取超时 → `AI_SERVICE_TIMEOUT`；
- Python 幂等处理中/冲突 → 对应公共 409；
- 非法 Envelope、未知内部 code、非法 answer/source → `AI_EXECUTION_FAILED`。

来源只映射 Python 实际返回的 `documentName/page/snippet/score`。空来源返回空列表，不伪造引用。

## 7. 明确未选择

- 不加入自动重试、熔断、MQ 或异步任务；
- 不在 Controller 访问 Mapper、数据库或 Python Client；
- 不引入数据库/Redis 模拟持久化；
- 不执行真实 DeepSeek、Embedding、FAISS 联调；
- 不修改冻结的 Phase 2 API、Database、Redis 文档。
