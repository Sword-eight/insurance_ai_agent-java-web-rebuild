# Insurance AI Platform 决策记录

> 文档版本：v1.0
> 状态：Phase 2 v1.0 决策已接受；自 2026-08-01 起生效
> 架构依据：[ARCHITECTURE.md](./ARCHITECTURE.md)
> 关联文档：[API.md](./API.md) / [DATABASE.md](./DATABASE.md) / [REDIS.md](./REDIS.md)

## 1. 决策规则

`docs/ARCHITECTURE.md` 是架构唯一事实来源。本文件记录 Phase 2 在冻结边界内如何选择 API、MySQL、Redis、错误、幂等和容错契约。

状态：

- `PROPOSED`：已完成设计和校验，等待对应 Review；
- `FUTURE`：只记录未来可能的改进方向，尚未选择方案，不属于当前 v1 能力；
- `ACCEPTED`：用户 Review 通过后生效；
- `SUPERSEDED`：被后续获批决策替代；
- `REJECTED`：明确不采用。

Phase 2 v1.0 的现行决策均已通过最终 Review，状态为 `ACCEPTED`；`P2-FUTURE-001` 仅是未来改进记录，继续保持 `FUTURE`，不属于当前能力。此次冻结没有修改 Phase 1 架构。

## 2. 继承约束

以下内容由 Phase 1 冻结，不是 Phase 2 的可选项：

- Web Client → Java Backend → Python AI Service；
- Controller → Service → Client / Mapper；
- MySQL 是完整聊天记录唯一长期事实源；
- Redis 是短期缓存、限流和幂等加速；FAISS 归 Python；
- Java 传 requestId、sessionId、当前 message 和有限 history；
- Python 第一版不持有长期用户会话事实；
- POST 聊天不无条件重试，读取超时视为结果未知；
- 不引入 MQ、完整 DDD、注册中心、Kubernetes 或分布式事务。

## 3. API 决策

### P2-API-001：区分公共与内部版本化 API

- 状态：`ACCEPTED`
- 问题：Web 与 Java→Python 面向不同调用方，不能共享暴露模型。
- 选择：公共 `/api/v1`；内部 `/internal/v1`；分别使用 Java DTO/VO 和 Python Schema。
- 原因：避免 Python 内部字段和异常泄露，路径可独立演进。
- 放弃：Web 直连 Python；共用一个万能 DTO。
- 代价：需要 AgentClient/KnowledgeClient 做模型转换。

### P2-API-002：统一公共响应但保留 HTTP 语义

- 状态：`ACCEPTED`
- 选择：`{code,message,data,traceId,timestamp}`，成功码 `OK`；错误使用正确 4xx/5xx，不全部返回 200。
- 原因：客户端有稳定业务码，同时日志和网关仍能按 HTTP 状态观察。
- 放弃：直接返回 Entity；所有异常都返回 200；把异常类名作为 code。
- 代价：需要全局异常映射和 VO。

### P2-API-003：有限历史固定为 5 轮/10 条

- 状态：`ACCEPTED`
- 选择：Java 传最多 10 条 user/assistant 消息、总计 12000 字符，单条最多 4000，最旧→最新；按最新完整轮次裁剪。
- 原因：与当前 `MEMORY_CONFIG.max_context_rounds=5` 对齐；控制同步请求体和 LLM 上下文；不传 ToolMessage，避免协议耦合。
- 放弃：传完整历史；只传当前消息；让 Python 回查 MySQL。
- 代价：早期上下文可能被裁剪，token 级预算仍需未来基于模型验证。

### P2-API-004：知识库采用内部流式 multipart

- 状态：`ACCEPTED`
- 选择：Java 保存原文件后，以单文件 multipart 把受控 PDF 内容和 metadata 传给 Python；上限 20 MiB。
- 原因：不依赖共享绝对路径，不引入对象存储，符合单机双进程 v1。
- 放弃：Python 读取任意 Java 路径；当前引入对象存储；在 JSON 中传 base64。
- 代价：同步上传占用连接，规模扩展时需演进为对象存储/异步任务。

