# Phase 2 Design：契约选择与取舍

## 1. 为什么分公共 API 与内部 API

- 问题：浏览器关注会话、消息 ID 和稳定错误；Python 返回的是 Agent、Tool 和检索内部结果。
- 选择：Java 暴露 `/api/v1`，Python 只暴露 `/internal/v1`；使用不同 DTO/Schema。
- 原因：Java 能屏蔽 Python 重构，Python 异常和 LangChain 类型不会泄露给 Web。
- 放弃：Web 直连 Python；公共/内部共用万能 DTO。
- 代价：Java 要做一次模型转换，但这是边界的实际职责。

当前 `application/handlers.py` 的 `tool_calls`、`agent_monitor` 和 `total_time` 是 Streamlit 调试数据，不自动成为公共契约。`sources` 只有在真实可追溯时才返回，不能把固定文本或 `N/A` 当事实。

## 2. 为什么 API ID 用 UUID，数据库仍用 BIGINT

- 问题：数据库希望索引紧凑，外部 API 又不应暴露连续自增 ID。
- 选择：每张业务表使用 BIGINT 内部主键，同时用 CHAR(36) UUID 作为公开 ID。
- 原因：Mapper 关联简单，外部标识跨服务稳定且不暴露记录数量。
- 放弃：只用自增 ID 对外；所有表只用随机字符串主键。
- 代价：每张表多一个唯一索引，Service/Mapper 需要做公开 ID 到内部 ID 的转换。

## 3. 为什么客户端 Key、requestId、traceId 不合并

- 问题：一次请求既需要客户端安全重发、服务端稳定执行标识，也需要跨日志追踪。
- 选择：
  - `Idempotency-Key`：客户端对“同一意图”的 UUID；
  - `requestId`：Java 首次接收后生成的业务执行 UUID；
  - `traceId`：每条调用链日志标识。
- 原因：同一业务请求可能经过不同 trace；日志重放也不应创建新业务记录。
- 放弃：用 traceId 防重；直接用数据库自增 ID 跨服务传递。
- 代价：多三个容易混淆的字段，因此必须在文档、日志和表约束中统一语义。

## 4. 为什么新增 `iap_chat_request`

- 问题：读取超时后 Python 可能已经执行，但 Java 没有回答；此时没有助手消息可以保存，却必须记录结果未知。
- 选择：request 表保存 `RECEIVED → PROCESSING → SUCCEEDED/FAILED/UNKNOWN`；message 表只保存真实用户/助手消息。
- 原因：幂等状态、错误摘要和消息内容职责分离；不会用“抱歉”占位消息伪装执行结果。
- 放弃：只用 message 表；把状态塞到 conversation；引入通用异步任务平台。
- 代价：多一张表和一次关联查询，但能准确解释失败和防重。

## 5. 为什么远程调用放在两个短事务之间

- 问题：Python chat 最长等待 60 秒，索引最长 300 秒；数据库事务若覆盖 HTTP，会长期持锁。
- 选择：事务 A 持久化请求/用户消息并提交；事务外调用 Python；事务 B 写结果状态/助手消息。
- 原因：只使用本地事务，降低锁冲突；状态机显式表达中间结果。
- 放弃：一个大事务等待 Python；引入分布式事务。
- 代价：系统会看到 PROCESSING/UNKNOWN，需要幂等恢复和运维观察。

## 6. 为什么历史限制为 5 轮/10 条

- 问题：完整历史会让请求体和模型上下文无限增长；只传当前消息又可能丢失上下文。
- 选择：Java 传最新 5 个完整 user/assistant 轮次，最多 10 条、12000 字符。
- 原因：与当前 `MEMORY_CONFIG.max_context_rounds=5` 对齐，成本可控；Python 不需要访问 MySQL。
- 放弃：完整历史；Python 依赖 `InMemorySaver` 恢复长期会话。
- 代价：很早的信息可能丢失；未来可增加摘要，但需单独设计且不能成为第二事实源。

当前 HTTP history 不包含 ToolMessage。现有 Graph 内部的 `safe_truncate_messages()` 仍负责防止 AI tool_calls 与 ToolMessage 被切断，两套裁剪解决不同边界的问题。

## 7. 为什么 Redis 只有三类 Key

- 问题：写一句“用 Redis 提升性能”无法指导实现，也容易把 Redis 变成第二数据库。
- 选择：
  - 最近 10 条消息，TTL 30 分钟；
  - 每用户聊天固定窗口，20 次/分钟，Key TTL 120 秒；
  - chat 幂等摘要，TTL 24 小时。
- 原因：三类都有明确 Value、读写者、回源和故障行为。
- 放弃：万能 JSON、无 TTL Key、Redis Session 和分布式锁。
- 代价：暂不缓存会话列表或文档查询；真实压测证明需要后再加。

## 8. 为什么近期消息写后删除

- 问题：多个请求并发更新同一个 JSON 数组容易覆盖或乱序。
- 选择：MySQL 事务成功后删除 Key，下次读取回源并回填。
- 原因：实现简单，MySQL 始终是事实来源。
- 放弃：write-behind；每次在 Redis 原地追加且把它作为权威历史。
- 代价：删除后的第一次读取会访问数据库；删除失败可能短时读到旧缓存，因此读取侧仍需校验。

## 9. 为什么限流故障采用 fail-closed

- 问题：Redis 不可用时放行所有请求会直接放大付费 LLM 成本和滥用风险。
- 选择：聊天限流不可用时返回 503，不写消息、不调用 Python；消息缓存和幂等缓存故障则回源 MySQL。
- 原因：不同 Redis 用例风险不同，不能用一个统一降级策略。
- 放弃：所有 Redis 故障都 fail-open；引入复杂网关限流。
- 代价：Redis 故障会暂时影响聊天可用性，必须有健康监控。

## 10. 为什么 POST 只允许极窄的连接级重试

- 问题：读取超时不能说明 Python 没有调用 DeepSeek 或 Tool。
- 选择：默认零重试；仅能确认请求未送达时，同 requestId、总预算内最多一次连接级重试；读取超时绝不自动重试。
- 原因：避免重复 Tool 副作用和重复消息。
- 放弃：统一三次重试；把 timeout 当失败后重新发送。
- 代价：部分瞬时失败不会自动恢复，但结果语义和费用更可控。

## 11. 为什么 Python 只有 10 分钟进程内防重

- 问题：Java 正常情况下不重复发送，但仍需防御短时间网络/客户端错误导致的相同内部 requestId。
- 选择：Facade 使用有容量上限、TTL 10 分钟的登记表；进行中返回冲突，完成后复用结果。
- 原因：不让 Python 访问 Java Redis/MySQL，也不把它变成长期会话存储。
- 放弃：持久化 Python Checkpointer/数据库；完全没有防重。
- 代价：重启或多实例不能依赖它。因此 Java MySQL 仍是最终防重，UNKNOWN 仍禁止自动重试。

## 12. 当前设计限制

- 同步 PDF 索引最长 300 秒，不适合大规模文档；后续才考虑对象存储和异步 Worker。
- 固定窗口限流存在边界突发；真实流量证明需要后才演进算法。
- 5 轮字符预算不是精确 token 预算；模型和提示词稳定后需测量。
- Python 进程内 request registry 不支持多实例一致去重。
- 当前源码来源追踪有限，API 虽允许 sources，但实现不得伪造。

