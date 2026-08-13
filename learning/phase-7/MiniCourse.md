# Phase 7 MiniCourse：MySQL 会话、消息与持久幂等

> 阶段目标：用 MyBatis-Plus 和 MySQL 落地会话、chat request、用户/助手消息以及同步聊天状态机。
> 当前状态：实现与验收完成；本文件保留为面试冲刺预习入口。
> 阶段边界：不实现 JWT、Redis、Vue、文档业务、MQ、异步任务或 UNKNOWN 对账。

本阶段面试冲刺需要掌握 5 个直接决定实现正确性的概念。

## 1. Entity、Mapper、DTO、VO 为什么必须分离

```text
HTTP DTO/VO ↔ Controller → Service → Mapper ↔ Entity ↔ MySQL
```

- Entity 表达表字段，包含内部 `BIGINT` 主键和数据库状态。
- DTO 表达请求，不携带数据库内部主键或可伪造的 userId。
- VO 表达公共视图，只返回 UUID、稳定状态和允许公开的内容。
- Mapper 负责 SQL，不判断资源归属、幂等结果或状态流转。

对外 UUID 和内部 BIGINT 同时存在：UUID 稳定、不可枚举，BIGINT 适合关系和索引。Entity 直接出现在 API 会泄露内部主键、删除字段和实现细节。

### 面试问法

问：MyBatis-Plus 已经提供 `BaseMapper`，为什么还需要 Service？

答：CRUD 工具只能减少样板 SQL，不能表达会话归属、幂等冲突、状态机和事务边界。Service 决定“何时、以什么顺序、在什么事务里”调用 Mapper。

## 2. 两个短事务为什么比一个长事务正确

同步聊天最长可能等待 Python 60 秒，不能一直持有数据库锁：

```text
事务 A：归属校验 + request/用户消息 + PROCESSING → commit
事务外：读取有限历史 + AgentClient.chat(...)
事务 B：助手消息 + SUCCEEDED，或 FAILED/UNKNOWN → commit
```

如果把 HTTP 放进事务，会长时间占用连接和行锁，放大超时、死锁和连接池耗尽。两个本地事务不保证远端原子性，而是用持久状态机诚实表达结果。

Spring 的 `@Transactional` 依赖代理；同一个 Bean 内部 `this.method()` 自调用不会经过代理。适合把事务 A/B 放进独立的事务协作者，ChatService 只负责跨事务编排。

## 3. 唯一约束才是最终幂等边界

冻结唯一键：

```text
(user_id, idempotency_key) UNIQUE
```

流程不能只做“先查、没有再插”，因为两个并发请求可能同时查不到。正确做法是：

1. 先查询已有 request；
2. 尝试插入；
3. 让唯一约束裁决竞争；
4. 失败事务结束后重新读取胜者；
5. 比较 `request_hash` 并按状态返回。

同 Key + 不同 hash 返回冲突；同 Key + `PROCESSING` 返回处理中；`SUCCEEDED` 从 MySQL 重建既有结果；`FAILED/UNKNOWN` 返回已记录错误，绝不再次调用 Python。

## 4. FAILED 和 UNKNOWN 的差别

```text
RECEIVED → PROCESSING → SUCCEEDED
                      → FAILED
                      → UNKNOWN
```

- `FAILED`：Java 知道调用前失败或 Python 明确失败。
- `UNKNOWN`：读取超时/连接中断，Python 可能已经执行模型或 Tool。

UNKNOWN 不能改写为 FAILED，也不能自动再发一次聊天，否则可能重复执行模型、Tool 和消息写入。失败或未知状态都不写占位助手消息；只有明确成功才写 ASSISTANT。

## 5. 归属、历史裁剪与失败关闭

所有会话和消息查询都必须带当前用户归属条件。Phase 7 早于 JWT，因此不能使用固定生产用户或新增 `X-User-Id`：

- 生产 `CurrentUserProvider` 在 Phase 9 前返回 `AUTH_UNAUTHORIZED`；
- 集成测试用 test-only provider 和隔离测试用户；
- Controller 永远不接受 body/header 中伪造的 userId。

调用 Python 前，Java 从 MySQL 读取最近最多 5 轮完整 user/assistant 对，排除当前 request 的 USER 消息，再按时间正序传递。MySQL 保存完整历史；Python checkpointer 不是长期事实来源。

## 冲刺自检

1. 为什么一个 60 秒远程调用不能放在数据库事务里？
2. 唯一约束如何修复“先查再插”的并发竞态？
3. `FAILED` 和 `UNKNOWN` 分别能否自动重试？
4. 为什么成功幂等重放还需要持久化公开 sources？
5. Phase 7 没有 JWT 时，怎样验证归属逻辑又不伪造生产认证？
