# Phase 1 Interview：架构口述题

> 只讨论 Phase 1 架构。Java Controller、AgentClient 和 FastAPI Router 均是目标设计，尚未实现。

## 1. 为什么使用 Java + Python 双服务？

- **面试问题**：为什么不继续维护一个 Python 单体？
- **推荐口述回答**：当前 Python 已经有 LangGraph、Tool、双引擎 RAG、BGE 和 FAISS，重写没有收益；目标又需要用户、JWT、会话、消息、MySQL 和 Redis，所以让 Java 管业务系统，Python 管 AI，通过内部 HTTP 隔离变化。
- **可能追问**：代价是什么？
- **追问回答**：多一次网络调用，需要处理超时、TraceId、错误映射和部署；但职责与技术栈更清晰。
- **常见错误回答**：“Java 性能一定比 Python 高，所以所有后端都放 Java。”

## 2. Java 和 Python 的职责边界是什么？

- **面试问题**：如何判断一个需求放在哪一侧？
- **推荐口述回答**：用户、权限、完整会话消息、文档元数据、MySQL、Redis归 Java；Agent、Tool Calling、RAG、Embedding、FAISS、DeepSeek归 Python。判断标准是业务数据所有权和 AI 能力所有权，不是类名。
- **可能追问**：保费计算为什么还在 Python？
- **追问回答**：它当前是 Agent Tool 的内部能力，由 `PremiumCalculatorTool → PremiumService` 使用；没有独立 Java 业务需求时不迁移。
- **常见错误回答**：“所有业务逻辑都必须放 Java。”

## 3. Controller、Service、Client、Mapper 分别做什么？

- **面试问题**：请口述 Java 分层。
- **推荐口述回答**：Controller 只处理 HTTP 和校验；Service 管业务编排、归属和事务；Client 封装 Python HTTP、超时和错误；Mapper只访问 MySQL。固定方向是 `Controller → Service → Client/Mapper`。
- **可能追问**：为什么 Controller 不能直接调用 AgentClient？
- **追问回答**：聊天还要决定消息状态、权限和失败处理，这些是 Service 的业务职责；直接调用会把 Controller 变成 God Class。
- **常见错误回答**：“Controller 直接调 Client 少一层，代码更快。”

## 4. 为什么 Python 不直接访问业务 MySQL？

- **面试问题**：让 Python 查聊天历史不是更方便吗？
- **推荐口述回答**：MySQL 是 Java 业务事实源。Python 直连会复制 Entity、权限和事务规则，还要持有数据库凭据。Java 应传 `sessionId`、当前消息和必要有限历史，Python 只完成推理。
- **可能追问**：Python 重启后上下文怎么办？
- **追问回答**：完整记录仍在 MySQL；Java重新提供有限历史。当前 `InMemorySaver` 只用于开发/测试。
- **常见错误回答**：“Python 不能连接 MySQL。”

## 5. MySQL、Redis、FAISS 有什么区别？

- **面试问题**：为什么需要三种存储？
- **推荐口述回答**：MySQL 保存完整、可审计的业务事实；Redis 保存近期消息缓存、限流、幂等标记等可过期数据；FAISS 保存 Python 根据文档生成的向量索引。Redis/FAISS 丢失可恢复，MySQL 业务事实不能依赖它们替代。
- **可能追问**：FAISS 状态存哪里？
- **追问回答**：FAISS 文件和加载状态归 Python；面向用户的索引业务状态由 Java 写 MySQL。
- **常见错误回答**：“Redis 更快，可以代替 MySQL 保存所有消息。”

## 6. 如何避免 Java 与 LangGraph 双重保存会话状态？

- **面试问题**：LangGraph 已有 Checkpointer，为什么还要 MySQL？
- **推荐口述回答**：Checkpointer 是 Agent 执行状态，不是用户业务记录。第一版 MySQL 是完整消息唯一长期事实源，Java传有限历史；`InMemorySaver` 不进入生产事实链。未来持久化 Checkpointer 必须单独做架构变更和一致性设计。
- **可能追问**：Redis 是否是第二事实源？
- **追问回答**：不是。Redis只缓存近期消息，过期或丢失后从 MySQL 恢复。
- **常见错误回答**：“两边都存，定期同步即可。”

## 7. 为什么聊天 POST 不能无条件重试？

- **面试问题**：调用 Python 超时后为什么不直接重试一次？
- **推荐口述回答**：连接失败可确认请求未被接收时，复用同一 requestId 才可能有限重试；读取超时意味着 Python 可能已调用 DeepSeek 或 Tool，重试会重复成本和副作用。消息写入也必须以 requestId 幂等。
- **可能追问**：熔断能解决重复吗？
- **追问回答**：不能。熔断只快速拒绝新调用，超时只停止等待；幂等和“不对未知结果自动重试”才避免重复。
- **常见错误回答**：“所有 5xx 和超时都重试三次。”

## 8. 为什么当前不引入 MQ 或完整 DDD？

- **面试问题**：用了 MQ 不是更像企业项目吗？
- **推荐口述回答**：第一版只有两个服务且聊天同步，当前没有异步吞吐或复杂领域模型需求。MQ、Worker、聚合根和 Command Bus 会增加部署和调试成本，违反 YAGNI。真实需求出现后再演进更可信。
- **可能追问**：知识库构建很慢怎么办？
- **追问回答**：当前先完成安全同步基线；异步任务只作为后续演进，必须单独设计状态、幂等和失败恢复。
- **常见错误回答**：“校招项目技术栈越多越好。”

## 9. 当前架构有什么局限？

- **面试问题**：请主动评价这个设计。
- **推荐口述回答**：第一，Java/FastAPI 尚未实现，当前只有设计证据；第二，同步聊天受 LLM 延迟影响；第三，上传安全、索引重建并发和原子切换仍待后续实现；另外有限历史、错误码、幂等存储仍需 Phase 2 冻结。
- **可能追问**：为什么还认为架构可行？
- **追问回答**：它复用已验证的 Python 核心，边界小，能按 Phase 逐步验证，而且明确记录限制而没有伪造完成度。
- **常见错误回答**：“架构已经完善，没有明显缺点。”
