# Phase 2 Learning Kit：把架构变成稳定契约

## 本阶段目标

Phase 1 回答“Java 与 Python 各自负责什么”；Phase 2 回答“它们以后怎样准确交流和保存状态”。本阶段冻结 API、错误码、MySQL 逻辑模型、Redis Key/TTL、幂等和容错默认值，不实现 Java、FastAPI、MySQL 或 Redis 代码。

## 当前实现与目标设计

当前真实实现：

- `app.py` 是 Streamlit 入口；
- `application/bootstrap.py` 装配现有 Graph、Tool、Service 和 RAG；
- `AgentGraphBuilder.invoke()` 接收当前消息与 `session_id`；
- `application/handlers.py` 返回供 Streamlit 使用的内部字典；
- Java Backend、FastAPI Router、DTO、VO、MySQL 和 Redis 均尚不存在。

Phase 2 目标契约：

- Web 只调用 Java `/api/v1`；
- Java Client 只调用 Python `/internal/v1`；
- MySQL 保存完整业务事实；Redis 只缓存/限流/幂等加速；
- Java 传最多 5 轮有限历史给 Python Facade；
- 同步 POST 通过 Idempotency-Key/requestId 防重，并区分连接失败与读取超时。

## 推荐阅读顺序

1. `MiniCourse.md`：先理解四类契约为什么存在。
2. `Design.md`：学习每项选择背后的取舍。
3. `CallGraph.md`：把字段、状态和存储放回真实调用链。
4. `Challenge.md`：独立做判断题、状态推演和调用图练习。
5. `Interview.md`：练习可口述的面试回答。
6. `KnowledgeSummary.md`：阶段结束后快速复习。
7. `ReviewChecklist.md`：最终检查是否达到冻结条件。

同时对照：

- `docs/API.md`
- `docs/DATABASE.md`
- `docs/REDIS.md`
- `docs/DECISIONS.md`

## 每份文档的用途

| 文件 | 用途 |
|---|---|
| `MiniCourse.md` | 直接教学 API、数据库、Redis 和可靠性基础 |
| `Design.md` | 解释为什么选择当前契约及其代价 |
| `CallGraph.md` | 展示正常聊天、异常聊天和知识库链路 |
| `Interview.md` | 训练 Phase 2 相关口述题与追问 |
| `Challenge.md` | 练习分层、状态机、幂等和故障判断 |
| `ReviewChecklist.md` | 冻结前逐项验收并给出 PASS/WARNING/ERROR |
| `KnowledgeSummary.md` | 一页记住最重要的规则和数字 |

## 学习验收标准

完成本阶段后，你应该能：

- 解释公共 API 和内部 API 为什么不能共用模型；
- 说清 traceId、Idempotency-Key、requestId 三者区别；
- 画出一次聊天的两个本地事务与中间 HTTP 调用；
- 解释为什么读取超时进入 `UNKNOWN` 且不能自动重试；
- 说出 5 张逻辑表各自职责和关键唯一约束；
- 为 Redis Key 同时说明 Value、TTL、事实来源和故障降级；
- 对照源码指出哪些字段当前真实存在，哪些只是未来 Facade 契约；
- 明确当前没有 Java/FastAPI 实现，测试通过不等于双服务已运行。

