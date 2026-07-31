# Phase 1 MiniCourse：把单体 AI 应用拆成边界清晰的双服务

> 面向基础：会 Java 语法、面向对象和 MySQL CRUD，不要求会 Spring Boot。
> 本阶段只学习 4 个知识点，只设计架构，不创建 Java/FastAPI 工程。

## 课程 1：服务边界不是按语言切，而是按数据和能力所有权切

### 1. 当前项目为什么需要它

当前仓库的在线入口是 `app.py`，一次 Streamlit 进程同时承担界面、会话、Agent、
RAG、文件上传和索引管理。目标平台要增加用户、权限、聊天记录、Redis 和 MySQL。
如果只因为“Java 擅长后端、Python 擅长 AI”就随意拆分，两个服务会同时修改会话或
知识库状态，最后谁的数据算准无法回答。

### 2. 它解决了什么问题

服务边界首先确定“谁拥有最终解释权”：

```text
Java：用户、权限、会话、消息、文档元数据的业务事实
Python：Agent、Tool、RAG、Embedding、FAISS、LLM 的 AI 事实
```

Java 保存“用户发过什么、系统返回什么”；Python决定“如何检索、是否调用工具、
如何生成回答”。两个服务通过 HTTP 契约交换必要数据，不共享业务数据库。

### 3. 不使用会怎样

- Java 和 Python 都保存聊天记录，失败后无法判断哪份是最终数据。
- Java 直接读 FAISS，必须理解 Python 序列化格式和 Embedding 维度。
- Python 直连 MySQL，AI 服务被用户表、权限表和事务规则绑死。
- 一次数据库表变更可能迫使 RAG 服务同时发布。

### 4. 本项目代码会如何使用

目标聊天链是：

```text
Web Client
→ Java ChatController
→ Java ChatService
→ Java AgentClient
→ Python HTTP Router
→ 现有 AgentGraphBuilder.invoke()
```

`ChatService` 管会话归属和消息状态；`AgentGraphBuilder` 继续执行现有
`agent → router → tools → agent`。Java 不需要知道 `ToolMessage` 或 FAISS。

### 5. 一个简单类比

银行柜台负责客户身份、账户和交易记录；验钞机负责判断钞票特征。柜台不拆开验钞机
读取内部传感器，验钞机也不登录银行账户数据库。两者只交换“待检测钞票”和“检测结果”。

### 6. 容易踩的坑

- 把 `conversationId` 传给 Python，就误认为会话归 Python 所有。
- 把文档元数据和 FAISS 索引当成同一类数据。
- 让 Web Client 绕过 Java 直接访问 Python。
- 为减少一次 HTTP 调用，把鉴权逻辑复制到 Python。
- 把当前 `InMemorySaver` 当成永久聊天记录。

### 7. 当前阶段需要掌握到什么深度

能够针对一项数据回答三个问题：

1. 谁创建它？
2. 谁是唯一事实来源？
3. 另一个服务通过什么最小字段使用它？

不需要现在设计完整 MySQL 表或 Redis Key；这些属于 Phase 2。

### 8. 5～10 分钟练习

把下面项目分别归入 Java、Python 或 HTTP 契约：

1. 用户密码散列
2. FAISS 索引
3. 聊天消息状态
4. `conversationId`
5. Tool Calling
6. 文档上传者
7. 检索来源
8. JWT

参考答案：Java 拥有 1、3、6、8；Python 拥有 2、5；4 和 7 通过契约传递，
但 4 的业务含义归 Java，7 的内容由 Python 生成。

## 课程 2：Java 分层的关键是依赖方向，不是文件夹数量

### 1. 当前项目为什么需要它

未来 Java 后端会同时处理 HTTP、鉴权、数据库、Redis 和 Python 调用。如果
`ChatController` 直接调用 `ChatMessageMapper` 再调用 Python，Controller 会逐渐成为
无法测试的“大总管”。本项目需要一条固定、面试时能口述的依赖链。

### 2. 它解决了什么问题

冻结依赖方向：

```text
Controller → Service → Client / Mapper
```

- Controller：接收 HTTP、校验 DTO、调用一个 Service、返回 VO。
- Service：业务编排和事务边界。
- Client：调用 Python 内部 HTTP API。
- Mapper：访问 MySQL。

