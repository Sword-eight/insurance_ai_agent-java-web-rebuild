# Phase 11 Knowledge Summary

- TraceId 贯通 Vue/Java/Python 的观测链，但不承担幂等。
- Java request/client 与 Python request 日志使用稳定键值字段，默认不记录载荷和凭据。
- chat 3s/60s、knowledge 3s/300s、health 1s/2s、DeepSeek 3s/50s。
- DeepSeek 自动重试为 0；knowledge 零重试；chat 仅连接未建立时同 requestId 最多一次。
- Agent/Knowledge 熔断隔离：20/10/50%/30s/3；网络、超时和 5xx 计失败，确定性 4xx 不计。
- Java/Python liveness 与依赖解耦，readiness 才检查 AI Runtime。
- Vue 稳定展示 401、429、502、503 与 UNKNOWN，不维护后端业务状态机。
- 三端全量测试和构建通过；真实线上 DeepSeek 调用及最终浏览器端到端留给 Phase 12。
