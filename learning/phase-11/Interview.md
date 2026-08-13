# Phase 11 Interview

1. **TraceId 和 requestId 有什么区别？** TraceId 关联技术调用链；requestId/Idempotency-Key 才约束业务防重。
2. **为什么 chat 读取超时不能自动重试？** 请求可能已到 Python 并完成 Tool，重试会产生重复副作用。
3. **唯一允许的 chat 重试是什么？** 能证明连接未建立时，以同一 requestId 最多补发一次。
4. **为什么 Agent 与 Knowledge 分开熔断？** 两个故障域和耗时分布不同，共用会让索引故障拖垮聊天。
5. **哪些异常计入熔断？** 网络、超时、delivery unknown 与上游 5xx；确定性 4xx 和协议错误不计入。
6. **熔断与超时的关系？** 超时限制单次等待；熔断依据历史失败快速拒绝新调用，二者不能互相替代。
7. **liveness 为什么不依赖 Python？** 依赖故障不应导致 Java 被重启；readiness 才表示能否接收 AI 流量。
8. **ContextVar 为什么适合 FastAPI？** 它隔离异步任务的请求上下文，并可用 token 精确恢复先前值。
9. **前端为什么不自动重发 UNKNOWN？** UNKNOWN 表示执行结果不确定，自动重发可能重复消息或索引。
10. **如何避免日志泄密？** 使用字段白名单，只记标识、路径、状态、耗时、错误码，不记录载荷和凭据。