### P2-API-005：不公开 Tool 和监控细节

- 状态：`ACCEPTED`
- 选择：公共聊天只返回 answer 和真实可追溯 sources；内部 v1 也不冻结 toolCalls。
- 原因：当前 `application/handlers.py` 中 Tool 参数、监控和截断结果用于 Streamlit 调试，不是稳定业务契约。
- 放弃：原样返回 `agent_monitor`、prompt、Tool 参数和伪造来源。
- 代价：前端暂时无法展示 Agent 调试过程；通过内部日志排查。

## 4. 幂等和状态决策

### P2-IDEM-001：客户端 Key、Java requestId、TraceId 三者分离

- 状态：`ACCEPTED`
- 选择：公共聊天要求 UUID `Idempotency-Key`；Java 为首次请求生成 UUID requestId；TraceId 只追踪日志。
- 原因：客户端能安全重发同一意图，Java 有内部稳定执行标识，链路追踪不影响业务唯一性。
- 放弃：用 TraceId 幂等；让数据库自增 ID 充当跨服务 requestId。
- 代价：需要额外映射和唯一约束。

### P2-IDEM-002：MySQL 最终防重，Redis 只加速

- 状态：`ACCEPTED`
- 选择：`iap_chat_request` 唯一 `(user_id,idempotency_key)` 并保存 requestHash；Redis 缓存 24 小时摘要。
- 原因：Redis 丢失后仍能防止重复消息和 Tool 调用。
- 放弃：只用 SETNX；只在 Controller 内存判断。
- 代价：每次缓存未命中需要查库。

### P2-IDEM-003：Python 使用短期进程内防重，不成为事实源

- 状态：`ACCEPTED`
- 选择：Facade 使用容量受限、TTL 10 分钟的 requestId 登记表复用单实例进行中/完成结果；进程重启后由 Java MySQL 兜底。
- 原因：防御 Java 短时间重复内部调用，又不引入 Python 访问 Redis/MySQL。
- 放弃：Python 持久化用户会话；没有任何内部防重；当前引入分布式存储。
- 代价：多实例/重启后不能依赖 Python 复用结果，因此 Java 对 UNKNOWN 仍禁止自动重试。

### P2-DB-001：请求状态与消息分表

- 状态：`ACCEPTED`
- 选择：5 张逻辑表，其中 `iap_chat_request` 保存执行/幂等状态，`iap_chat_message` 保存消息。
- 原因：读取超时产生 UNKNOWN，但不应伪造助手消息；职责清晰。
- 放弃：把处理状态塞进 Conversation；把整段历史存成 JSON；再引入通用任务表。
- 代价：聊天查询涉及 request/message 关联，但换来可审计状态。

### P2-DB-002：远程 HTTP 不进入长事务

- 状态：`ACCEPTED`
- 选择：HTTP 前后使用两个短本地事务，中间事务外调用 Python；通过状态机和补偿表达结果。
- 原因：避免持锁 60～300 秒，不需要分布式事务。
- 放弃：在一个数据库事务中等待 Python；引入 Seata/分布式事务。
- 代价：必须处理 FAILED/UNKNOWN 中间结果和缓存失效。

## 5. Redis 决策

### P2-REDIS-001：只使用三类 Key

- 状态：`ACCEPTED`
- 选择：近期消息 30 分钟、固定窗口聊天限流 120 秒、聊天幂等摘要 24 小时。
- 原因：都对应当前明确需求，且 Key/Value/TTL/回源可解释。
- 放弃：万能 JSON、Redis Session、分布式锁和无 TTL Key。
- 代价：更多热点缓存需真实需求后再增加。

### P2-REDIS-002：近期消息采用 Cache-Aside 写后删除

