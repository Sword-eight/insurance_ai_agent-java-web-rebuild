# Phase 11 Review Checklist

- [x] Java/Python TraceId 贯通且请求结束清理上下文。
- [x] 日志字段稳定，不记录聊天正文、PDF、JWT、密码或 API Key。
- [x] chat、knowledge、health 与 DeepSeek 超时符合冻结值。
- [x] DeepSeek、knowledge 和未知结果不自动重试。
- [x] chat 仅连接未建立时同 requestId 最多重试一次。
- [x] Agent/Knowledge 使用独立熔断器，5xx/网络/超时计失败，确定性 4xx 不计。
- [x] liveness 独立，readiness 校验 Python Envelope/TraceId/status 且最多重试一次。
- [x] Vue 稳定展示 401、429、502、503、UNKNOWN。
- [x] Java 89、Python 75、Vue 26 项测试全通过；Java/Vue 构建通过。
- [x] 无数据库、Redis、MQ、异步 task 或冻结架构变更。
- [ ] Phase 12 执行真实浏览器 Vue→Java→Python 与在线 DeepSeek 最终联调。
