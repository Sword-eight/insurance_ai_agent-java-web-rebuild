# Phase 11 Learning Kit

## 一句话讲清设计

用 TraceId 和结构化日志看见一次跨服务调用，用冻结超时和两个独立熔断器限制故障扩散，同时坚持
“未知结果不重试”，避免观测与容错机制制造重复消息或重复索引。

## 最值得学习的核心文件

### `ResilientAgentClient`

- 位置：ChatService 与原始 HTTP Agent 适配器之间。
- 输入/输出：同一 `AgentChatRequest + traceId` → Python 响应或稳定 Client 异常。
- 依赖：`HttpAgentClient`、独立 Agent `CircuitBreaker`。
- 面试点：为什么只对连接未建立重试，且必须复用 requestId。
- 小修改：增加状态迁移事件日志，不要记录 message。

### `AiResilienceConfig`

- 位置：Java 容错策略配置边界。
- 输入/输出：冻结参数和异常分类 → 两个隔离熔断实例。
- 依赖：Resilience4j。
- 面试点：确定性 4xx 为什么不计失败、为什么不能共享 breaker。
- 小修改：把配置暴露到受控指标，不允许请求维度标签造成高基数。

### `PythonReadinessHealthIndicator`

- 位置：Java Actuator readiness 贡献者。
- 输入/输出：短时 Python health GET → UP/DOWN。
- 依赖：专用 1s/2s RestClient、InternalEnvelope、TraceId。
- 面试点：为什么 liveness 独立、readiness 为什么最多再查一次。
- 小修改：补 TraceId 不匹配和错误 status 的测试。

### `api/main.py` 与 `utils/trace_context.py`

- 位置：Python HTTP 请求生命周期入口。
- 输入/输出：X-Trace-Id → ContextVar、响应头、结构化请求日志。
- 依赖：FastAPI middleware、ContextVar、统一异常 Envelope。
- 面试点：异步上下文隔离与 finally reset。
- 小修改：编写两个并发请求的 TraceId 隔离测试。

### `services/llm_factory.py`

- 位置：LangGraph 到 DeepSeek 的模型构造边界。
- 输入/输出：环境配置 → ChatOpenAI（connect 3s/read 50s/retry 0）。
- 依赖：httpx、langchain-openai。
- 面试点：SDK 隐式重试如何破坏总预算和副作用语义。
- 小修改：为非法环境变量补启动失败测试。

### `web-client/src/api/http.ts`

- 位置：Vue 的唯一 HTTP 错误翻译层。
- 输入/输出：Axios/Java Envelope → 稳定用户提示和 TraceId。
- 依赖：Axios、登录 session。
- 面试点：为什么前端不自动重发 UNKNOWN，也不复制后端状态机。
- 小修改：从 Axios 429 响应完整验证 Retry-After 解析。

## 推荐讲解顺序

1. 先区分 TraceId、requestId 与 Idempotency-Key。
2. 用超时后的 UNKNOWN 解释为何写请求不能盲目重试。
3. 再讲双熔断器的故障隔离和失败分类。
4. 对比 liveness/readiness，说明运维行为。
5. 最后展示三端日志和 Vue 稳定降级，以及真实测试证据。

## 验收结论

四维审计为 PASS with WARNING，无未解决 ERROR。证据与限制见 [Audit](./Audit.md)，真实调用链见
[CallGraph](./CallGraph.md)，复习题见 [Interview](./Interview.md)。本阶段未提交或推送，等待用户决定。