`Config`、`Security`、`Exception` 是横切基础设施，但不能绕过 Service 改写业务数据。

### 3. 不使用会怎样

- Controller 同时承担事务、远程调用和异常恢复。
- 单元测试必须启动数据库和 Python 服务才能测一个 HTTP 参数错误。
- 同一业务规则会复制到多个 Controller。
- Mapper 返回的 Entity 直接暴露到 Web，数据库字段变化会破坏 API。

### 4. 本项目代码会如何使用

以同步聊天为例：

```text
ChatController
  接收 ChatRequest DTO
→ ChatService
  校验会话归属、编排消息状态
→ AgentClient
  调用 Python
→ ChatMessageMapper
  持久化助手消息或失败状态
→ ChatResponse VO
```

Phase 6 尚未接数据库时，`ChatService` 仍是编排入口；Phase 7 加 Mapper 时不用改变
Controller 到 Python 的边界。

### 5. 一个简单类比

餐厅服务员只接单和上菜，厨师长安排制作，采购员联系供应商，仓库管理员记录库存。
如果服务员直接进仓库改库存再打电话给供应商，岗位存在但职责已经失效。

### 6. 容易踩的坑

- Controller 直接注入 `AgentClient`。
- Controller 直接注入 Mapper。
- Service 返回 Entity，导致密码散列或删除标记泄露。
- 为了“看起来高级”增加 Domain Service、Factory、Command Bus。
- 在业务方法中手动 `new` Spring 管理的 Client。

### 7. 当前阶段需要掌握到什么深度

能解释 Entity、DTO、VO 的区别：

- Entity 对应持久化结构；
- DTO 承接请求或服务间参数；
- VO 是对外响应视图。

暂时不需要掌握 Spring MVC 注解、MyBatis-Plus API 或事务传播级别。

### 8. 5～10 分钟练习

下面代码有什么问题？

```java
class ChatController {
    ChatMessageMapper mapper;
    AgentClient client;

    ChatResponse chat(ChatRequest request) {
        mapper.insert(...);
        return client.chat(request);
    }
}
```

用不超过五行伪代码改写为 `Controller → ChatService → AgentClient/Mapper`，并说明
如果 Python 超时，失败状态应由哪一层决定。答案是 Service，因为它掌握完整业务流程。

## 课程 3：Python 迁移采用薄包装，不把现有 AI 核心改成 Java 风格

### 1. 当前项目为什么需要它

当前 Python 已有真实调用关系：

```text
AgentGraphBuilder
→ InsuranceRAGTool
→ RetrievalService
→ BaseRetriever
→ LangChain/LlamaIndex
→ FAISS
```

若为了让目录长得像 Java 而移动 `graph/`、`tools/`、`services/`、`rag/`，会破坏
已有 import、测试和模块语义，却没有增加业务能力。

### 2. 它解决了什么问题

只在外侧增加 HTTP 薄包装：

```text
FastAPI Router（未来）
→ Python Application Wrapper（未来）
→ application.bootstrap 初始化的现有对象
→ graph / tools / services / rag（保留）
```

Router 只做 HTTP 校验、TraceId 传递、调用和异常映射；它不直接查询 FAISS，也不重新实现
Agent Router。

### 3. 不使用会怎样

- Python Router 直接调用 Retriever，绕过 Agent 和 Tool Calling。
- 同一套 RAG 逻辑在旧 Streamlit 与新 FastAPI 中复制。
- 大量重命名造成回归，但功能没有变化。
- 面试时无法说明重构收益，只能说“为了分层移动了文件”。

### 4. 本项目代码会如何使用

`application/bootstrap.py` 当前负责创建 Embedding、Builder、Retriever、Service、Tools
和 Graph。未来 FastAPI lifespan 调用一次 bootstrap，把结果保存为应用级资源。

聊天 Router 调用包装后的 `AgentGraphBuilder.invoke()`；知识库 Router 调用
`KnowledgeService`。`app.py` 和 `ui/` 可保留为本地调试入口，但不是生产流量入口。

### 5. 一个简单类比

已有发动机工作正常，迁移是给发动机安装标准接口和固定支架，而不是把每个零件改成
另一家汽车厂的命名。

