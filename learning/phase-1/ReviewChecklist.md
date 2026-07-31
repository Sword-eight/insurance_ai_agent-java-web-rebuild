# Phase 1 Review Checklist：正式冻结检查记录

## 判定规则

- **PASS**：证据完整、与源码及 Architecture 一致，可以冻结该检查项。
- **WARNING**：目标清楚但实现或精确契约属于后续 Phase；必须记录，不阻止本阶段 Review。
- **ERROR**：边界矛盾、把规划写成已实现、形成双重事实源、跨层调用或引入未批准组件；解决前不得冻结。

## A. 事实与状态

- [ ] 明确写出当前入口是 Streamlit `app.py`。
- [ ] 明确 `graph/`、`tools/`、`services/`、`rag/` 当前已存在。
- [ ] Java Backend、FastAPI Router、Facade 均标注为目标设计、尚未实现。
- [ ] 没有把规划中的运行结果、测试或性能写成已验证事实。

## B. Java/Python 边界

- [ ] Web Client 只访问 Java，Java 通过内部 HTTP 调 Python。
- [ ] Java 负责用户、权限、会话、消息、文档元数据、MySQL 和 Redis。
- [ ] Python 负责 Agent、Tool、RAG、Embedding、FAISS 和 DeepSeek。
- [ ] Java 不读取 FAISS，Python 不访问 Java 业务 MySQL/Redis。
- [ ] Controller 不直接调用 Mapper 或 Python Client。

## C. 会话与存储

- [ ] MySQL 是完整聊天记录唯一长期事实来源。
- [ ] Redis 只保存近期消息缓存、限流、幂等标记等短期数据。
- [ ] Java 调 Python 传 `sessionId`、当前 message 和必要有限 history。
- [ ] `InMemorySaver` 只用于开发/测试，不与 Java 消息库形成双重事实源。
- [ ] 持久化 LangGraph Checkpointer 被标记为需单独架构变更。
- [ ] MySQL、Redis、FAISS 的所有权和可恢复性明确。

## D. Python 调用关系

- [ ] Graph 被描述为 Agent/Router 编排者。
- [ ] Tools 仅在 `tool_calls` 存在时执行。
- [ ] Tools 调 Services；Services 只在需要检索时调用 RAG。
- [ ] Graph 直接调用 DeepSeek；保费 Tool 不被错误画成经过 RAG。
- [ ] bootstrap 只装配长生命周期对象，不出现在每次请求业务链。
- [ ] Router 只处理 HTTP，Facade 负责协议转换和调用现有 Graph/Service。

## E. 可靠性与知识库

- [ ] POST 聊天明确禁止无条件重试。
- [ ] 连接失败与读取超时采用不同处理原则。
- [ ] requestId/幂等键用于防止重复消息和重复执行。
- [ ] 超时、重试、熔断不会被描述为可自动取消 Python Tool。
- [ ] 上传链包含 Controller、Service、MySQL 元数据、KnowledgeClient、Python、切分、Embedding、FAISS。
- [ ] PDF 原文件、元数据、索引业务状态和 FAISS 数据所有权明确。

## F. 过度设计与源码一致性

- [ ] 未引入 MQ、Worker、Nacos、Kubernetes、完整 DDD 或分布式事务。
- [ ] 未为统一 Java 风格移动/重命名 Python 核心目录。
- [ ] 文档使用的当前类和函数能在源码定位。
- [ ] 所有 WARNING 都注明后续 Phase，没有借机实现。
- [ ] 当前没有未解决、阻止正式冻结的 ERROR。

## 当前候选审计快照

- 功能：PASS
- 架构：PASS
- 设计：PASS
- 生命周期：WARNING（FastAPI lifespan、索引并发等尚未实现）
- ERROR：无。用户最终 Review 已通过，`docs/ARCHITECTURE.md v1.0` 已正式冻结。
