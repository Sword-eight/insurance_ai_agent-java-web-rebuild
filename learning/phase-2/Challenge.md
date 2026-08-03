# Phase 2 Challenge

## 练习 1：画出双事务聊天链

任务：从 Web POST 开始，画出事务 A、事务外 AgentClient、事务 B、Redis 缓存失效和最终响应。

限制：不能把 HTTP 调用放进事务；必须标出 request 状态变化。

验收标准：包含 Controller、Service、MySQL、Redis、AgentClient、FastAPI Facade、Graph；正常流程最终只有一条 USER 和一条 ASSISTANT 消息。

提示：先写 `RECEIVED → PROCESSING`，再考虑 Python 响应。

## 练习 2：推演三类失败

任务：分别分析“连接前失败”“读取超时”“Python 明确返回 Tool 错误”的 HTTP 错误码、chat request 状态、是否写助手消息、是否自动重试。

限制：不能把三个场景都叫网络错误。

验收标准：读取超时必须出现 UNKNOWN；每个场景说明是否可能已执行 Tool。

提示：关键问题是“能否证明请求没有送达”。

## 练习 3：找出幂等漏洞

任务：审查伪代码“先查 Redis，没有 Key 就调用 Python，成功后 SETEX”，指出至少三个并发或故障漏洞。

限制：不能只说“加锁”；需要使用当前 MySQL 唯一约束和状态机。

验收标准：覆盖 Redis 丢失、并发先查后写、读取超时和相同 Key 不同 message。

提示：数据库唯一约束负责最后一道防线，requestHash 负责判断冲突。

## 练习 4：设计有限历史裁剪样例

任务：给出 7 轮 user/assistant 对话，其中若干长消息；手工得到满足 10 条和 12000 字符预算的 history。

限制：按最新完整轮次保留；不能出现孤立 assistant；不能传 system/tool。

验收标准：写出裁剪前后条数、字符数、顺序，并说明 Java 和 Python 各自的校验责任。

提示：先从最新一轮倒序累计，再恢复为最旧→最新。

## 练习 5：判断数据应该放在哪里

任务：为“完整聊天历史、最近 10 条消息、PDF 原文件、文档索引状态、Embedding 向量、限流计数、requestHash”选择 MySQL、Redis、Java 文件存储或 Python/FAISS。

限制：每项只能有一个权威来源，但允许写出派生缓存。

验收标准：每项说明丢失后的恢复方式；不能让 Python 访问业务 MySQL。

提示：先问它是业务事实、临时加速，还是 AI 派生数据。

## 练习 6：口述一次架构 Review

任务：用 3 分钟向面试官解释为什么当前只有 5 张逻辑表、3 类 Redis Key、两个 API 前缀，以及为什么没有 MQ。

限制：不能用“最佳实践都这么做”作为理由；必须联系当前同步聊天和 Streamlit 现状。

验收标准：明确当前尚未实现 Java/FastAPI；至少说出一个取舍和一个后续限制。

提示：按“问题→选择→原因→代价”组织答案。

