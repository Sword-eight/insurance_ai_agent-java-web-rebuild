# Phase 7 Learning Kit：MySQL 会话、消息与持久幂等

## 阶段结论

Phase 7 已用 Flyway、MyBatis-Plus 和 Java Service 落地会话、聊天请求、消息以及持久幂等：

```text
ConversationController → ConversationService / MessageService → Mapper → MySQL
ChatController → ChatService
               → 事务 A：request + USER + PROCESSING
               → 事务外：AgentClient → Python
               → 事务 B：ASSISTANT + SUCCEEDED，或 FAILED / UNKNOWN
```

用户、会话、request 和消息使用内部 BIGINT 关系键，对外只暴露 UUID。生产用户上下文在 Phase 9 前失败关闭；测试使用隔离用户，不新增可伪造的用户 Header。

四维审计结论：**PASS with WARNING**，无 ERROR。WARNING：当前机器没有 MySQL 8/Docker，数据库自动化只在 H2 MySQL 模式执行；Python 回归因本机可选 `transformers` 导入阻塞，使用进程内隔离该可选模块后通过。

## 验证摘要

```text
Java 全量测试：42 tests，0 failures，0 errors，0 skipped
Flyway V1：H2 MySQL 模式迁移成功
Java package：BUILD SUCCESS，生成可运行 JAR
Python pytest：63 passed（可选 transformers 隔离模式）
真实 MySQL 8：未执行，不宣称通过
```

## 导航

| 文件 | 用途 |
|---|---|
| `MiniCourse.md` | 本阶段 5 个必需概念 |
| `KnowledgeSummary.md` | 一页知识摘要 |
| `Interview.md` | 高频面试问答 |
| `Design.md` | 数据模型、事务、幂等与取舍 |
| `Challenge.md` | 编码与故障推演练习 |
| `CallGraph.md` | 当前真实调用图 |
| `TestMatrix.md` | 正常、异常与持久化测试矩阵 |
| `ReviewChecklist.md` | 构建证据与四维审计 |

## 阶段边界

本阶段不实现 JWT、Redis、Vue、文档业务、MQ、异步任务或 UNKNOWN 对账。Phase 7 到此停止，不自动进入 Phase 8。