### 6. 容易踩的坑

- 新建一个“统一 Repository 层”后把 `graph/` 全部塞进去。
- Router 直接调用 `VectorStoreManager`。
- FastAPI 每个请求都执行 `init_services()`。
- 为了 Adapter 名称好看，把所有第三方 SDK 再包一层。
- 删除 Streamlit，导致现有本地调试能力丢失。

### 7. 当前阶段需要掌握到什么深度

能区分三类调整：

- 保留：现有核心语义不变；
- 包装：在边界增加 HTTP/Application 适配；
- 后续修债：并发、上传安全、来源追踪等在对应 Phase 处理。

当前不需要会写 FastAPI 路由或 Pydantic 模型。

### 8. 5～10 分钟练习

为以下四种调用判断“允许”或“禁止”，并说明原因：

1. FastAPI Router → Agent Application Wrapper
2. FastAPI Router → FAISS
3. AgentGraphBuilder → Tool
4. Java Controller → Python AgentClient

答案：1、3 允许；2 禁止，因为绕过现有编排；4 禁止，Controller 必须先调用 Service。

## 课程 4：生命周期和容错必须与同步聊天的副作用一起设计

### 1. 当前项目为什么需要它

BGE 模型、FAISS 索引、LLM Client 和 HTTP Client 创建成本高或持有连接资源。当前
`app.py` 在 Streamlit rerun 时会再次执行 `init_services()`；未来服务化后不能让每个
HTTP 请求重新加载模型或建立连接池。同时，同步聊天可能耗时较长，盲目重试还会产生
重复 LLM 调用和重复消息。

### 2. 它解决了什么问题

目标生命周期分三类：

```text
进程级：HTTP Client、LLM Client、Embedding、Vector Store、Graph
请求级：DTO、TraceId、鉴权上下文
业务级：用户、会话、消息、文档元数据
```

进程级对象由 Spring Bean 或 FastAPI lifespan 创建一次；请求结束不销毁连接池和模型；
应用关闭时统一释放资源。

### 3. 不使用会怎样

- 每次聊天重新加载 BGE，延迟和内存不可接受。
- 每次 Java 调 Python 都新建 HTTP Client，连接无法复用。
- 自动重试 POST 聊天，可能重复扣费或生成两条助手消息。
- 没有 TraceId 时，只能分别翻 Java 和 Python 日志猜测同一次请求。

### 4. 本项目代码会如何使用

- Java `AgentClient` 是单例 Spring Bean，复用连接池。
- Python lifespan 创建一次 `AgentGraphBuilder`、Embedding 和 Vector Store。
- `X-Trace-Id` 从 Java 传到 Python并回传。
- 聊天 POST 默认不做业务级自动重试；只有确认请求未发送或具备幂等保护时才有限重试。
- 连接超时与读取超时分开配置，不能使用无限等待。

### 5. 一个简单类比

打印店不会每打印一页就买一台打印机；打印机是店级资源，订单是请求级数据，客户档案是
业务数据。卡纸后是否重打还要先确认上一份是否已经打印完成。

### 6. 容易踩的坑

- 把“Spring 默认单例”理解成类中可以保存当前用户字段。
- Python 全局单例没有并发保护，却同时重建和查询索引。
- 连接超时和读取超时设置成同一个极短数值。
- 对所有 5xx 无条件重试。
- TraceId 只写 Java 日志，不传给 Python。

### 7. 当前阶段需要掌握到什么深度

能说明为什么四个对象应该复用：

- LLM Client：复用连接与配置；
- Embedding Model：加载昂贵、占用大量内存；
- Vector Store：避免重复反序列化和状态不一致；
- HTTP Client：复用连接池，集中配置超时和容错。

具体 Resilience4j 参数、Redis TTL 和错误码在 Phase 2 冻结。

### 8. 5～10 分钟练习

画出一次聊天的时间预算：

```text
浏览器 → Java → Python → DeepSeek → Python → Java → 浏览器
```

标出连接超时、读取超时和总超时。然后回答：为什么“Python 已收到请求并调用 DeepSeek，
但 Java 在 55 秒时超时”的场景不能直接无条件重试？因为第一次调用可能仍在执行，
第二次调用会产生重复成本和不一致结果。