- 状态：`ACCEPTED`
- 选择：读未命中回源最新 10 条；消息事务提交后删除缓存，而不是并发更新 JSON 数组。
- 原因：MySQL 保持事实源，写后删除的并发语义更简单。
- 放弃：write-behind；把 Redis 当消息数据库。
- 代价：删除后首读会访问 MySQL，删除失败存在最长 30 分钟陈旧风险，读取侧需校验。

### P2-REDIS-003：聊天限流固定窗口且 Redis 故障 fail-closed

- 状态：`ACCEPTED`
- 选择：每用户每分钟 20 次，固定窗口；限流组件故障时返回 503，不调用付费 LLM。
- 原因：实现简单，保护外部成本和滥用风险。
- 放弃：故障时无限放行；当前引入复杂令牌桶/网关。
- 代价：窗口边缘可能突发，Redis 故障会暂时影响聊天可用性。

## 6. 错误与容错决策

### P2-RES-001：区分连接失败和读取超时

- 状态：`ACCEPTED`
- 选择：chat 连接/读取 3/60 秒，knowledge 3/300 秒，health 1/2 秒，DeepSeek 3/50 秒；禁止无限超时。
- 原因：读取超时不能证明 Python 未执行，必须进入 UNKNOWN。
- 放弃：所有超时统一自动重试；无上限等待。
- 代价：慢请求可能被中断且需要人工/后续状态核对。

### P2-RES-002：只允许一次可证明未送达的连接级重试

- 状态：`ACCEPTED`
- 选择：POST 默认零重试；仅 Client 能明确确认请求未被发送/接收时，在总预算内复用 requestId 最多一次。
- 原因：避免重复 DeepSeek、Tool 和消息写入。
- 放弃：统一重试三次；读取超时重试。
- 代价：部分瞬时错误直接失败，但结果语义可靠。

### P2-RES-003：两个独立熔断器

- 状态：`ACCEPTED`
- 选择：AgentClient/KnowledgeClient 各自 count window 20、minimum 10、50% 阈值、打开 30 秒、半开 3 次；只统计网络、超时和 5xx。
- 原因：聊天和索引耗时/故障域不同，不能互相拖累。
- 放弃：共用一个“AI 熔断器”；用熔断代替超时。
- 代价：Phase 11 需要分别测试并依据真实指标调参。

## 7. 未来改进：UNKNOWN 结果对账与回收

### P2-FUTURE-001：UNKNOWN 结果对账与回收

- 状态：`FUTURE`
- 当前结论：只记录问题和候选方向，不选择方案、不新增接口、不实现结果回收，也不改变同步 v1 契约。

#### 当前问题

Java 调用 Python 发生读取超时时，只能确认 Java 没有及时收到响应，不能确认 Python 是否已经：

- 接收请求；
- 调用 DeepSeek；
- 执行 Tool；
- 生成最终回答。

因此当前同步 v1 必须把 `iap_chat_request` 标记为 `UNKNOWN`，禁止自动重新调用 Python，以避免重复执行 LLM、Tool 和消息写入。相同 `Idempotency-Key` 继续返回原请求状态；用户明确点击“重新生成”时，必须使用新的 `Idempotency-Key`，由 Java 生成新的 `requestId`。

#### 当前限制

Python 当前只使用容量受限、TTL 10 分钟的单进程 requestId 登记表。它是短期防重设施，不是长期事实来源，存在以下限制：

- Python 进程重启后登记状态和结果丢失；
- 多实例之间不能共享执行状态；
- Java 无法可靠回收 Python 在读取超时后最终完成的结果；
- 单独增加 `GET /requests/{requestId}`，在没有持久化结果的情况下，不能彻底解决重启、多实例和结果丢失问题。

当前系统不宣称具备 UNKNOWN 结果对账或回收能力。

#### 未来候选方案

以下方案只进入未来评估，不在本记录中选择，也不授权实现：

