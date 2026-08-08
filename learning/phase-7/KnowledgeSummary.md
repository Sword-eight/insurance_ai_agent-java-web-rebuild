# Phase 7 Knowledge Summary

## 数据事实

MySQL 是会话、chat request 和完整消息的长期事实来源。Redis 尚未加入；Python 不访问 Java 数据库，checkpointer 也不替代业务历史。

```text
iap_user → iap_conversation → iap_chat_request → iap_chat_message
```

对外 UUID 与内部 BIGINT 分工：UUID 稳定且不可枚举，BIGINT 用于外键和索引。DTO、VO、Entity 不复用。

## 事务边界

```text
短事务 A：归属 + request + USER + PROCESSING
事务外：AgentClient.chat
短事务 B：ASSISTANT + sources + SUCCEEDED / FAILED / UNKNOWN
```

HTTP 不进入数据库事务。`@Transactional` 放在独立持久化协作者，避免 Spring 自调用失效。

## 幂等

`UNIQUE(user_id, idempotency_key)` 是并发最终防线；`request_hash` 区分合法重放与载荷冲突。成功从 MySQL 重建结果，任何终态重放都不再次调用 Python。

## 状态语义

- FAILED：明确未调用或明确失败；
- UNKNOWN：远端可能已经执行；
- 只有 SUCCEEDED 写 ASSISTANT；
- UNKNOWN 禁止自动重试。

## 归属和认证

所有会话/消息查询都先解析当前用户并带归属条件。Phase 9 前生产 `CurrentUserProvider` 返回 401，测试才注入隔离用户。没有 `X-User-Id` 后门。

## 验证边界

42 个 Java 测试覆盖 migration、公共链路、归属、持久幂等、并发处理中、历史和失败状态；63 个 Python 测试保持回归。H2 MySQL 模式不是 MySQL 8，真实数据库仍需部署环境验证。
