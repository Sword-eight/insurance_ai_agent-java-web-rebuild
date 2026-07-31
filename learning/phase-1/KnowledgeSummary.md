# Phase 1 Knowledge Summary

## 总体架构

```text
Web Client
→ Java Spring Boot：业务、鉴权、持久化、统一 API
→ Internal HTTP
→ Python FastAPI：HTTP 薄包装
→ 现有 LangGraph / Tools / Services / RAG
```

当前只有 Streamlit Python 应用；Java 和 FastAPI 是目标设计，尚未实现。

## Java 职责

用户、JWT、资源归属、完整会话消息、文档元数据、MySQL、Redis、文件接入、统一异常、
TraceId，以及通过 `AgentClient` / `KnowledgeClient` 编排 Python。

## Python 职责

LangGraph Agent/Router、条件 Tool Calling、Retrieval/Knowledge/Premium Service、
LangChain/LlamaIndex、BGE Embedding、FAISS、DeepSeek 和离线 LoRA。

## 聊天调用链

```text
Web → ChatController → ChatService → AgentClient
→ FastAPI Router → Chat Facade → Graph → DeepSeek
                                   └─[tool_calls]→ Tool → Service → [按需] RAG/FAISS
```

Java 传 `requestId + sessionId + message + finite history`；MySQL 保存完整记录。

## 知识库调用链

```text
Web → DocumentController → DocumentService
→ Java 保存 PDF + MySQL 元数据
→ KnowledgeClient → FastAPI → Knowledge Facade
→ KnowledgeService → Split → BGE → FAISS
→ Java 更新索引业务状态
```

## 三种存储职责

| 存储 | 职责 |
|---|---|
| MySQL | 用户、完整消息、文档元数据和索引业务状态；唯一业务事实源 |
| Redis | 近期消息缓存、限流、幂等标记等可过期短期数据 |
| FAISS | Python 生成的派生向量索引，可由受控文档重建 |

## 五条最重要规则

1. `Controller → Service → Client/Mapper`，禁止 Controller 直连 Mapper/Python。
2. Java 不读取 FAISS，Python 不访问业务 MySQL/Redis。
3. Graph 按条件调用 Tools，Services 只按需进入 RAG；不是固定线性链。
4. MySQL 是完整消息唯一长期事实源，`InMemorySaver` 仅开发/测试。
5. 聊天 POST 不无条件重试；连接失败和读取超时必须区分，并复用 requestId。

## 当前三个限制

1. Java Backend 和 FastAPI 尚未实现，当前无法做双服务端到端验证。
2. 同步 LLM 调用延迟较长，精确错误码、历史上限和幂等存储仍待 Phase 2。
3. 上传安全、索引并发/原子切换和 FAISS 信任边界仍待后续实现。
