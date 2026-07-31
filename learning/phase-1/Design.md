# Phase 1 Design：架构决策与取舍

> 本文解释“为什么这样设计”。目标定义以已冻结的 `docs/ARCHITECTURE.md v1.0` 为准。

## 1. 为什么拆成 Java Backend 与 Python AI Service

- **问题**：当前 Streamlit 进程同时承担 UI、会话和 AI；目标还需要用户、鉴权和持久化。
- **选择**：Java 承担业务后端，Python 暴露内部 AI 服务。
- **原因**：Java 适合展示 Spring MVC、事务、MySQL、Redis、JWT；现有 Python 已有真实 AI 链。
- **放弃的方案**：继续扩展 Python 单体；或把 RAG/LangGraph 重写为 Java。
- **代价或限制**：增加一次内部 HTTP 调用和跨服务错误处理，部署从一个进程变为两个进程。

## 2. 为什么业务数据归 Java

- **问题**：用户、会话、消息和文档状态若两边都保存，会出现双重事实来源。
- **选择**：MySQL 由 Java 独占，保存完整聊天记录和业务元数据。
- **原因**：鉴权、资源归属、消息事务和查询接口都由 Java 编排，所有权集中最容易保证一致。
- **放弃的方案**：Python 直连业务 MySQL；Java/Python 各自保存一份完整消息。
- **代价或限制**：Java 必须向 Python传递本次推理所需的有限历史，内部 DTO 更明确。

## 3. 为什么 LangGraph、RAG、Embedding、FAISS 留在 Python

- **问题**：AI 核心使用 LangGraph、LangChain、LlamaIndex、SentenceTransformer 和 FAISS。
- **选择**：全部保留在 Python，由 Java 只调用稳定 HTTP 契约。
- **原因**：避免复制 Prompt、Tool Schema、向量维度、切分规则和索引序列化细节。
- **放弃的方案**：Java 直接读 FAISS；Java 重写 Router 或 Retriever。
- **代价或限制**：Java 无法在进程内降级执行 AI，Python 不可用时必须明确快速失败。

## 4. 为什么 Controller 不能直接调用 Mapper

- **问题**：若 Controller 同时处理 HTTP、SQL 和事务，测试与复用都会变差。
- **选择**：固定 `Controller → Service → Mapper`。
- **原因**：Service 掌握业务规则、资源归属和事务边界；Controller 只处理 HTTP。
- **放弃的方案**：简单 CRUD 直接在 Controller 调 Mapper。
- **代价或限制**：即使早期功能简单，也需要保留一个薄 Service，但不增加无需求的 Domain Service。

## 5. 为什么 Controller 不能直接调用 Python

- **问题**：聊天要同时处理会话归属、消息状态、Python 超时和响应转换。
- **选择**：固定 `Controller → Service → AgentClient/KnowledgeClient`。
- **原因**：Service 决定业务结果；Client 只处理内部 HTTP、超时、错误映射和熔断。
- **放弃的方案**：Controller 注入 `AgentClient`；或 Mapper 调 Python。
- **代价或限制**：多一层调用，但可以分别单测 HTTP 参数、业务编排和远程 Client。

## 6. 为什么使用 AgentClient / KnowledgeClient

- **问题**：聊天和知识库操作的超时、幂等和失败语义不同。
- **选择**：用两个职责明确的 Client 隔离 Python 协议。
- **原因**：聊天读取时间长且不能盲目重试；知识库是文件和索引生命周期操作。
- **放弃的方案**：一个万能 `PythonClient`；在各 Service 中散落 HTTP 调用。
- **代价或限制**：配置和测试会多两组，但不会引入新的服务或框架。

## 7. 为什么第一版 Python 尽量无用户长期状态

- **问题**：现有 `InMemorySaver` 与未来 Java 消息库同时持久化会形成双重事实来源。
- **选择**：MySQL 是完整消息唯一长期事实源；Java传 `sessionId`、当前消息和有限历史。
- **原因**：Python 重启不影响业务记录，扩容时也不依赖某个进程内线程状态。
- **放弃的方案**：把 `InMemorySaver` 当生产会话库；立即引入持久化 LangGraph Checkpointer。
- **代价或限制**：每次内部请求携带有限历史；未来引入持久化 Checkpointer 必须单独变更架构。

## 8. 为什么 MySQL、Redis、FAISS 分工

- **问题**：三种存储的数据寿命、查询方式和一致性要求不同。
- **选择**：MySQL 存业务事实，Redis 存可过期短期数据，FAISS 存 Python 派生向量索引。
- **原因**：消息需要事务和审计；限流/近期缓存需要 TTL；向量相似度需要专用索引。
- **放弃的方案**：Redis 保存唯一聊天记录；MySQL 保存向量实现细节；Java 读取 FAISS。
- **代价或限制**：需要设计缓存失效和索引状态同步，但各存储职责可独立解释。

## 9. 为什么当前不引入 MQ、DDD、Nacos、Kubernetes

- **问题**：组件越多，学习和故障面越大，但第一版只有两个服务和同步聊天。
- **选择**：只使用 HTTP、简单分层、MySQL、Redis 和本地/受控文件。
- **原因**：当前没有异步吞吐、服务发现、多集群编排或复杂领域模型的真实需求。
- **放弃的方案**：预先加入 MQ Worker、注册中心、完整 DDD、分布式事务和 Kubernetes。
- **代价或限制**：长耗时索引任务第一版能力有限；出现真实规模需求后再独立演进。

## 10. 为什么 Router、Facade、bootstrap 分开

- **问题**：若 FastAPI Router 创建模型或 bootstrap 处理 HTTP，生命周期与协议会混在一起。
- **选择**：Router 只处理 HTTP；Facade 转换协议并调用 Graph/Service；bootstrap 只装配长生命周期对象。
- **原因**：Embedding、LLM Client、Vector Store 和 Graph 只初始化一次，请求逻辑可以独立测试。
- **放弃的方案**：每个请求调用 `init_services()`；Router 直接访问 Retriever/FAISS。
- **代价或限制**：未来需新增一个很薄的 Facade，但不得借此重写已有 AI 模块。

## 当前设计的局限与演进点

- Java Backend 和 FastAPI 尚未实现，当前只能验证架构一致性，不能做端到端验证。
- 同步聊天有较长读取时间；连接失败与读取超时必须采用不同重试策略。
- 上传安全、索引重建并发、原子切换和 FAISS 信任边界要在 Phase 10/11 实现。
- 有限历史的最大条数、错误码、幂等存储、DB 表和 Redis TTL 要到 Phase 2 冻结。
- 在线 LoRA 不在当前目标内；引入持久化 LangGraph Checkpointer也需要单独架构变更。
