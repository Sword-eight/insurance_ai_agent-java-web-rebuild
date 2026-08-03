# Phase 2 Review Checklist

## 判定规则

- **PASS**：契约完整、内部一致、与冻结架构和真实源码相符，可进入最终 Review。
- **WARNING**：设计已明确，但实现、容量验证或运行证据属于后续 Phase；需记录且不能伪装已完成。
- **ERROR**：服务边界冲突、双重事实源、无条件 POST 重试、重复写入风险、关键契约缺失或把规划描述成实现；解决前不得通过。

## A. 状态与事实

- [x] 当前入口明确为 Streamlit，Java/FastAPI/MySQL/Redis 尚未实现。
- [x] `AgentGraphBuilder`、Graph Router、Tools、Services、RAG 的真实职责未被改写。
- [x] Phase 2 文档没有宣称 API 已启动或数据库已创建。
- [x] 当前 `application/handlers.py` 的调试字段没有被无条件公开。

## B. API

- [x] `/api/v1` 与 `/internal/v1` 分离。
- [x] 请求/响应字段、长度、分页、时间和 ID 格式明确。
- [x] 公共响应不直接暴露 Entity、LangChain 类型或 Python 堆栈。
- [x] 聊天、会话、消息和文档能力有稳定路径。
- [x] history 只含 user/assistant，最多 10 条、12000 字符。
- [x] sources 只允许返回真实可追溯内容，不伪造统计。

## C. 错误、追踪与幂等

- [x] 公共和内部错误码及 HTTP 映射明确。
- [x] traceId、Idempotency-Key、requestId 的职责分开。
- [x] 相同 Key + 不同 hash 返回 409。
- [x] 成功重复请求复用原结果，不重复写消息或调用 Tool。
- [x] 连接失败与读取超时采用不同状态和重试规则。
- [x] 读取超时进入 UNKNOWN，不自动重试。

## D. MySQL

- [x] 5 张逻辑表都有单一真实职责。
- [x] MySQL 是完整消息唯一长期事实源。
- [x] request 与 message 分表能表达失败/未知而不伪造助手消息。
- [x] 关键唯一约束和查询索引已定义。
- [x] 远程 HTTP 位于两个短事务之间。
- [x] 不保存 FAISS、Embedding、JWT、密码明文或 Python 对象。

## E. Redis

- [x] 三类 Key 都定义了 Value、TTL、读写者、来源和故障行为。
- [x] 近期消息 cache miss 回源 MySQL。
- [x] 幂等 Redis 故障时由 MySQL 唯一约束兜底。
- [x] 限流阈值、窗口、Retry-After 和 fail-closed 明确。
- [x] 没有无 TTL 短期 Key、万能 JSON 或分布式锁。
- [x] Python 不访问 Java Redis。

## F. 知识库

- [x] Java 管理 PDF 原文件、文档元数据和业务索引状态。
- [x] Python 管理解析、切分、Embedding 和 FAISS。
- [x] v1 使用受控单 PDF multipart，不传任意共享路径。
- [x] HTTP 已接收不等于索引成功。
- [x] 读取超时可表达 UNKNOWN。

## G. 生命周期与过度设计

- [x] HTTP/Redis Client 将由容器管理，不按请求创建。
- [x] Python request registry 明确为短期防重而非会话事实源。
- [x] 未引入 MQ、异步 task_id、完整 DDD、Nacos、Kubernetes 或分布式事务。
- [x] 没有迁移或重写 Python 核心目录。
- [ ] FastAPI lifespan、Client 连接池、熔断和并发尚未实现并验证（WARNING，后续 Phase）。

## 审计结论

| 维度 | 结论 | 说明 |
|---|---|---|
| 功能 | PASS | 文档契约覆盖正常和异常流程；实现状态披露准确 |
| 架构 | PASS | 服务、存储和分层边界与 Architecture v1.0 一致 |
| 设计 | PASS | 表、Key 和抽象均有当前需求支撑 |
| 生命周期 | WARNING | 目标生命周期已规定，真实 Bean/lifespan/并发需后续实现验证 |

ERROR：无。总评：**PASS with WARNING**。Phase 2 v1.0 已通过用户最终 Review 并正式冻结；WARNING 不代表功能已实现，也不授权自动进入 Phase 3。
