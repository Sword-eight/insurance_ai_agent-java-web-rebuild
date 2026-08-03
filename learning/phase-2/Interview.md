# Phase 2 Interview：契约、幂等与存储设计

## 1. 公共 API 和 Java→Python 内部 API 为什么要分开？

推荐口述回答：公共 API 面向 Web 业务，返回 conversationId、messageId 和稳定业务错误；内部 API 面向 AI 能力，传 requestId、有限历史并返回内部成功或错误结构。分开以后 Python 的 LangChain 类型、Tool 参数和异常不会泄露，Java 也能独立演进鉴权与持久化。

可能追问：为什么不能共用一个 DTO？

追问回答：同一个类会同时承担外部兼容性、数据库映射和 Python 协议，任一侧修改都会传导。当前设计明确 DTO、VO、Entity 和 Python Schema 分离。

常见错误回答：因为微服务都必须多写一套 DTO。

## 2. traceId、requestId 和 Idempotency-Key 有什么区别？

推荐口述回答：traceId 关联一次调用链日志；Idempotency-Key 由客户端标识同一次提交意图；requestId 由 Java 首次接收后生成，标识跨 Java/Python 的业务执行。相同业务重试可能产生新 trace，但应复用 Idempotency-Key，并返回同一个 requestId 对应结果。

可能追问：为什么不能拿 traceId 做幂等？

追问回答：网关或客户端重试可能生成新 trace，同一个 trace 也可能包含多个下游调用。日志标识没有业务唯一性承诺。

常见错误回答：三个都是 UUID，任选一个就行。

## 3. 为什么聊天请求要单独建 `iap_chat_request` 表？

推荐口述回答：消息表保存真实 user/assistant 内容，请求表保存一次执行的幂等键、摘要和 RECEIVED、PROCESSING、SUCCEEDED、FAILED、UNKNOWN 状态。读取超时时没有可靠助手消息，但必须记录结果未知，分表能避免伪造占位回答，也让幂等约束清晰。

可能追问：这是不是过度设计？

追问回答：不是通用任务表，只服务同步聊天的防重和结果状态。没有它就无法可靠表达读取超时和复用原结果，这是当前真实需求。

常见错误回答：为了符合 DDD，所以请求和消息必须分表。

## 4. 为什么不能把调用 Python 放在一个数据库事务里？

推荐口述回答：聊天可能等待 60 秒，索引可能等待 300 秒。远程调用期间持有事务会长期占用连接和锁。当前用两个短事务：先持久化请求和用户消息，再事务外调用 Python，最后短事务写助手消息或失败/未知状态。

可能追问：两个事务之间失败怎么办？

追问回答：状态机就是恢复依据。PROCESSING 可被监控，明确失败写 FAILED，读取超时写 UNKNOWN；同幂等键不会新建第二次执行。

常见错误回答：加 `@Transactional` 包住整个 Service 最安全。

## 5. 为什么读取超时不能自动重试聊天 POST？

推荐口述回答：读取超时只说明 Java 没拿到响应，Python 可能已经调用 DeepSeek 或 Tool。自动重发会造成重复费用、副作用和消息。Java 应把原 requestId 标记 UNKNOWN，不写助手消息，也不发第二次 POST。

可能追问：什么时候允许一次重试？

追问回答：只有 HTTP Client 能明确确认请求没有送达时，复用同一 requestId、且总时间预算允许，最多一次连接级重试；参数错误、读取超时和明确业务失败都不重试。

常见错误回答：所有网络错误都交给 Resilience4j 重试三次。

## 6. Redis 和 MySQL 如何共同实现幂等？

推荐口述回答：MySQL 在 `(user_id,idempotency_key)` 上有唯一约束，是最终防重；Redis 只缓存 requestId、requestHash 和状态 24 小时。缓存未命中或故障时查 MySQL，所以 Redis 丢失不会重复调用 Python。

可能追问：为什么不用 Redis SETNX 就够了？

追问回答：Key 会过期、Redis 可能丢数据或故障切换，SETNX 不能承担长期业务事实。数据库唯一约束还能处理并发“先查后写”竞争。

常见错误回答：Redis 更快，所以幂等只放 Redis。

## 7. 为什么有限历史是 10 条、12000 字符？

推荐口述回答：当前 Python 配置是最大 5 轮，Phase 2 因此冻结最多 5 个 user/assistant 轮次，也就是 10 条，同时加 12000 字符总预算。这样请求大小和模型上下文可控，Python 也不需要回查 MySQL。

可能追问：这和 `safe_truncate_messages()` 有何不同？

追问回答：HTTP 裁剪只传业务 user/assistant 历史；Graph 内部函数还要保证 AI tool_calls 和 ToolMessage 不被切断。两者处理不同层的消息结构。

常见错误回答：Redis 里有多少历史就全部发给模型。

## 8. 为什么近期消息缓存采用写后删除？

推荐口述回答：消息先提交 MySQL，再删除会话近期缓存，下次读取回源最新 10 条。相比并发更新 JSON 数组，写后删除更容易避免覆盖和乱序，而且缓存失败不会回滚业务事实。

可能追问：删除失败怎么办？

追问回答：记录指标，读取侧校验并最终由 30 分钟 TTL 修复。若一致性要求提高，可在后续基于真实问题改进，但不引入 MQ。

常见错误回答：先删缓存再写数据库就绝对一致。

## 9. Redis 故障时为什么不同用例有不同降级？

推荐口述回答：消息缓存和幂等加速都能回源 MySQL，所以 fail-open 到数据库；聊天限流保护付费 LLM 成本，Redis 不可用时 fail-closed 返回 503。降级要按业务风险设计，不能一刀切。

可能追问：固定窗口有什么缺点？

追问回答：窗口边界可能短时双倍突发。但 v1 每用户 20 次/分钟足够简单可测，真实压测后再决定滑动窗口或令牌桶。

常见错误回答：Redis 挂了所有功能都必须直接报错。

## 10. Python 的 10 分钟 requestId 登记表会不会成为第二事实源？

推荐口述回答：不会。它只在单进程短时间内防止相同内部 requestId 重复执行，容量和 TTL 都有限；不保存完整长期会话。Java MySQL 仍保存完整消息和 durable request 状态，Python 重启后 Java 也不会重试 UNKNOWN 请求。

可能追问：多实例时怎么办？

追问回答：进程内登记表不能跨实例一致防重，这是 v1 限制。需要多实例时必须单独设计共享执行去重，但不能让 Python 接管 Java 业务数据库。

常见错误回答：有了 Python 内存缓存，Java 就不需要幂等表。

