# Phase 7 Challenges

## Challenge 1：并发幂等竞态

画出两个线程同时执行“查询为空→插入”的时序，指出唯一约束在哪一步裁决。实现测试，证明最终只有一个 request 和一条 USER。

## Challenge 2：事务外 HTTP

故意给 ChatService 整体添加 `@Transactional`，观察 Agent mock 中事务状态。解释为什么测试应失败，并恢复两个短事务设计。

## Challenge 3：UNKNOWN

分别模拟连接拒绝、读取超时和连接重置。为每种情况判断 FAILED/UNKNOWN、公共错误码、是否写 ASSISTANT、是否允许自动重试。

## Challenge 4：成功重放

首次返回一个真实 source，第二次使用同 Key/同载荷。断言两个 ChatResponse 完全相同、Agent 只调用一次，并检查 `sources_json` 不包含内部 Tool 参数。

## Challenge 5：历史窗口

连续完成 7 次聊天，第 7 次调用应只收到第 2～6 次的五个完整 pair。再插入一个 FAILED request，证明它不进入历史。

## Challenge 6：归属攻击

创建 A、B 两个用户和各自会话。用 A 查询 B 的详情、消息并向 B 会话聊天，验证稳定 403 且不产生 request/message。

## Challenge 7：数据库差异

列出 H2 MySQL 模式无法替代真实 MySQL 8 的至少四点，并设计 Testcontainers/MySQL CI 测试方案；只做方案，不在 Phase 7 引入 Docker 依赖。

## Challenge 8：进入 Phase 8 前

设计 Redis 近期消息缓存如何以 MySQL 为事实来源，明确缓存键、TTL、失效顺序和 Redis 不可用时的行为。不得让缓存失败回滚已提交的聊天事实。
