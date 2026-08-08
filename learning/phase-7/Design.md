# Phase 7 Design Notes

## 1. 数据模型与标识

Flyway V1 创建 `iap_user`、`iap_conversation`、`iap_chat_request` 和 `iap_chat_message`。`iap_user` 只作为外键前置表，注册、密码哈希和 JWT 行为仍属于 Phase 9。

内部关系使用自增 BIGINT，对外使用 Java 生成的 UUID。Entity 保存内部字段；DTO 不接受 userId；VO 不暴露内部主键、删除时间或错误摘要。

## 2. 两个短事务

`ChatService` 不带 `@Transactional`，跨边界编排 `ChatPersistenceService`：

1. 事务 A 锁定 ACTIVE 会话，校验归属，创建 request 与 USER 消息，更新为 PROCESSING；
2. 提交后读取有限历史并调用 AgentClient；
3. 事务 B 锁定会话，写 ASSISTANT 与 sources，再更新 SUCCEEDED；失败则只更新 FAILED/UNKNOWN。

远程 HTTP 不持有数据库事务，集成测试通过 `TransactionSynchronizationManager` 明确断言这一点。

## 3. 持久幂等

最终边界是唯一约束 `(user_id, idempotency_key)`，不是“先查再插”。摘要使用规范化 `conversationId + message` 的 SHA-256：

- 同 Key、不同 hash：`CHAT_IDEMPOTENCY_CONFLICT`；
- 同 Key、PROCESSING：`CHAT_REQUEST_IN_PROGRESS`；
- 同 Key、SUCCEEDED：从 request、两条消息和 `sources_json` 重建第一次结果；
- 同 Key、FAILED/UNKNOWN：返回已记录稳定错误，不再次调用 Python。

插入竞争由唯一约束裁决；失败事务结束后重新读取胜者。

## 4. 状态机与交付不确定性

连接建立前明确失败或 Python 明确拒绝归为 FAILED。读取超时、无法判断是否送达的连接中断，以及 Python 报告同 requestId 仍在处理，归为 UNKNOWN。只有明确成功才写 ASSISTANT；UNKNOWN 禁止自动重试。

`HttpAgentClient` 新增 `DELIVERY_UNKNOWN` 内部分类，不改变公共错误码。公共调用仍只看到冻结的安全 ErrorCode。

## 5. 历史与来源

Python 调用最多携带最近 5 个已成功 request 的完整 USER/ASSISTANT 对，排除当前 request，并恢复为正序。MySQL 保存完整历史，Python checkpointer 不是长期事实来源。

公开 sources 在写入前经过与公共 VO 相同的字段校验，序列化到 `sources_json`。这使成功重放无需再次调用 Python，也不会伪造引用。

## 6. 认证过渡

`CurrentUserProvider` 隔离用户上下文。Phase 7 的生产实现始终抛出 `AUTH_UNAUTHORIZED`；Phase 9 再替换为 JWT 安全上下文。集成测试通过 MockBean 注入测试用户，并显式插入 `iap_user` 外键行。

## 7. 测试数据库与精度

生产连接参数 `MYSQL_URL/MYSQL_USERNAME/MYSQL_PASSWORD` 必须来自环境变量。测试使用 H2 `MODE=MySQL` 执行同一 migration。应用时间截断到毫秒，与 `DATETIME(3)` 对齐；JSON 读取同时兼容 MySQL 原生 JSON 文本和 H2 对字符串参数的文本节点包装。

H2 能验证 migration、约束和业务编排，但不能替代真实 MySQL 8 的方言、锁等待与执行计划验证。
