# Phase 5 Knowledge Summary

## 固定公共响应

```text
ApiResponse<T>(code, message, data, traceId, timestamp)
```

成功固定 `code=OK`、`message=success`；失败 `data=null`。HTTP status 保留 4xx/5xx 语义，不把错误伪装成 200。`Instant` 由 Jackson 序列化为 UTC ISO-8601。

## 错误边界

`ErrorCode` 只包含 Phase 2 冻结的 17 个公共错误码和 HTTP 映射。`BusinessException` 携带已知业务错误；`GlobalExceptionHandler` 统一处理：

```text
Validation / malformed JSON → 400 VALIDATION_ERROR
BusinessException           → frozen status + code
unsupported content type    → 415 UNSUPPORTED_MEDIA_TYPE
oversized multipart          → 413 FILE_TOO_LARGE
unexpected exception        → 500 INTERNAL_ERROR
```

未知异常只在日志中记录 traceId 和异常类型，不记录可能含敏感内容的 exception message。

## TraceId 生命周期

`TraceIdFilter` 是最高优先级、无请求字段的单例 Filter。合法 Header 原样复用；非法或缺失值使用无连字符 UUID。TraceId 同时进入 request attribute、响应 Header 与 MDC，调用链结束后只删除本 Filter 管理的 MDC key。

规则为 16～64 位 `[A-Za-z0-9_-]`。TraceId 不是用户身份、幂等键或数据库主键。

## OpenAPI 暴露面

Springdoc 使用 2.6.0 API-only starter。配置同时使用：

- 全局 `springdoc.paths-to-match=/api/v1/**`；
- `GroupedOpenApi public-v1` 的 `/api/v1/**` 匹配。

因此默认 `/v3/api-docs` 与 `/v3/api-docs/public-v1` 都不包含内部测试路径。项目没有引入 Swagger UI starter。

## 测试策略

生产代码没有为测试增加业务接口。测试通过 `@Import` 注册 test-only Controller，用 MockMvc 走完整 Filter → Validation → Controller/Advice → Jackson 链路，并直接解析 OpenAPI JSON 验证 paths。
