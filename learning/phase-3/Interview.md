# Phase 3 Interview：双服务 Skeleton 高频问答

## 1. 为什么先做 Skeleton，不直接打通聊天？

先独立验证构建工具、服务入口、序列化契约和生命周期边界。若直接接 LLM，网络、模型、索引和业务编排会同时进入故障面，难以判断问题来自工程还是 AI 链路。

## 2. Skeleton 与 Mock 有什么区别？

Skeleton 是生产工程结构和真实接口边界；Mock 是测试中的替身。本阶段的不可用 Facade 是明确占位实现，只负责阻止伪功能，不模拟 AI 成功结果。

## 3. 为什么 Java 只有 Client 接口，没有实现类？

Phase 3 验证依赖方向；真实 HTTP timeout、错误映射和容错属于 Phase 6/11。提前写实现会跨 Phase，也可能让 Service 语义尚未确定时固化错误适配器。

## 4. 为什么 Python Router 不直接调用 `AgentGraphBuilder`？

Router 只处理 HTTP。Facade 承担协议转换、有限历史和用例调用；Graph 保留 Agent/Tool 编排。如果 Router 直调 Graph，HTTP、生命周期和 AI 编排会耦合。

## 5. 为什么 history 必须是完整 user/assistant 对？

冻结契约要求最多 5 个完整轮次。奇数条或乱序历史可能把上一轮问题当成当前问题，也会造成 Java 与 Python 对上下文理解不一致。

## 6. TraceId、requestId、sessionId 有什么区别？

- TraceId：日志链路标识，不负责幂等；
- requestId：一次 AI 执行的业务标识；
- sessionId：本次 Graph 的会话关联标识，不授权 Python 回查业务库。

## 7. 为什么 live 成功但 ready 返回 503？

HTTP 进程已经可用，但 Phase 4 的模型、索引和真实 Facade 尚未初始化。这种区分可避免负载均衡把“进程活着”误判为“业务可接流量”。

## 8. 如何证明没有伪造 AI 功能？

有效 chat 请求先通过 Schema，再由不可用 Facade 返回 `AI_LLM_UNAVAILABLE`；响应中没有 answer。测试同时验证 status code、错误码和 answer 缺失。

## 9. 为什么 Java DTO 与 Python Schema 不共享代码？

它们属于独立进程和语言。共享运行时模型会制造构建耦合；正确做法是共享冻结契约，并用两端测试检测漂移。

## 10. 本阶段最重要的可验证证据是什么？

Python 全量 39 tests、Java 5 tests、Maven `BUILD SUCCESS`、可执行 JAR、真实 Uvicorn liveness 200，以及静态检查证明 HTTP Skeleton 未导入 AI 核心。
