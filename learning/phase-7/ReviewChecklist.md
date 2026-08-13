# Phase 7 Review Checklist

## 1. 范围与 Git

- [x] 独立分支：`phase-7-mysql-conversations`，基线为 Phase 6 提交 `8ba045b`。
- [x] 开始时除获批的 Phase 7 v1.1 设计增补外无用户改动；未覆盖 Phase 2 文档。
- [x] v1.1 只补充用户上下文过渡、会话 VO 和 sources 持久重放，未改变服务职责。
- [x] 未实现 Redis、JWT、Vue、文档业务、MQ、异步任务或 UNKNOWN 对账。
- [x] 未提交、未 push、未进入 Phase 8。

## 2. 功能验收

| 检查项 | 结果 | 证据 |
|---|---|---|
| Flyway 创建四张 Phase 7 表 | PASS | H2 MySQL 模式启动日志，schema version v1 |
| 会话创建/列表/详情 | PASS | Service + MockMvc 集成测试 |
| 消息分页与稳定顺序 | PASS | 端到端公共 API 测试 |
| 当前用户归属与越权区分 | PASS | 双用户集成测试 |
| Phase 9 前生产失败关闭 | PASS | 公共接口返回 401 测试 |
| 持久幂等成功重放 | PASS | 相同响应、sources 恢复、Agent 调用计数 |
| 同 Key 不同载荷冲突 | PASS | 409 + 无第二次 Agent 调用 |
| PROCESSING 并发重复 | PASS | CountDownLatch 并发测试 |
| FAILED/UNKNOWN 不写助手消息 | PASS | 数据库状态与行数断言 |
| 最近五个完整历史 pair | PASS | 七轮聊天历史断言 |
| HTTP 在事务外 | PASS | Agent mock 事务状态断言 false |
| 生产密钥外部化 | PASS | 三个 MYSQL 环境变量，无硬编码凭据 |

## 3. 实际构建与测试

### Java

```text
mvn test
Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

mvn -DskipTests package
BUILD SUCCESS
生成 target/insurance-platform-backend-0.6.0-SNAPSHOT.jar
```

测试使用 H2 2.2.224 `MODE=MySQL` 执行 Flyway V1。Flyway 输出对该 H2 补丁版本的支持 WARNING，不影响测试通过；本机没有 MySQL 8 或 Docker，因此没有执行真实 MySQL 测试。

### Python

普通 `pytest` 在收集 `test_graph_builder.py` 时卡在以下可选依赖链：

```text
langchain_openai
→ langchain_core.language_models.base
→ transformers.GPT2TokenizerFast
→ transformers 4.49.0 可选包扫描阻塞
```

`openai`、`tiktoken`、`huggingface_hub`、`tokenizers`、`safetensors` 单独导入正常。未修改 Python 源码或安装，使用单进程临时隔离可选 transformers：

```text
python -c "import sys; sys.modules['transformers']=None; import pytest; ..."
63 passed in 214.90s
```

该结果验证正式 Python API/Graph 测试路径，但不验证 finetune/transformers 功能。

## 4. 四维审计

### 功能：PASS

- 会话、消息、聊天持久化、成功重放、冲突、处理中、失败和未知流程均有真实自动化证据。
- 公共创建→聊天→列表→详情→消息链通过 MockMvc 走真实 Service/Mapper/migration。

### 架构：PASS

- 依赖方向保持 `Controller → Service → Client / Mapper`。
- Controller 对 Mapper、AgentClient 和事务注解扫描零命中。
- Python 未访问 `iap_*` 表；Java/Python 仍只通过内部 HTTP 通信。
- 没有引入 Redis、MQ、分布式事务或新的服务职责。

### 设计：PASS

- Entity、DTO、VO 分离；内部 BIGINT 不进入公共响应。
- 唯一约束提供并发最终防重，request_hash 提供载荷一致性。
- 两个短事务包围事务外 HTTP；FAILED/UNKNOWN 语义分离。
- 成功 sources 可持久重放，历史只包含最近五个完整成功 pair。

### 生命周期：PASS with WARNING

- PASS：生产用户上下文在 JWT 前失败关闭；测试用户只存在于测试上下文。
- PASS：数据库/HTTP Client 由 Spring 管理，没有请求级资源泄漏。
- WARNING：未在真实 MySQL 8 验证 DDL、JSON 和并发锁语义。
- WARNING：H2 2.2.224 触发 Flyway 支持版本提示。
- WARNING：本机可选 transformers 导入阻塞，Python 回归使用隔离模式；不影响 Phase 7 Java 变更，但需单独修复 Python 环境。
- WARNING：UNKNOWN 只持久化，不在本阶段实现对账恢复。

## 5. 结论

ERROR：无。Phase 7 结论：**PASS with WARNING**。WARNING 均已明确边界且不伪装为真实 MySQL 或 transformers 验证；不得据此自动进入 Phase 8。
