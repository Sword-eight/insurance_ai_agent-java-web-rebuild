# Phase 11 MiniCourse：可观测性与容错

> 状态：开发前必修
> 目标：能用 TraceId、结构化日志、超时预算、熔断器和健康探针解释一次故障，同时保证聊天与索引不会被自动重复执行。

## 1. TraceId 是日志关联键，不是业务幂等键

Java 公共入口接受合法 `X-Trace-Id`，否则生成新值并写入 MDC；Java 调 Python 时传递同一值。
Python 在请求上下文中保存并回传它，使两端日志可以按一个字段检索。

```text
Vue -> Java TraceIdFilter/MDC -> AgentClient/KnowledgeClient
     -> Python middleware/context -> response header + envelope
```

TraceId 只能回答“这些日志属于哪条调用链”，不能证明“这是同一次业务意图”。聊天防重仍依赖
`Idempotency-Key + requestId`，文档索引仍依赖 Java 的文档/request 状态。

## 2. 结构化日志要可查询，也要默认脱敏

结构化日志使用稳定字段，例如：

```text
event, service, traceId, requestId, method, path, status, durationMs, errorCode
```

字段名稳定后，可以按 `traceId` 串联请求，按 `errorCode` 聚合故障，按 `durationMs` 定位慢调用。
日志不得包含 JWT、密码、API Key、完整聊天消息、PDF 内容、SQL、堆栈响应或本机绝对路径。

异常堆栈只留在服务端受控日志；公共响应继续只返回稳定错误码、TraceId 和安全摘要。

## 3. 超时是预算，不能用重试把预算翻倍

冻结预算是：

- Java→Python chat：连接 3 秒、读取 60 秒；
- Java→Python knowledge：连接 3 秒、读取 300 秒；
- Java→Python health：连接 1 秒、读取 2 秒；
- Python→DeepSeek：连接 3 秒、读取 50 秒、SDK 自动重试 0 次。

读取超时只说明 Java 没拿到结果，Python 可能已经执行。聊天或索引 POST 因此保持零自动重试，
写 `UNKNOWN` 后停止。只有只读 health GET 可以在短预算内再试一次。

## 4. 熔断器保护故障域，不替代超时

Agent 与 Knowledge 使用独立熔断器：窗口 20、至少 10 次、失败率 50%、打开 30 秒、半开允许
3 次。网络错误、超时和上游 5xx 计入失败；参数、权限、冲突等确定性 4xx 不计入。

```text
CLOSED --失败率达到阈值--> OPEN --30s--> HALF_OPEN
   ^                                      |
   `---------- 探测恢复成功 -------------'
```

OPEN 时 Java 快速返回 `AI_SERVICE_UNAVAILABLE`，不会调用 Python，也不会产生第二条消息或第二次
索引。熔断器只是拒绝新的下游调用；它不能取消已经执行中的 Python Tool，也不能恢复 UNKNOWN。

## 5. Liveness、Readiness 与前端降级展示

Liveness 只回答“进程是否活着”，不能因为模型或索引暂时不可用就让进程被反复重启。Readiness
回答“现在是否可接收 AI 请求”，可检查 Python Runtime，并在失败时让 Java readiness 变为 DOWN。

Vue 不实现熔断或状态机，只稳定展示：

- 401：清理登录态并跳转；
- 429：提示限流和可用的 `Retry-After`；
- 502：下游明确执行失败；
- 503：依赖不可用或熔断打开；
- 504 / `UNKNOWN`：结果暂时无法确认，绝不自动重发。

## 快速自测

1. 为什么同一个业务重试可以有不同 TraceId，却必须复用 Idempotency-Key？
2. 哪些日志字段可以记录，哪些请求内容必须默认脱敏？
3. 为什么读取超时不能自动重试聊天或 PDF 索引？
4. 为什么 Agent 与 Knowledge 不能共用一个熔断器？
5. Python Runtime 初始化失败时，liveness 和 readiness 应分别返回什么？