1. Python 持久化 requestId、执行状态和最终结果，并提供状态查询接口；
2. 使用异步 `taskId` 和状态轮询；
3. Python 执行结束后向 Java 发送带 requestId 的幂等结果回调；
4. 未来引入适合的共享结果存储，支持多实例和进程重启后的结果恢复。

这些候选项可能影响服务接口、部署方式、生命周期和数据所有权。进入任何实现前，必须单独提出架构变更并经过 Review；不得作为 Phase 2 同步 v1 的隐含能力。

#### 进入实现前必须回答的问题

- 结果的权威所有者是谁；
- Python 结果保存在哪里以及保留多久；
- Java 如何安全地把 `UNKNOWN` 转为 `SUCCEEDED`；
- 回调或查询重复到达时如何幂等；
- Python 重启和多实例如何保证结果可查询；
- 迟到结果到达时，如果用户已经发起新的 requestId，应如何展示和审计；
- 是否会改变当前 Java/MySQL 是业务事实源的架构边界。

#### 当前 v1 保持不变

- `API.md` 中聊天仍是同步 POST；读取超时后不自动重试。
- `DATABASE.md` 中 `UNKNOWN` 仍是当前终态，不增加自动状态转换。
- Python 不访问 Java 业务 MySQL 或 Redis。
- Python 10 分钟 requestId 登记表不承担长期事实来源。
- 当前不增加状态查询 API、回调、MQ、Worker、对象存储、共享结果数据库或新服务。

## 8. 一致性矩阵

| 主题 | API | MySQL | Redis | Python |
|---|---|---|---|---|
| requestId | 内部必填 UUID | 唯一、长期状态 | 24h 摘要 | 10min 单进程防重 |
| Idempotency-Key | 公共聊天必填 UUID | 用户范围唯一 | hash 后入 Key | 不感知 |
| 会话历史 | 最多 10 条/12000 字符 | 完整事实 | 近期 10 条/30min | 仅本次输入 |
| 读取超时 | 504，不自动重试 | request → UNKNOWN | 写 UNKNOWN 摘要 | 可能仍执行 |
| 文档索引 | 单 PDF multipart | 业务状态 | v1 不缓存 | 解析/Embedding/FAISS |
| TraceId | Header/Envelope | 非业务主键 | 不作 Key | 日志关联 |

## 9. 已排除内容

- Java 直接访问 FAISS；Python 访问业务 MySQL/Redis；
- 持久化 LangGraph Checkpointer；
- MQ、异步 task_id、Worker、分布式事务；
- 完整 DDD、Nacos、Kubernetes；
- 在线 LoRA 推理；
- 目录迁移、Java/FastAPI 代码实现。

## 10. 变更流程

- 若只调整实现细节且不改变稳定契约，例如连接池大小，可在对应实现 Phase Review。
- 若改变路径、字段、错误码、Key Schema、TTL、状态机或幂等规则，先更新对应文档和本文件，等待批准。
- 若改变服务职责、数据所有权或调用方向，属于冻结架构变更，必须按 `AGENTS.md` 的六项说明流程处理。

## 11. Phase 2 审计结论

| 维度 | 结论 | 证据 |
|---|---|---|
| 功能 | PASS | API 正常/异常结果、状态机、Key 与回源规则可验证；本阶段不伪装为已实现接口 |
| 架构 | PASS | Java/Python、MySQL/Redis/FAISS、Controller/Service 边界保持不变 |
| 设计 | PASS | 5 张职责明确的逻辑表、3 类 Redis Key；未引入通用任务平台或无需求抽象 |
| 生命周期 | WARNING | HTTP/Redis Client、Python request registry、FastAPI lifespan 尚待后续实现和真实并发验证 |

ERROR：未发现。结论：**PASS with WARNING**。Phase 2 v1.0 已通过最终 Review 并正式冻结；`P2-FUTURE-001` 仍为未选择、未实现的 `FUTURE` 记录。本阶段不自行提交，也不自动进入 Phase 3。
