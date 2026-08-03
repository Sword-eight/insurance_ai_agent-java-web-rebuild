# Phase 2 MiniCourse：把架构边界变成可执行契约

> 面向基础：会 Java 语法、面向对象和 MySQL CRUD，不要求会 Spring MVC、Redis 或分布式系统。
> 本阶段只学习 4 个知识点，只设计 API、MySQL、Redis 和错误契约，不写实现代码。

## 课程 1：API 契约不是“几个 JSON 字段”，而是两个服务共同遵守的边界

### 1. 当前项目为什么需要它

当前 Python 的 `handle_chat_message()` 返回 `final_answer`、`tool_calls`、
`retrieved_sources`、`agent_monitor` 和 `total_time`，这是 Streamlit 内部字典，不是稳定 HTTP
契约。未来 Java 的 `AgentClient` 不能直接依赖 LangChain 消息类或 Python 内部字段名。

### 2. 它解决了什么问题

Phase 2 要分别冻结两类 API：

```text
Web Client ↔ Java 公共 API
Java AgentClient / KnowledgeClient ↔ Python 内部 API
```

公共 API 面向用户业务，内部 API 面向 AI 调用。两者可以传递相同答案，但不能共用 Entity，
也不能让 Python 的异常类型泄露到浏览器。

### 3. 不使用会怎样

- Python 把 `final_answer` 改名后，Java 运行时才发现反序列化失败。
- Web Client 被迫理解 ToolMessage、Retriever 或 Python 异常。
- `userId` 由请求体伪造，而不是来自未来的认证上下文。
- 错误时一会儿返回字符串，一会儿返回对象，调用方无法稳定判断。

### 4. 本项目代码会如何使用

目标公共链使用 `ChatRequestDTO → ChatController → ChatService → ChatResponseVO`；目标内部链
使用 `AgentChatRequest → AgentClient → Python ChatRequestSchema`。内部请求至少包含冻结架构
规定的 `requestId`、`sessionId`、当前 `message` 和有限 `history`。

Python Facade 把这些字段转换为现有 `AgentGraphBuilder` 能使用的输入，再把当前
`_parse_agent_result()` 的结果转换为稳定内部响应。当前仓库尚无这些 DTO、VO 或 Router。

### 5. 一个简单类比

API 契约像插座标准。墙内电线和电器内部电路可以各自变化，但插头尺寸、电压和接地规则必须
双方提前约定，不能等通电后再猜。

### 6. 容易踩的坑

- 把 MyBatis Entity 直接作为 Controller 响应。
- 公共响应和内部响应混成一个万能 DTO。
- 把 `traceId` 当幂等键，或把 `requestId` 当用户身份。
- 为未来可能用到的 token、模型详情编造字段。
- 只设计成功 JSON，不设计校验、超时和依赖失败。

### 7. 当前阶段需要掌握到什么深度

能为每个字段说明：谁生成、是否必填、是否敏感、由谁校验、失败时返回什么。能区分 DTO、
VO、Entity 和 Python Schema，但不需要掌握 Spring/Pydantic 注解语法。

### 8. 5～10 分钟练习

拿当前 `handle_chat_message()` 返回字典，划分为三组：应进入公共 Chat VO、只应留在内部调试、
当前不能稳定承诺。为每个保留字段写出类型、是否可空和生成方。

## 课程 2：数据库设计先冻结业务事实和状态机，再考虑 SQL

### 1. 当前项目为什么需要它

冻结架构要求 MySQL 保存完整聊天记录和文档元数据，但当前数据只在 Streamlit
`session_state` 与 `InMemorySaver` 中。未来如果只设计一张 `message` 表而不设计归属、状态
和 requestId，Python 超时后就无法判断消息是否已处理或是否允许重试。

### 2. 它解决了什么问题

Phase 2 先确定核心记录及关系：

```text
User 1 ── N Conversation 1 ── N ChatMessage
User 1 ── N KnowledgeDocument
```

同时冻结消息和文档状态，例如消息从处理中进入成功或失败，文档从待处理进入构建中、成功或
失败。具体表名、字段、唯一约束、索引和审计时间必须与这些业务事实对应。

### 3. 不使用会怎样

- 助手消息失败后仍被当作成功展示。
- 同一 requestId 写入两次，产生重复用户消息或助手消息。
- 用户可以查询不属于自己的 conversation。
- Java 仅因 Python 接收 HTTP 就把文档标记为索引成功。
- 删除会话时没有明确消息如何处理。

### 4. 本项目代码会如何使用

目标 `ChatService` 先校验 conversation 归属，以 requestId 创建或复用本次消息处理记录，再调
`AgentClient`，最后更新成功/失败状态。`ChatMessageMapper` 只执行数据库访问，不决定超时
后应该写什么状态。

目标 `DocumentService` 保存 PDF 原文件和 MySQL 元数据，调用 `KnowledgeClient` 后再更新索引
业务状态。Python 的 `KnowledgeService` 仍只返回索引处理结果，不访问这些表。

### 5. 一个简单类比

快递系统不只保存“有一个包裹”，还要保存属于谁、运单号和运输状态。SQL 是记账方式，状态机
才决定这笔记录在业务上代表什么。

### 6. 容易踩的坑

- 只画表，不写唯一事实来源和状态变化。
- 用数据库自增 ID 代替跨请求 requestId。
- 在 Controller 开事务或直接调 Mapper。
- 把完整聊天 JSON 同时塞进 Conversation 和 Message。
- 把 FAISS 向量或 Python对象序列化进 MySQL。

### 7. 当前阶段需要掌握到什么深度

能画实体关系、说明主外键方向、唯一约束、常用查询索引和状态字段；能指出 ChatService 的事务
边界。暂不需要写建表 SQL 或掌握 MyBatis-Plus API。

