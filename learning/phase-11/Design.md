# Phase 11 Design

## 关键选择

1. Java 的 Agent 与 Knowledge 各自包装已有 HTTP Client，不改变 `Service -> Client -> Python`
   方向。熔断窗口为 20、最少 10 次、失败率 50%、打开 30 秒、半开 3 次。
2. 只在连接尚未建立的 `UNAVAILABLE` 上，对 chat 使用同一 request 对象最多补发一次；读取超时、
   delivery unknown 与所有 knowledge 写请求均不重试。
3. Java liveness 只包含 `livenessState`；readiness 加入 Python `/health/ready`，失败时最多再检查一次。
4. Python 使用 `ContextVar` 保存请求 TraceId，并在 middleware `finally` 中恢复 token，防止异步请求串号。
5. DeepSeek 使用连接 3 秒、读取 50 秒和 SDK `max_retries=0`；Vue 只翻译稳定错误，不复制后端状态机。

## 代价与边界

- 同步 chat/knowledge 仍占用请求线程，这是冻结 v1 设计，不引入 MQ 或异步 task。
- 熔断器只拒绝新调用，不能取消已经到达 Python 的 Tool，也不能自动解决 `UNKNOWN`。
- readiness 是同步短探测；它不携带模型、数据库或索引内部细节，避免健康响应泄露实现信息。
- 未新增数据库、Redis key、消息状态或索引格式；没有架构漂移。
