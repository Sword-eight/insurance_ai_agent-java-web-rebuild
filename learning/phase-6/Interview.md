# Phase 6 Interview Q&A

## 1. 为什么 Controller 不直接调用 AgentClient？

Controller 只负责 HTTP。聊天的稳定 ID、响应校验、来源映射和错误翻译都是用例规则，应放在 Service。Phase 7 增加会话归属和事务时，只扩展 Service/Mapper，不需要改写 HTTP 边界。

## 2. 为什么公共 DTO 和内部 DTO 要分开？

它们服务不同调用方。公共请求是 `conversationId + message`，内部请求还需要 `requestId/history`；分离能避免向 Web 泄露 Python 契约，也允许两侧独立演进。

## 3. 为什么读取超时不能直接自动重试？

读取超时时连接已经建立，Python 可能仍在执行 Graph。自动重试可能重复调用模型或工具。当前设计返回 504，由稳定 requestId 和后续持久幂等能力处理不确定性。

## 4. TraceId、Idempotency-Key、requestId 有什么区别？

TraceId 用于观察一次调用链；Idempotency-Key 表达客户端的提交意图；requestId 标识一次内部业务执行。三者生命周期和唯一性语义不同，不能混用。

## 5. Phase 6 的幂等为什么不算生产级持久幂等？

Java 只稳定生成 requestId，Python `RequestRegistry` 只保存在当前进程内存。服务重启或多实例后没有共享事实。生产级兜底需要 Phase 7 的数据库唯一约束和状态记录。

## 6. 为什么固定 HTTP/1.1？

JDK HttpClient 默认可能对明文端点尝试 h2c 升级。真实联调中 Uvicorn 收到 chunked 请求但 FastAPI body 缺失。固定 HTTP/1.1 消除升级协商差异，是当前内部链路的最小兼容配置。

## 7. `@AssertTrue` 为什么会影响 JSON？

Jackson 按 JavaBean 规则把 `isHistoryPaired()` 识别为布尔属性。即使它只用于 Bean Validation，也可能被序列化。`@JsonIgnore` 明确告诉 Jackson 不把校验访问器当契约字段。

## 8. MockMvc 和双进程 smoke 分别证明什么？

MockMvc 证明 Java Web、校验、Service 协作和公共 Envelope；Client 单测证明内部 JSON 和超时分类；双进程 smoke 才证明 Java JAR 与 FastAPI 能通过真实网络协议互通。它们互补，不能相互冒充。

## 9. 如何避免 Python 错误泄露给 Web？

Client 只提取稳定内部 code，丢弃内部 message；Service 将已知 code 映射到冻结公共 ErrorCode，未知 code 统一返回安全的 `AI_EXECUTION_FAILED`。
