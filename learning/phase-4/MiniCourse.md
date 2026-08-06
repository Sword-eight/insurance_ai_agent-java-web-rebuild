# Phase 4 MiniCourse：FastAPI 最小包装与进程生命周期

> 目标：把 Phase 3 的 HTTP Skeleton 安全接到现有 Python AI 对象图，证明“启动一次、请求复用、错误稳定、重复请求不重复执行”。
> 本阶段只讲 5 个面试必需概念，不实现 Java 调用链、数据库、Redis、上传安全或索引并发。

## 概念 1：FastAPI lifespan 是 Composition Root 的宿主

昂贵对象不能在 Router 或每个请求中创建。FastAPI lifespan 在进程启动时调用一次 bootstrap，
把 Graph、KnowledgeService、Facade 和 request registry 放进应用状态；请求只读取这些对象。

```text
process startup
→ lifespan
→ bootstrap once
→ app.state.runtime
→ many requests reuse the same Facades/Graph/Embedding/Index
→ shutdown closes closable resources
```

面试要点：进程级单例降低模型加载和连接创建成本，但“只创建一次”不等于线程安全；索引重建并发仍需 Phase 10 单独解决。

## 概念 2：Facade 是 HTTP 契约与 LangGraph 状态之间的防腐层

Router 只认识 Pydantic Schema；Graph 只认识 LangChain Message 和 AgentState。Facade 把冻结的
`requestId/sessionId/message/history` 转换为 Graph 输入，再把最终 AIMessage 转成稳定结果。

```text
HTTP AgentChatRequest
→ AgentChatCommand
→ HumanMessage / AIMessage
→ AgentGraphBuilder
→ AgentChatResult
→ InternalEnvelope
```

Facade 不返回 Streamlit 的 `tool_args`、monitor 或伪造来源，也不使用“抱歉”固定文案冒充模型回答。没有真实来源证据时，`sources` 必须为空。

## 概念 3：有限历史与 Checkpointer 隔离解决不同问题

Java 传入的最多 5 轮历史是本次推理的权威上下文；`InMemorySaver` 只是 Python 内部开发状态，
不能替代 MySQL。若每次既传完整有限历史，又复用同一个 LangGraph thread，消息会重复累加。

Phase 4 需要保持两个标识：

- `sessionId`：业务会话关联，写入 Graph state；
- `requestId`：本次执行隔离与防重标识，使不同请求不复用旧 checkpoint。

HTTP 执行的 checkpoint 只是临时运行状态，必须在 `finally` 中删除；否则每个新 requestId
都会在进程内永久留下状态，形成无界内存增长。

面试要点：上下文裁剪控制输入，checkpoint namespace 控制运行状态；二者不能混为一个长期事实来源。

## 概念 4：进程内幂等登记表是一个并发状态机

同一个 `requestId` 在 10 分钟窗口内只有三种关键结果：

```text
不存在 → 注册 IN_PROGRESS(lease) → 执行 Graph → SUCCEEDED(result) / FAILED(error)
IN_PROGRESS + 相同摘要 → 409 AI_REQUEST_IN_PROGRESS
SUCCEEDED + 相同摘要 → 返回缓存结果，不再执行 Graph
任意已存在状态 + 不同摘要 → 409 AI_REQUEST_CONFLICT
```

登记表必须容量受限、使用单调时钟计算 TTL，并用锁保证“检查 + 注册”原子化。它只防御单进程短时重复调用；Java/MySQL 仍是长期幂等事实源。
每次执行还需要独立 lease：TTL 到期后旧执行若迟到，不能覆盖同 requestId 的新执行状态。

## 概念 5：live、ready 与安全降级

- liveness：HTTP 进程是否存活，不要求模型和索引可用；
- readiness：bootstrap 是否成功，服务是否能接收 AI 请求；
- 业务错误：按冻结错误码返回，不泄露堆栈、路径、密钥或完整消息。

启动初始化失败时，进程可以保持 live=200、ready=503；Router 不得退回到每请求重新初始化。
知识库 `rebuild()` 当前会先删除索引，因此 Phase 4 不通过 HTTP 启用它，也不把上传内容写入受控目录；这些数据安全与并发语义属于 Phase 10。

## 5～10 分钟自测

1. 为什么不能在 `get_agent_facade()` 中调用 `init_services()`？
2. 同一 session 的两个不同 requestId 为什么不能共享同一份本地 checkpoint 历史？
3. 两个线程同时提交同一 requestId 时，哪一步必须在锁内完成？
4. bootstrap 失败后，live 和 ready 应分别返回什么？
5. 为什么 Phase 4 可以开放只读 knowledge status，却不能直接开放当前的 delete-then-build rebuild？
