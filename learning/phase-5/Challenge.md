# Phase 5 Challenges

## Challenge 1：错误双通道

分别为参数错误、幂等冲突、AI 连接失败和未知异常写出 HTTP status、业务 code、data 以及是否可自动重试。解释为什么不能只看其中一个通道。

## Challenge 2：校验分层

把下列规则放到 DTO 或 Service，并说明原因：消息最长 4000、conversationId 是 UUID、会话属于当前用户、Idempotency-Key 已用于不同消息、Python 返回超时。

## Challenge 3：MDC 串链

模拟同一线程连续处理两个请求：第一个带合法 TraceId，第二个不带。如果 Filter 没有 finally 清理，会发生什么？写出能证明清理成功的 MockMvc 断言。

## Challenge 4：异常脱敏

让测试 Controller 抛出 message 含 Windows 绝对路径和 token 字样的 RuntimeException，同时捕获 HTTP body 和日志事件。要求 body、日志均不包含敏感 message，但日志保留 traceId 和异常类型。

## Challenge 5：OpenAPI 漏出

同时注册 `/api/v1/demo` 与 `/internal/demo` 测试 Controller。验证 `/v3/api-docs` 和 `/v3/api-docs/public-v1` 的全部 path 都以 `/api/v1/` 开头。思考为什么只测分组入口不充分。

## Challenge 6：进入 Phase 6

设计 `ChatController → ChatService → AgentClient` 时，指出 ApiResponse、BusinessException 和 TraceId 各在哪一层使用；禁止 Controller 直接调用 AgentClient。
