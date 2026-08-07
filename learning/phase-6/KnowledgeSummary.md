# Phase 6 Knowledge Summary

## 同步聊天链

```text
POST /api/v1/chat/messages
→ ChatController
→ ChatService
→ HttpAgentClient
→ POST /internal/v1/agent/chat
→ Python AgentFacade / Graph
```

Controller 只处理公共 HTTP；Service 表达用例和错误语义；Client 隔离内部协议。公共 DTO/VO 与 Python DTO/Envelope 不能复用。

## 三类标识

- TraceId：链路观测，Java→Python 原样透传。
- Idempotency-Key：公共提交意图，由调用方提供。
- requestId：内部业务执行标识，由 Java 根据 Key 稳定生成。

Phase 6 的防重只在 Python 单进程内生效，不持久、不可跨重启。

## Client 规则

共享 JDK HTTP Client，3 秒连接超时、60 秒读取超时、无自动重试。连接失败、读取超时、内部拒绝和协议错误必须分类，不能全部变成 500。

当前 Java→Uvicorn 链路固定 HTTP/1.1。默认 h2c 升级曾导致 chunked 请求到达 FastAPI 时 body 缺失；固定版本后双进程链恢复。

## 严格契约

内部请求 JSON 只有：

```text
requestId, sessionId, message, history
```

Bean Validation 的 `is...` 方法需要 `@JsonIgnore`，否则 Jackson 可能把校验结果当作额外字段，而 Python `extra="forbid"` 会拒绝请求。

## 验证边界

33 个 Java 测试验证分层和映射，63 个 Python 测试验证原有服务，双进程 smoke 验证真实 HTTP。SmokeGraph 只替代模型/索引，不替代 FastAPI、AgentFacade 或 RequestRegistry，因此可以证明链路连通，但不能证明真实模型效果。
