# Phase 3 MiniCourse：双服务 Skeleton 与契约边界

> 目标：建立可构建、可启动、可验证的 Java/Python 双服务骨架，同时明确证明业务链尚未接入。
> 本阶段只学习 4 个面试必需概念；FastAPI 业务包装属于 Phase 4，Spring Boot 公共基础能力属于 Phase 5。

## 概念 1：Skeleton 不是“空项目”，也不是“提前实现”

Skeleton 的职责是验证工程边界：目录能被工具识别、应用入口能加载、接口签名符合冻结契约、
占位行为不会被误认为真实业务。它不调用 DeepSeek、Embedding、FAISS、MySQL 或 Redis。

本项目的边界是：

```text
Phase 3：入口 + Schema/DTO + Client/Facade 接口 + 明确占位响应 + Smoke Test
Phase 4：FastAPI lifespan 和现有 Python 对象图的最小包装
Phase 5：Spring Boot 统一响应、校验、异常和 OpenAPI
Phase 6：ChatController → ChatService → AgentClient → Python
```

面试回答要点：Skeleton 用最小可执行证据降低后续集成风险；如果占位接口返回伪造答案，测试通过也只是在验证假功能。

## 概念 2：Contract-first 如何防止双服务各说各话

Phase 2 已冻结 HTTP 路径、Header、字段、长度、错误码和超时语义。Phase 3 的 Java DTO 与 Python
Pydantic Schema 必须从同一契约落地，但二者不是共享运行时对象：Java 负责调用方模型，Python
负责服务端校验模型。

以聊天请求为例，两端都必须表达：

```text
requestId：UUID
sessionId：UUID
message：trim 后 1～4000 字符
history：只允许 user/assistant，最多 10 条、总计不超过 12000 字符
```

面试回答要点：契约测试关注边界可观察行为，而不是要求 Java 与 Python 共享源码；这样可以独立部署、独立演进，并在漂移发生时尽早失败。

## 概念 3：依赖倒置与“接口先行”

Java 业务层未来只依赖 `AgentClient` / `KnowledgeClient` 接口，不依赖具体 HTTP 库；Python Router
未来只依赖 Application Facade，不直接操作 Graph、Retriever 或 FAISS。依赖方向保持：

```text
Java：Controller → Service → Client interface → HTTP adapter
Python：Router → Facade interface/implementation → graph/services
```

Phase 3 只建立端口与类型，Phase 4/6 再接真实适配器。这样做不是为了堆抽象，而是隔离两个真实变化点：HTTP 协议和 AI 核心实现。

面试回答要点：接口应有明确调用方；没有调用场景的 Repository、Factory、Command Bus 不属于本阶段。

## 概念 4：Composition Root 与生命周期边界

Composition Root 是集中创建和连接长生命周期对象的位置。Spring Boot 使用容器管理单例 Bean；
FastAPI 使用 lifespan 在进程启动时初始化资源。请求处理函数只能取得已初始化依赖，不能每次请求
重新加载模型或索引。

Phase 3 只验证入口和生命周期挂载点存在，不调用当前 `application.bootstrap.init_services()`；后者会
加载 Embedding、索引和 Graph，必须到 Phase 4 才能在离线替身和异常测试保护下接入。

面试回答要点：单例只表示创建次数，不自动保证线程安全；索引重建与检索并发仍属于 Phase 10 的独立问题。

## 5～10 分钟自测

1. 为什么 Phase 3 的占位聊天接口不能返回固定“AI 答案”？
2. Java DTO 和 Python Schema 字段相同，为什么仍不应共享同一个运行时模型？
3. Router 直接调用 FAISS 会破坏哪一层边界？
4. 为什么 Phase 3 Smoke Test 不应要求 API Key、BGE 模型或真实索引？
5. 如果 JDK/Maven 不可用，哪些结论可以给出，哪些构建结论必须保留为未验证？

