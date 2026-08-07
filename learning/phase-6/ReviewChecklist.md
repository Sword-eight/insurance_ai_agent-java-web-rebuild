# Phase 6 Review Checklist

## 1. 范围与 Git

- [x] 独立分支：`phase-6-java-python-chat`，基线为 Phase 5 提交 `89170ee`。
- [x] 开始时工作树干净；当前差异均为 Phase 6 实现、测试和 Learning Kit。
- [x] Phase 2 冻结文档无修改。
- [x] 未修改 Python Graph、Tool、RAG、Embedding、FAISS 或模型实现。
- [x] 未接 MySQL、Redis、JWT、Vue、MQ 或异步任务。
- [x] 未提交、未 push、未进入 Phase 7。

## 2. 功能验收

| 检查项 | 结果 | 证据 |
|---|---|---|
| 公共聊天入口与 DTO 校验 | PASS | MockMvc 集成测试 |
| Controller→Service→Client 分层 | PASS | 生产调用图与构造依赖 |
| Java→Python 冻结 JSON | PASS | Client 捕获请求并断言精确字段集合 |
| TraceId 端到端透传 | PASS | Client 测试 + 双进程响应回显 |
| 3s connect / 60s read timeout | PASS | 配置属性与 Client 构造 |
| 读取超时不自动重试 | PASS | 一次请求计数与 TIMEOUT 断言 |
| 内部错误安全翻译 | PASS | Service/Client 错误映射测试 |
| 空/非法响应拒绝 | PASS | 协议和 Service 结果校验 |
| 同键同载荷复用 | PASS | 双进程 200，业务 data 一致 |
| 同键不同载荷冲突 | PASS | 双进程 409 + CHAT_IDEMPOTENCY_CONFLICT |
| 来源不伪造 | PASS | 空列表与严格字段映射 |
| 进程清理 | PASS | smoke 后 8765/8766 监听器为 0 |

## 3. 实际构建与测试

### Java

```text
mvn verify
Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
生成 insurance-platform-backend-0.6.0-SNAPSHOT.jar
```

构建前曾发现本机离线 Maven clean plugin 缓存不完整；联网补齐官方依赖后，`clean verify` 从零编译 26 个生产源文件和 8 个测试源文件并通过 33/33。最终 HTTP/1.1 修复后再次 `verify` 通过，字节码检查确认 JAR 使用 `HttpClient.Version.HTTP_1_1`。

### Python

```text
python -m pytest -q
63 passed in 25.65s
```

### 双进程

```text
Python live：200
首次聊天：200 OK
同键同载荷：200 OK，same data=true
同键不同载荷：409 CHAT_IDEMPOTENCY_CONFLICT
Python internal access：200 / 200 / 409
listeners remaining：0
```

## 4. 四维审计

### 功能：PASS

- 正常、复用、冲突、校验、连接失败、读取超时和协议失败均有真实测试证据。
- 双进程 smoke 使用 Java 可执行 JAR 与 FastAPI 完成真实 HTTP 调用。

### 架构：PASS

- 依赖方向符合 `Controller → Service → Client → Python`。
- Controller 不访问 Client/Mapper；Python 不访问 Java 数据库。
- 未引入冻结架构之外的中间件或异步模型。

### 设计：PASS

- 公共与内部 DTO/Envelope 隔离，内部 message 不泄露。
- HTTP Client 为应用级共享实例，明确超时且不自动重试。
- HTTP/1.1 固定是当前 JDK Client→Uvicorn 的最小兼容适配。
- Jackson 校验访问器通过 `@JsonIgnore` 保持严格 JSON 契约。

### 生命周期：PASS with WARNING

- PASS：Client/Service/Controller 无请求级可变实例字段。
- PASS：测试后 Java/Python 进程均停止，无端口残留。
- WARNING：`RequestRegistry` 只在单个 Python 进程内有效，重启后幂等状态丢失。
- WARNING：smoke 的 Graph 是离线替身，未验证真实 DeepSeek、Embedding、FAISS 或外部网络。
- WARNING：同步聊天会占用请求线程至多约 60 秒；这是冻结 v1 设计，Phase 11 再评估容错与观测。

## 5. 结论

ERROR：无。Phase 6 结论：**PASS with WARNING**。WARNING 已明确边界且不阻止本阶段验收；不得据此自动进入 Phase 7。
