# Phase 7 Interview Q&A

## 1. 为什么聊天需要 request 表，不能只存消息？

消息表达内容，request 表达一次执行的幂等键、摘要和状态。FAILED/UNKNOWN 时没有助手消息，但仍必须记住执行结果，防止重复调用模型或 Tool。

## 2. 为什么远程 HTTP 不能放在数据库事务里？

调用可能等待 60 秒。长事务会占用连接和行锁，放大锁等待、死锁与连接池耗尽。两个短事务配合状态机，比伪造跨服务原子事务更诚实。

## 3. “先查再插”为什么不能保证幂等？

两个并发请求可以同时查不到。数据库唯一约束才是最终裁决者；应用在重复键事务回滚后读取胜者，再比较 hash 和状态。

## 4. FAILED 与 UNKNOWN 有什么区别？

FAILED 表示明确失败；UNKNOWN 表示远端可能已收到并执行。UNKNOWN 自动重试可能产生重复副作用，因此只能返回记录状态或由后续对账处理。

## 5. 为什么 sources 需要随 request 持久化？

成功重放必须返回第一次完整业务结果。如果只存 answer，重放会丢失引用；如果再次调用 Python，又破坏幂等。只存已校验公开字段可兼顾一致性与安全。

## 6. 怎样证明 AgentClient 不在事务里？

结构上让 ChatService 调两个事务代理方法，中间调用 Client；测试中在 AgentClient mock 内断言 `TransactionSynchronizationManager.isActualTransactionActive()` 为 false。

## 7. 为什么 Phase 7 创建 user 表却不实现登录？

会话与 request 的归属和外键需要用户实体。表是数据完整性的前置条件；注册、密码策略和 JWT 是 Phase 9 的独立业务范围，不能夹带实现。

## 8. 没有 JWT 时如何安全测试？

生产 provider 失败关闭。测试上下文覆盖 provider 并插入 test-only 用户行。不能临时接受客户端传来的 userId，因为那会形成未认证越权入口。

## 9. H2 MySQL 模式能证明什么？

它能验证 migration 基本语法、约束、Mapper 映射和 Service 编排；不能完全证明 MySQL 的 JSON 转换、锁等待、隔离级别和执行计划。真实 MySQL 仍需单独验证。

## 10. 为什么历史按成功 request 取五对？

按消息条数截断可能产生孤立 USER/ASSISTANT。按 SUCCEEDED request 选最近五个，再把每个 request 的两条消息恢复正序，能保证协议要求的完整交替对。
