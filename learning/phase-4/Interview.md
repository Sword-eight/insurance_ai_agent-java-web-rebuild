# Phase 4 Interview Q&A

## 1. 为什么用 lifespan，而不是在 Router 中初始化模型？

模型、Embedding、Graph 和索引都属于进程级昂贵资源。Router 内初始化会把启动成本放大到每个请求，还会产生多份 checkpointer 和不一致状态。lifespan 是 Composition Root：启动创建一次，请求只取依赖，关闭集中释放。

## 2. 初始化失败时为什么 live=200、ready=503？

liveness 只判断进程和 HTTP 事件循环是否存活；readiness 判断实例能否接收 AI 流量。两者分离后，平台不会因外部模型暂时不可用而反复重启健康进程，同时负载均衡不会把业务请求送给未就绪实例。

## 3. 为什么 history 和 LangGraph checkpoint 不能同时长期累加？

Java 传入的有限 history 是本次生产上下文。若再复用 session checkpoint，上一请求的历史和当前消息会重复出现。Phase 4 用 requestId 隔离执行，结束即清理 checkpoint；MySQL 仍是完整聊天历史唯一长期事实源。

## 4. 进程内 requestId 登记表解决了什么？

它防御单实例内的短时重复内部调用：进行中快速冲突、完成结果复用、不同内容冲突。它不能跨实例、不能跨重启、不能解决 Java 读取超时后的 UNKNOWN 对账，因此不能替代 Java/MySQL 幂等记录。

## 5. 为什么 TTL 还需要 execution lease？

旧调用可能运行超过 TTL。若 TTL 后新调用复用同一 requestId，旧调用迟到时不能把新调用的 IN_PROGRESS 状态改成自己的结果。每次注册生成 lease，终态转换必须匹配 lease，从而阻止 ABA/迟到写覆盖。

## 6. 为什么不返回固定兜底回答？

HTTP 200 和 answer 会被 Java 当成真实 AI 结果持久化。固定“抱歉”会把依赖故障伪装成功，破坏状态、指标和重试判断。无有效 AIMessage 时应返回稳定错误。

## 7. 为什么 Phase 4 不开放当前 rebuild？

现有 `KnowledgeService.rebuild()` 是 delete-then-build，且没有检索并发保护；上传链也缺大小、MIME、PDF 签名、哈希和受控落盘。直接暴露会引入索引损坏与数据风险，所以留到 Phase 10。

## 8. 为什么 Router 手动取得 AgentFacade？

FastAPI 会在 body 校验前解析普通依赖。Runtime 不可用时，这会让非法 body 错误地返回 503。聊天 Router 在已验证的 handler 内取得 Facade，仍保持 Router→Facade 边界，同时保证冻结契约的非法请求稳定返回 400。
