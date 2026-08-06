# Phase 5 Call Graph

## 1. 正常公共请求

```text
HTTP request
→ TraceIdFilter
   → validate X-Trace-Id or generate UUID
   → request attribute + response header + MDC
→ DispatcherServlet
→ Bean Validation
→ Controller（当前仅 test-only）
→ ApiResponse.success(data, current traceId)
→ Jackson serializes UTC Instant
→ HTTP 2xx + Envelope
→ TraceIdFilter finally removes MDC traceId
```

生产代码当前没有业务 Controller；Phase 6 才增加 ChatController。

## 2. 参数/JSON 错误

```text
TraceIdFilter
→ Spring MVC binding / @Valid
→ MethodArgumentNotValidException or HttpMessageNotReadableException
→ GlobalExceptionHandler
→ 400 + ApiResponse.failure(VALIDATION_ERROR)
→ Controller method not invoked
→ Filter clears MDC
```

## 3. 业务异常

```text
Controller → future Service
→ throw BusinessException(ErrorCode, safeMessage)
→ GlobalExceptionHandler
→ ErrorCode.httpStatus + frozen code + data=null
```

## 4. 未知异常

```text
unexpected Exception
→ GlobalExceptionHandler
   → log(traceId, exception type)          # no exception message
   → 500 INTERNAL_ERROR / safe message
→ response header traceId == body traceId
→ Filter finally clears MDC
```

## 5. OpenAPI

```text
GET /v3/api-docs
or /v3/api-docs/public-v1
→ springdoc global paths-to-match=/api/v1/**
→ GroupedOpenApi public-v1 also matches /api/v1/**
→ OpenAPI JSON contains only Java public paths
```

## 6. 下一阶段才允许的业务链

```text
ChatController → ChatService → AgentClient → Python /internal/v1/agent/chat
```

Phase 5 没有实现或测试该端到端调用。
