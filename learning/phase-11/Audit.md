# Phase 11 Audit

> 审计日期：2026-08-12
> 结论：PASS with WARNING；无未解决 ERROR。

## 真实验证证据

| 验证 | 实际结果 |
|---|---|
| Java 全量 `mvn test` | 89 passed，0 failures/errors/skips；包含 MySQL 8.4 Testcontainers |
| Java 最终 5xx/熔断定向 | 9 passed，BUILD SUCCESS |
| Java `mvn -DskipTests package` | BUILD SUCCESS；生成可执行 Spring Boot JAR |
| Python `pytest -q` | 75 passed，1 个既存依赖弃用 warning |
| Vue `npm test` | 8 files / 26 tests passed |
| Vue `npm run build` | TypeScript typecheck 与 Vite production build PASS |

## 四维审计

### 功能：PASS

TraceId、结构化日志、冻结超时、有限连接重试、双熔断器、健康探针和 Vue 错误展示均有实际测试。
正常响应、确定性 4xx、上游 5xx、timeout、breaker OPEN 与 readiness DOWN 均覆盖。

### 架构：PASS

保持 `Controller -> Service -> Client/Mapper -> Python/MySQL/Redis`；Python AI 能力仍位于原图、工具、
服务与 RAG 结构中。未引入 MQ、Worker、共享数据库或新的业务状态机。

### 设计：PASS

TraceId 与幂等职责分离；chat 只有可证明未发送时最多重试一次，knowledge 与未知结果零重试；
Agent/Knowledge 故障域隔离；liveness 不因依赖失败触发重启。

### 生命周期：PASS

Python ContextVar 在请求后恢复；Java MDC 在 Filter finally 清理；HTTP Client 和熔断器为应用级 Bean；
健康检查不泄露细节。Java 全量测试包含真实临时 MySQL，未连接用户或生产数据。

## WARNING

1. 未使用真实 DeepSeek Key 发起线上模型调用；测试验证构造参数与零自动重试，最终在线联调留给 Phase 12。
2. 未在真实浏览器完成 Vue→Java→Python 点击链；Vitest、类型检查和 production build 已通过，最终 E2E 留给 Phase 12。
3. `langchain-community` 有既存弃用 warning；不在本阶段扩大依赖迁移。
4. Flyway 对 H2 2.2.224/MySQL 8.4 给出高于已测试版本提示，但 migration 在真实容器中通过。

## 安全与仓库

- 未提交 `.env`、密钥、模型、索引、checkpoint、日志、`target` 或 `dist`。
- 日志未输出聊天正文、PDF 内容、JWT、密码、DeepSeek Key 或本机绝对路径。
- `AGENTS.md` 与 `docs/CODEX_WORKFLOW.md` 是用户明确保留的规划基线，本阶段未改动其内容。
