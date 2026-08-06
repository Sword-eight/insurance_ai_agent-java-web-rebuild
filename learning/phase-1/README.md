# Phase 1 Learning Kit：双服务架构设计

> 状态：Phase 1 已完成；配合已冻结主体边界并增补客户端规划的 `docs/ARCHITECTURE.md v1.1` 使用。
> 路线增补：Vue 正式客户端安排在 Phase 9.5/10.5；见 [学习总索引](../README.md)。

## 本阶段目标

Phase 1 只设计 Insurance AI Platform 的目标边界：

```text
Web Client → Java Spring Boot Backend → Python FastAPI AI Service
```

本阶段不创建 Java/FastAPI 工程，不迁移目录，不实现 API、数据库或 Redis。

## 先区分“当前”与“目标”

### Phase 1 冻结时的真实实现

- 正式在线入口是 `app.py` 启动的 Streamlit。
- `application/bootstrap.py` 手动装配 Python 对象。
- `graph/`、`tools/`、`services/`、`rag/`、DeepSeek 和 FAISS 调用已存在。
- Java Backend 和第一方 FastAPI Router 均不存在。

### Phase 1 目标架构

- Web Client 只访问 Java。
- Java 负责用户、权限、会话、完整消息、文档元数据、MySQL 和 Redis。
- Python 负责 Agent、Tool、RAG、Embedding、FAISS 和 LLM。
- Java 通过未来的 `AgentClient` / `KnowledgeClient` 调用未来的 FastAPI 内部接口。

目标类名用于解释未来职责，不能描述为仓库已经实现。

## 推荐阅读顺序

1. [MiniCourse.md](MiniCourse.md)
2. [Design.md](Design.md)
3. [CallGraph.md](CallGraph.md)
4. [Challenge.md](Challenge.md)
5. [Interview.md](Interview.md)
6. [KnowledgeSummary.md](KnowledgeSummary.md)
7. [ReviewChecklist.md](ReviewChecklist.md)

## 每份文档的用途

| 文档 | 用途 |
|---|---|
| `MiniCourse.md` | 从零理解服务边界、Java 分层、Python 薄包装和生命周期 |
| `Design.md` | 理解每项架构选择的原因、替代方案与代价 |
| `CallGraph.md` | 对照当前真实调用链与尚未实现的目标调用链 |
| `Challenge.md` | 用画图、判断、排错和口述练习检查理解 |
| `Interview.md` | 准备只围绕 Phase 1 架构的面试问答 |
| `KnowledgeSummary.md` | 阶段结束后快速复习的一页摘要 |
| `ReviewChecklist.md` | 正式冻结前执行一致性和过度设计检查 |

## 学习验收标准

完成学习后应能：

- 口述当前 Streamlit 调用链，并指出 Tool/RAG 是条件分支。
- 说明 Java 和 Python 各自拥有的数据与能力。
- 解释 `Controller → Service → Client/Mapper` 的依赖方向。
- 说明 MySQL、Redis、FAISS 为什么不能互相替代。
- 解释为什么 MySQL 是完整消息唯一长期事实来源。
- 说明 POST 聊天读取超时后为什么不能无条件重试。
- 画出目标聊天链和知识库上传链，并标注“尚未实现”节点。
- 指出当前方案至少三个限制，且不借机引入 MQ、DDD 或注册中心。

## Review 边界

Learning Kit 用于解释架构，不替代 `docs/ARCHITECTURE.md`。如两者冲突，以已冻结的
Architecture 为准，并先报告文档漂移。当前不得据此宣称 Java/FastAPI 功能可运行。
