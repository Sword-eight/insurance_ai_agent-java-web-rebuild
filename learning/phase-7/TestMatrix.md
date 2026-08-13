# Phase 7 Test Matrix

> 状态：实现完成。完整命令证据见 `ReviewChecklist.md`。

## 1. Migration 与会话

| 场景 | 期望 |
|---|---|
| 空测试库启动 | Flyway V1 创建四张表和约束 |
| 创建会话 | 201、ACTIVE、UUID、毫秒 UTC 时间 |
| 会话列表 | 仅当前用户，updated_at/id 倒序，page/size 生效 |
| 会话详情不存在 | `CONVERSATION_NOT_FOUND` |
| 会话属于其他用户 | `CONVERSATION_ACCESS_DENIED` |
| Phase 9 前无用户上下文 | 401 `AUTH_UNAUTHORIZED` |

## 2. 聊天持久化与幂等

| 场景 | 期望 |
|---|---|
| 首次成功 | request + USER + ASSISTANT，状态 SUCCEEDED |
| 成功重放 | 业务响应与第一次相同，sources 可恢复，不调用 Agent |
| 同 Key 不同载荷 | 409 冲突，不新增行、不调用 Agent |
| 首次仍在 Agent 执行 | 重复请求返回处理中，Agent 总调用一次 |
| 读取超时 | UNKNOWN，仅 USER；重复请求不调用 Agent |
| 明确依赖失败 | FAILED，仅 USER |
| 非法响应/answer/source | FAILED + 安全公共错误 |

## 3. 历史与事务

| 场景 | 期望 |
|---|---|
| 无历史 | Python history 为空 |
| 一个成功 request | 下一次收到完整 user/assistant 对 |
| 超过五轮 | 只发送最近 5 对、保持正序 |
| 当前 USER | 不进入本次 history |
| AgentClient 调用 | `isActualTransactionActive=false` |

## 4. 公共 API 与回归

| 场景 | 期望 |
|---|---|
| 创建→聊天→列表→详情→消息 | 统一 Envelope 与冻结字段全部正确 |
| 消息分页 | sequence_no 升序，含 requestId/role/content/createdAt |
| OpenAPI | 只出现 `/api/v1/**`，不暴露内部 API |
| Phase 0～6 Java 测试 | 全量继续通过 |
| Python 测试 | 63 项继续通过；本机使用可选 transformers 隔离模式 |
