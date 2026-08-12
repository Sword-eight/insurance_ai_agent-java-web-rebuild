# Phase 11 Call Graph

## 聊天正常链路

```text
Vue ChatView
  -> Java TraceIdFilter (MDC + request log)
  -> ChatController -> ChatService
  -> ResilientAgentClient -> agentCircuitBreaker
  -> HttpAgentClient -- X-Trace-Id --> FastAPI middleware
  -> AgentFacade -> LangGraph -> LLM factory -> DeepSeek
  <- InternalEnvelope / X-Trace-Id
  <- Java public envelope -> Vue stable display
```

## 异常与防重复链路

```text
connect 未建立 -> UNAVAILABLE -> 同 requestId 最多重试一次
read timeout   -> TIMEOUT/UNKNOWN -> 不重试 -> Java UNKNOWN 语义
Python 5xx     -> UPSTREAM_FAILURE -> 计入 Agent breaker -> 公共 502/503/504
breaker OPEN   -> 快速 503，不调用 Python

Knowledge index timeout / 5xx / breaker OPEN
  -> ResilientKnowledgeClient 零重试
  -> DocumentService 维持 FAILED/UNKNOWN 冻结状态语义
```

## 健康链路

```text
GET /actuator/health/liveness -> livenessState -> UP（与 Python 无关）
GET /actuator/health/readiness
  -> PythonReadinessHealthIndicator
  -> GET /internal/v1/health/ready（最多两次检查）
  -> 验证 HTTP + Envelope + TraceId + data.status
```

## 日志关联

```text
Java request log / Client log -- traceId --> Python middleware / graph logs
```

两端均只记录路径、状态、耗时、错误码和业务标识，不记录聊天正文、PDF、JWT、密码或 API Key。
