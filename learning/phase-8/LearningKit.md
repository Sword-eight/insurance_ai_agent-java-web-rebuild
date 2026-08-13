# Phase 8 Learning Kit

## 一句话讲清设计

MySQL 保存事实，Redis 只保存可过期的性能与保护状态：最近消息用 cache-aside，幂等缓存只提供查询提示，限流则因保护下游而选择 fail-closed。

## 面试讲解顺序

1. 先讲数据所有权：消息和幂等结果属于 MySQL，Redis 丢失不能破坏正确性。
2. 再讲主链路：限流在最前，短事务写用户消息，中间无事务调用 Python，第二个短事务写助手消息。
3. 解释一致性：数据库提交后删除最近消息缓存，删除失败由 TTL 兜底，不引入分布式事务。
4. 解释并发：Lua 原子计数；MySQL 唯一约束解决幂等并发竞态，Redis 不能替代它。
5. 最后讲故障矩阵：最近消息和幂等缓存 fail-open，限流 fail-closed。

## 代码阅读地图

- `ChatService`：观察限流、幂等提示、两个事务、无事务 AI 调用和提交后缓存动作的顺序。
- `ChatPersistenceService`：观察 Redis requestId 只能缩小查询范围，重放仍由 MySQL 校验。
- `RedisChatRateLimiter`：观察 Lua 的 `INCR/EXPIRE/TTL` 原子组合与 503 映射。
- `RedisRecentMessageCache`：观察原始 JSON 数组校验、坏数据删除和 fail-open。
- `RedisChatIdempotencyCache`：观察冻结 Value 结构、24 小时 TTL 和故障回退。
- `DefaultRedisKeyFactory`：观察环境隔离、Key 版本和幂等键哈希。

## 高频问题与参考答案

### 为什么 Redis 幂等命中后还要查询 MySQL？

因为 Redis 可能过期、丢失、陈旧或被错误写入。它只能提供 `requestId` 查询提示；请求哈希、会话、状态和原始响应必须由 MySQL 事实确认。

### 为什么限流故障不是 fail-open？

限流的职责是保护数据库和 Python AI 服务。Redis 故障时继续放行会在失去保护的同时放大下游压力，因此冻结策略是 503 fail-closed。幂等和最近消息缓存则能安全回源 MySQL。

### 为什么先提交 MySQL 再删除缓存？

事务提交前删除会留下竞态：另一线程可能在旧事务尚未提交时回源并重新写入旧缓存。提交后删除能缩小不一致窗口；删除失败不反向回滚数据库，由 TTL 和下一次失效修复。

### Lua 解决了什么问题？

它让首次 `INCR` 与 `EXPIRE` 成为一个原子操作，避免进程在两条命令之间失败而留下无 TTL 的永久计数器。

### 固定窗口有什么缺点？

分钟边界两侧可能形成突发流量，例如前一分钟最后几秒和下一分钟最初几秒各 20 次。本阶段按冻结契约采用固定窗口；滑动窗口属于需要重新评审的演进方案。

## 自测题

1. Redis 最近消息缓存中为什么保存 `sequenceNo`，读取时为什么必须验证升序？
2. 同一个幂等键但请求正文不同，Redis 与 MySQL 分别承担什么职责？
3. 如果助手响应已写入 MySQL，但缓存删除失败，下一次请求可能看到什么，系统靠什么恢复？
4. 为什么限流 Key 含 `minuteEpoch`，TTL 却设置为 120 秒而不是恰好 60 秒？
5. 如何证明 Python 没有访问 Redis，也没有改变其长期会话职责？

## 动手练习

- 把限流上限在测试配置中改为 2，证明第 3 次拒绝且 MySQL 请求数不增加；完成后恢复配置。
- 向最近消息 Key 写入乱序数组，观察 Adapter 将其视为 miss 并删除坏数据。
- 让幂等缓存返回另一个用户的 requestId，证明 MySQL 用户条件阻止错误重放。
- 停止 Redis 后分别调用限流、历史缓存和幂等路径，比较 503 与 MySQL 回退行为。

## 验收命令说明

Maven 本地仓库使用 `D:\MavenPhase8\repository`。普通完整回归执行 `mvn test`；真实 MySQL 联合验收需显式指向隔离验收库，不能指向开发库或生产库。Docker Engine 29 需要 Testcontainers 1.21.4 或更高兼容版本。
