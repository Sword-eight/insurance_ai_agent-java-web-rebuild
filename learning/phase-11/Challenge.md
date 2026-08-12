# Phase 11 Challenge

1. 为熔断器增加事件监听，只输出状态迁移，不记录请求内容；说明如何避免高基数指标。
2. 给 readiness 测试增加“200 但 TraceId 不匹配”和“200 但 status 非 UP”两个异常用例。
3. 用两个并发 FastAPI 请求验证 `ContextVar` TraceId 不串号，并确认请求结束后恢复为 `NONE`。
4. 给 Vue 增加一个 429 Axios envelope 用例，从真实响应头解析 `Retry-After`，不增加自动重试。
5. 设计 UNKNOWN 人工查询流程，但只写方案，不改变同步 v1 或引入 MQ。
