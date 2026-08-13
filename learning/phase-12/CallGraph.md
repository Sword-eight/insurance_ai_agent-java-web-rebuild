# Phase 12 Call Graph

## 浏览器正常聊天

```text
Chrome -> Vue /login -> Java AuthController -> MySQL
       -> Vue ChatView -> Java ChatController -> ChatService
       -> Redis 幂等状态 + MySQL request/message
       -> Java HttpAgentClient -- HTTP/TraceId --> FastAPI Agent router/facade
       -> Phase 12 deterministic graph double
       <- InternalEnvelope <- Java public envelope <- Vue answer
```

同一个 `Idempotency-Key` 重放时，Java 返回原结果；MySQL 只有一个 request、两条消息，Redis 保存
`SUCCEEDED` 且 TTL 在冻结范围内。

## 文档链路

```text
Chrome -> Vue DocumentsView -> Java DocumentController -> DocumentService
       -> Java 隔离文件目录 + MySQL document metadata
       -> Java KnowledgeClient -- multipart/SHA-256 --> FastAPI knowledge router/facade
       -> Phase 12 deterministic knowledge double + Python 隔离目录
       <- INDEXED <- Java 状态迁移 <- Vue “可检索”
```

## 异常链路

```text
PHASE12_UNAVAILABLE -> Python 503 -> Java FAILED -> Vue “AI 服务暂时不可用”
PHASE12_TIMEOUT     -> Python 延迟 -> Java 504/UNKNOWN -> Vue “结果暂时无法确认”
未登录访问文档页   -> Vue route guard -> /login?redirect=/documents
```

UNKNOWN 不自动重发；浏览器断言每次失败只有一条用户消息。

## 环境生命周期

```text
Start-Phase12E2E.ps1
  -> 验证 JAR/Python/Node/Vite/端口
  -> 临时 MySQL + Redis
  -> Python readiness -> Java readiness -> Vue 200
  -> state.json 记录本轮精确资源

Stop-Phase12E2E.ps1
  -> 校验 workspace、PID executable、容器名与 Phase 标签
  -> 只停止本轮资源 -> 保留 ignored logs 供诊断
```
