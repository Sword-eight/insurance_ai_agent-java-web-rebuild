# Phase 6 MiniCourse：Java 调 Python 同步聊天链

> 阶段目标：完成 `ChatController → ChatService → AgentClient → Python FastAPI` 的无数据库同步链路。
> 当前状态：实现完成；保留为本阶段的面试前置课程。
> 阶段边界：不接 MySQL、Redis、JWT、Vue、MQ、异步任务或知识库写入。

本阶段只要求掌握 5 个能直接支撑编码、排错和面试表达的概念。

## 1. Controller、Service、Client 的依赖方向

### 核心结论

```text
HTTP → ChatController → ChatService → AgentClient → Python
```

- Controller 负责 HTTP 绑定、Bean Validation、读取 Header、调用 Service 和返回公共 Envelope。
- Service 负责聊天用例编排、生成内部执行标识、调用 Client、把 Client 失败翻译为公共业务错误。
- Client 负责 Java→Python HTTP、内部 DTO、超时和内部错误解析，不知道 Web、用户权限或数据库。

Controller 不能直接调用 `AgentClient`。否则 Phase 7 增加会话归属、事务和消息持久化时，只能把
业务逻辑继续塞进 Controller，最终破坏冻结架构。

### 面试问法

问：无数据库的 Phase 6 为什么还要保留 Service？

答：数据库只是 Service 未来的一项依赖，不是 Service 存在的原因。Service 表达聊天用例和
失败语义；Phase 7 增加 Mapper 时，Controller 与 AgentClient 的边界不需要重写。

## 2. 公共 DTO/VO 与内部 DTO/Envelope 转换

Web 调 Java 与 Java 调 Python 面向不同调用方：

```text
ChatRequest DTO
→ ChatService command
→ AgentChatRequest
→ Python InternalEnvelope<AgentChatResponse>
→ ChatResponse VO
→ ApiResponse<ChatResponse>
```

- 公共请求使用 `conversationId + message`，并从 Header 取得 `Idempotency-Key`。
- 内部请求使用 `requestId + sessionId + message + history`。
- Vue/公共调用方永远看不到 Python 的 `InternalEnvelope`、内部错误类型、Tool 参数或 prompt。
- `sources` 只能传递 Python 真实返回的来源；为空就返回空列表，不能补固定页码或假分数。

### 容易踩的坑

- Controller 直接返回 `AgentChatResponse`，泄露内部模型。
- 用一个 `Map<String, Object>` 贯穿所有层，编译器无法检查契约。
- 把 Python 的异常 message 原样返回 Web，泄露内部路径或实现细节。

## 3. 同步 HTTP Client 的生命周期、超时和错误分类

`AgentClient` 是应用级单例，复用同一个 HTTP Client/连接资源；不能在每次聊天中手动 `new`。

冻结预算：

```text
Java → Python connect timeout = 3s
Java → Python read timeout    = 60s
Python → DeepSeek read budget = 50s
```

必须区分：

- 连接失败：可确认没有建立连接，映射 `AI_SERVICE_UNAVAILABLE`。
- 读取超时：Python 可能仍在执行，映射 `AI_SERVICE_TIMEOUT`，不得自动重发。
- Python 明确失败 Envelope：解析稳定内部 code，再由 Service 转为公共错误。
- 非法/空 Envelope：属于协议或依赖失败，不能当成功返回空 answer。

Phase 6 默认不做聊天自动重试，不提前实现 Phase 11 的熔断调优。

## 4. TraceId、Idempotency-Key 与 requestId

三个 ID 不能混用：

| 标识 | 来源 | 作用 |
|---|---|---|
| TraceId | Java 入口复用或生成 | 串联日志；同一次重试也可以有新 TraceId |
| Idempotency-Key | 公共调用方 Header | 标识同一次提交意图 |
| requestId | Java 首次受理时形成 | 跨 Java/Python 标识一次业务执行 |

Java 调 Python 时必须透传同一个 `X-Trace-Id`，并在内部 body 传 `requestId`。读取超时后不能用
新的 requestId 自动再调一次 Python。

Phase 6 没有 MySQL，因此只能验证单进程、短生命周期的防重行为，不能宣称具备持久幂等、
重启恢复或 `UNKNOWN` 对账。Phase 7 才由 MySQL 唯一约束和请求状态成为最终兜底；Phase 8 的
Redis 只能加速。任何 Phase 6 过渡实现都必须保持三种 ID 的概念分离，并明确其非持久限制。

## 5. 分层测试与端到端证据

一条聊天链需要不同层次的证据：

1. DTO 测试：UUID、消息长度、history 轮次和字符预算。
2. Service 单元测试：成功转换、明确失败、连接失败、读取超时，且 Controller 不旁路 Client。
3. Client HTTP 测试：真实 JSON、Header、超时和错误 Envelope 映射。
4. Controller/MockMvc 测试：公共路径、Envelope、HTTP 状态、TraceId、`Idempotency-Key`。
5. Java→Python smoke：两个进程真实通过 HTTP 完成一次无数据库聊天链。

Mock 只能证明本层决策，不能冒充跨进程联调。真实 DeepSeek/Embedding/FAISS 未运行时必须明确
写成离线替身验证；只有实际启动两端并取得响应，才能声称 Java→Python HTTP 链已打通。

## 冲刺自检

完成预习后，应能在 2～3 分钟内回答：

1. 为什么 Controller 不能直接注入 AgentClient？
2. 公共 Envelope 与 Python InternalEnvelope 为什么不能共用？
3. 连接失败和读取超时为什么不能统一重试？
4. TraceId、Idempotency-Key、requestId 分别解决什么问题？
5. 哪些测试能证明分层正确，哪个证据才能证明两进程真正连通？
