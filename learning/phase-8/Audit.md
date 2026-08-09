# Phase 8 四维审计

> 审计对象：Redis 最近消息缓存、聊天限流、幂等加速及其聊天主链路接入。

## 功能：PASS

- 最近消息采用 cache-aside，命中直接生成有限历史，未命中或坏 JSON 回源 MySQL 并回填。
- 固定分钟窗口允许前 20 次，第 21 次返回 429，`Retry-After` 最小为 1 秒；跨分钟使用新 Key。
- 限流 Redis 故障 fail-closed 为 503，并发生在数据库写入和 Python 调用之前。
- 幂等 Redis 命中和未命中均由 MySQL 确认；Redis 故障回退 MySQL，数据库唯一约束保留最终防线。
- 最近消息 30 分钟、限流 120 秒、幂等摘要 24 小时 TTL 均由真实 Redis 命令验证。

## 架构：PASS

- 保持 `Controller -> Service -> Redis/Mapper/Client`；Controller 未直接访问 Redis、Mapper 或 Python Client。
- MySQL 仍是用户、会话、请求和消息唯一事实来源；Redis Value 均可过期或重建。
- Python 源码、LangGraph/RAG/FAISS 调用关系、公共聊天 DTO 和数据库迁移均未修改。
- 未引入 MQ、Worker、分布式锁、分布式事务、Nacos 或新的异步协议。

## 设计：PASS

- Redis Key 统一包含环境、`v1` 和业务域；原始幂等 UUID 经 SHA-256 后进入 Key。
- Lua 将计数和首次 TTL 设置合并为原子操作；未使用 `KEYS` 通配扫描。
- 最近消息 Value 是冻结的原始 JSON 数组；幂等 Value 不含未批准的 `schemaVersion`。
- Redis 连接、命令超时有限；Adapter 对可选缓存 fail-open，对限流 fail-closed。
- Testcontainers 仅为测试依赖，并固定到兼容 Docker Engine 29 的 1.21.4。

## 生命周期：PASS

- Redis Template、KeyFactory、Clock 和各 Adapter 均由 Spring 作为单例管理，不保存请求级可变状态。
- 所有缓存写入和失效均在相应 MySQL 事务方法返回后执行；失效失败不会回滚已提交事务。
- 测试 Redis 容器由 Testcontainers/Ryuk 清理；Spring 测试上下文显式关闭连接。
- Key 均有有限 TTL，不创建长期 Redis 业务事实或后台线程。

## 实际验证

- Java 完整测试：55 tests，0 failures，0 errors，0 skipped。
- 真实 Redis：Redis 7.4 Alpine，验证序列化、坏 JSON、TTL、20/21、跨窗口和故障语义。
- 联合验收：真实 MySQL 8.4 + 隔离 Redis 7.4，完整执行新请求、Redis 幂等命中、MySQL 重放确认、历史回源和第二次请求。
- 构建：Maven package 成功，依赖缓存位于 D 盘。

## 非阻塞警告

- 当前 Spring Boot 管理的 Flyway 10.10 对 MySQL 的已测试上限提示为 8.1；实际 MySQL 8.4 迁移校验和联合测试均通过。后续依赖升级阶段应重新评估 Flyway 版本。
- H2 仍只用于快速回归；本阶段已经额外完成真实 MySQL 8.4 联合验收，不能用 H2 结果替代该结论。

## 总结

总体结论：PASS。没有 ERROR，没有架构漂移；以上警告不阻塞 Phase 8 验收。
