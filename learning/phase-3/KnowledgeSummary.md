# Phase 3 Knowledge Summary

## 一句话架构

```text
Java Backend Skeleton --Client 端口--> Python FastAPI Skeleton --Facade 端口--> 未接入
```

Skeleton 的成功标准不是“能回答问题”，而是边界可构建、契约可验证、未实现能力诚实失败。

## 五个关键点

1. **契约先行**：Phase 2 的路径、UUID、长度、history 预算、TraceId 和错误 Envelope 被两端类型表达。
2. **端口隔离**：Java Service 未来依赖 `AgentClient`/`KnowledgeClient`；Python Router 依赖 Facade，不直接依赖 Graph/FAISS。
3. **生命周期挂载点**：FastAPI lifespan 当前只把 readiness 设为 false，Phase 4 才初始化进程级 AI 对象。
4. **显式不可用**：live 返回 200；ready 和业务调用返回稳定错误，不生成固定答案冒充 Agent。
5. **离线证据**：Python 39 个测试和 Java 5 个测试均真实执行，JAR 被真实生成。

## DTO 与领域对象为什么分开

Python Router 把 `AgentChatRequest` 转为 `AgentChatCommand`，避免 Application Facade 依赖 FastAPI/Pydantic。
Java Client DTO 表达跨服务协议，未来公共 Web DTO/VO 不应直接复用它们。这样公共 API、内部 API 和 AI 核心可以分别演进。

## live 与 ready 的区别

- live：进程和 HTTP 栈可响应；Phase 3 已实现。
- ready：模型、索引和 Facade 可处理 AI 请求；Phase 3 明确返回 503。

live 成功不能证明聊天可用，ready 失败也不代表进程已崩溃。

## 当前真实边界

- `api/` 没有导入 `graph/`、`tools/`、`services/`、`rag/` 或 `memory/`。
- Java 没有 Controller、Service、Mapper、MySQL、Redis 或具体 HTTP Client。
- `application/bootstrap.py` 没有被 FastAPI 调用。
- Streamlit 原入口保持不变。
