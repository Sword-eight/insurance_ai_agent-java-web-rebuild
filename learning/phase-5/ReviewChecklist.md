# Phase 5 Review Checklist

## 1. 范围与 Git

- [x] 开始时 `java-web-rebuild` 工作区干净，HEAD 为 Phase 4 `8e66c04`。
- [x] 只修改 Java 公共基础设施、配置、测试和 `learning/phase-5/`。
- [x] Phase 2 冻结文档无差异。
- [x] Python 源码、现有 Client 端口/DTO、Phase 6 业务模块未修改。
- [x] 未提交、未 push、未进入 Phase 6。
- [x] `target/` 被忽略，可执行 JAR 不进入提交。

## 2. 功能验收

| 检查项 | 结果 | 证据 |
|---|---|---|
| 固定成功/失败 Envelope | PASS | ApiResponse 单元测试 |
| 17 个冻结公共错误码 | PASS | 完整枚举顺序与关键 HTTP 映射断言 |
| Bean Validation 在方法前失败 | PASS | invocation count 保持 0 |
| 非法 JSON | PASS | HTTP 400 + VALIDATION_ERROR |
| 业务异常 | PASS | HTTP 409 + CHAT_IDEMPOTENCY_CONFLICT |
| 不支持媒体类型 | PASS | HTTP 415 + UNSUPPORTED_MEDIA_TYPE |
| 未知异常 HTTP 脱敏 | PASS | body 不含路径、文件名或异常类型 |
| 未知异常日志脱敏 | PASS | 日志含 trace/type，不含 exception message |
| TraceId 合法/缺失/非法 | PASS | 原样复用或生成规则测试 |
| Header/Envelope 一致 | PASS | MockMvc 响应对比 |
| MDC 生命周期 | PASS | 请求结束后 MDC key 为 null |
| OpenAPI 公开路径 | PASS | 默认与 public-v1 文档均排除 internal path |
| 无 DB/Redis/Python 启动 | PASS | SpringBootTest Context |

## 3. 实际构建与测试

### Java 最终验证

```text
mvn -B -o verify
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
生成 insurance-platform-backend-0.5.0-SNAPSHOT.jar
JAR 大小：23,603,584 bytes
```

首次联网构建下载 springdoc 依赖后为 18/18 通过；加入全局 OpenAPI 与 TraceId 边界测试后，最终离线 verify 为 20/20。

### Python 回归

```text
pytest --collect-only：63 tests collected
第一批：42 passed
第二批：21 passed
合计：63 passed，0 failed，0 skipped
```

一次 `python -m pytest -q` 在本机资源争用期间运行 180.8 秒后被工具终止且没有结果，因此未被记录为通过。随后按收集清单拆成互斥的两个批次，覆盖全部 63 项并分别取得退出码 0。

## 4. 四维审计

### 功能：PASS

- Envelope、校验、异常、TraceId 与 OpenAPI 均有正常和异常测试。
- 未知错误明确失败，不返回内部 exception message。

### 架构：PASS

- 只增加 `common` 与 `config` 基础职责；没有生产业务 Controller。
- 未调用 AgentClient/KnowledgeClient，没有 Controller→Client 或 Controller→Mapper 旁路。
- 未引入 MySQL、Redis、JWT、MQ、taskId、Python AI 核心依赖。

### 设计：PASS

- ErrorCode 是冻结有限集合；没有新增临时公共 code。
- Validation、业务异常和未知异常职责分离。
- OpenAPI 只公开 `/api/v1/**`，API-only starter 避免无需求 UI。
- 测试接口只存在于 test source，不进入生产 JAR。

### 生命周期：PASS with WARNING

- PASS：Filter、Advice、Config 是无请求可变字段的单例。
- PASS：TraceId 仅存方法局部/request/MDC，并在 finally 清理。
- PASS：Spring Context 不依赖 DB、Redis 或 Python 服务启动。
- WARNING：MDC 不会天然跨异步线程传播；当前同步 v1 不使用异步，未来若引入必须单独设计。
- WARNING：Java→Python TraceId 传递和完整观测属于 Phase 6/11，不能由本阶段测试冒充。
- WARNING：本机资源争用造成一次 Python 单命令超时；拆分后的完整回归已通过。

## 5. 结论

ERROR：无。Phase 5 结论：**PASS with WARNING**。WARNING 均为已冻结的后续工作或已披露的本机验证条件，不阻止 Phase 5 验收，但不得据此自动进入 Phase 6。