### 8. 5～10 分钟练习

为“用户发送消息，Python 读取超时”写出数据库状态变化草图。要求同一 requestId 再次到达时
不能新增第二条用户消息，并说明哪些更新应在同一 Java Service 事务中。

## 课程 3：Redis 设计必须同时写 Key、TTL、来源和失效方式

### 1. 当前项目为什么需要它

目标架构让 Redis 保存近期消息缓存、限流、幂等标记和短期数据，但 MySQL 才是完整消息的唯一长期事实源。
如果只写“使用 Redis 提升性能”，实现时很容易把缓存变成永久存储，或者 Key 互相覆盖。

### 2. 它解决了什么问题

每类 Redis 数据必须冻结四件事：

```text
Key 模式 + Value 结构 + TTL + 失效/回源规则
```

例如近期消息缓存必须能由 MySQL 重建；限流 Key 必须有时间窗口；幂等 Key 必须绑定 requestId
和处理状态。Redis 故障不能删除 MySQL 中的完整消息。

### 3. 不使用会怎样

- Key 没有用户或会话维度，不同用户互相覆盖。
- 没有 TTL，限流和幂等记录永久增长。
- 缓存与数据库更新顺序错误，用户长期看到旧消息。
- Redis 丢失后无法恢复聊天记录。
- 一个通用 JSON Key 同时承担缓存、锁和幂等。

### 4. 本项目代码会如何使用

未来 `ChatService` 可以先查近期消息缓存，未命中时由 `ChatMessageMapper` 查询 MySQL并回填；
限流在 Java 入口按用户/接口计数；requestId 幂等记录阻止重复业务写入。Python 不访问这个
Redis，也不依赖 Redis 获取历史。

Phase 2 的 `docs/REDIS.md` 会冻结命名空间、Key 示例、TTL、写入方、读取方和降级方式，但本轮
尚不创建实现。

### 5. 一个简单类比

Redis 像前台便签，MySQL 像正式档案。便签能快速提醒近期事项，但便签丢了必须能查档案，不能
因为前台方便就销毁正式记录。

### 6. 容易踩的坑

- 使用 `KEYS *` 或无命名空间 Key。
- TTL 写在代码常量里但文档没有含义。
- 把缓存未命中当作“数据不存在”。
- 限流失败时默认放行/拒绝却不做明确决策。
- 幂等 TTL 小于一次最慢请求可能持续的时间。

### 7. 当前阶段需要掌握到什么深度

能为近期消息、限流、幂等分别写出 Key/Value/TTL/失效规则，并说明 Redis 不可用时的行为。
不需要掌握 Lua、Redisson 或 Redis 集群。

### 8. 5～10 分钟练习

设计三个互不冲突的 Key 模式：用户聊天限流、会话近期消息、聊天 requestId 幂等。不要给具体
TTL 数字，先说明 TTL 必须覆盖的业务时间范围和过期后如何回源或重试。

## 课程 4：错误码、TraceId、超时和幂等共同定义一次调用的结局

### 1. 当前项目为什么需要它

一次目标聊天跨越浏览器、Java、Python 和 DeepSeek。当前 Python Tool 可能把异常格式化成
文本，Java 侧又尚不存在。如果不先冻结错误语义，读取超时后自动重试可能重复调用 Tool，
而用户只看到模糊的“系统错误”。

### 2. 它解决了什么问题

四个概念各有职责：

- 错误码：稳定表达失败类别和调用方动作；
- TraceId：串联同一次链路日志；
- requestId/幂等键：标识同一次业务执行；
- 超时/重试/熔断：限制等待和故障扩散。

它们不能互相替代。TraceId 相同不代表请求幂等，超时也不代表 Python 已停止执行。

### 3. 不使用会怎样

- 连接失败和读取超时都重试，产生重复 DeepSeek/Tool 调用。
- Java 保存两条助手消息，或把未知结果误标成功。
- Python 堆栈和内部类名泄露给 Web Client。
- Java/Python 日志无法关联。
- 熔断开启后返回与参数错误相同的错误码。

### 4. 本项目代码会如何使用

Java 入口生成/接收 TraceId，并为用户消息生成唯一 requestId。`AgentClient` 连接失败只有在可
确认请求未送达、复用同一 requestId 且时间预算允许时才能有限重试；读取超时结果未知，不得
自动调用第二次。

Python Router 回传 TraceId，Facade 使用 requestId 作为执行标识并调用现有 Graph。Java
`ChatService` 根据 Client 结果更新消息状态，统一异常处理器再转换为公共错误响应。

### 5. 一个简单类比

TraceId 是快递查询号，requestId 是订单号，错误码是异常原因，超时是你不再继续等电话。电话
超时并不能证明仓库没有发货，因此不能直接再下一张新订单。

### 6. 容易踩的坑

- 所有错误都返回 HTTP 200 + `success=false`，或所有错误都返回 500。
- 错误码直接等于异常类名。
- 对 POST 聊天统一重试三次。
- 熔断器与超时共享一个模糊“AI失败”状态。
- requestId 没有唯一约束，只有日志记录。

### 7. 当前阶段需要掌握到什么深度

能区分校验、权限、资源不存在、冲突、限流、Python 不可用、连接失败、读取超时和内部错误；
能说明哪些可重试、由谁映射、消息状态如何变化。具体 Resilience4j 代码属于后续 Phase。

### 8. 5～10 分钟练习

为以下场景各写一行契约：message 为空、conversation 不属于用户、连接 Python 失败、Python
读取超时、RAG 返回空结果、熔断打开。每行包含 HTTP 状态类别、业务错误码类别、retryable、
消息状态和是否允许自动重试。
