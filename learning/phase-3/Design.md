# Phase 3 Design：选择与取舍

## 1. 保留 Python 根目录，新增 `api/`

- 选择：现有 `graph/`、`tools/`、`services/`、`rag/` 原地保留，只新增薄 HTTP 包装目录。
- 原因：现有 import 和 Streamlit 基线稳定；架构明确禁止为了目录对称移动 Python 核心。
- 放弃：创建 `python-service/` 后整体搬迁现有源码。
- 代价：仓库暂时不是完全对称的双服务目录，但避免了高风险迁移。

## 2. Java 使用独立 `java-backend/` Maven 子工程

- 选择：Java 17、Spring Boot 3.3.13、Maven，只有应用入口、配置、Client 端口和内部 DTO。
- 原因：Java 当前是全新工程，独立子目录不会破坏 Python import。
- 放弃：根目录改成 Maven 多模块；提前创建所有业务包。
- 代价：当前需要分别执行 Python 与 Maven 命令，最终统一构建留给后续阶段。

## 3. Router → Facade Command，而不是 Router → Pydantic → Graph

- 选择：Router 把 Pydantic Schema 转为无框架 dataclass Command。
- 原因：Application 端口不依赖 FastAPI，Phase 4 可以在离线测试中替换实现。
- 放弃：Facade 方法直接接收 `AgentChatRequest`；Router 直接调用 Graph。
- 代价：多一次明确的字段转换。

## 4. 未实现能力返回冻结错误，不返回固定成功数据

- 选择：chat/ready 返回 503 `AI_LLM_UNAVAILABLE`，knowledge 占位返回 500 `AI_INTERNAL_ERROR`。
- 原因：两者都是 Phase 2 已冻结的内部错误码；不会新增临时稳定错误码或伪造业务成功。
- 放弃：返回 200 固定 answer；发明 `NOT_IMPLEMENTED` 契约。
- 代价：Skeleton 不能用于业务演示，但状态真实。

## 5. 只验证 multipart 形状，不做文档安全实现

- 选择：接收 `metadata` JSON 与流式 `UploadFile`，转换为 `BinaryIO` 端口后由不可用 Facade 终止。
- 原因：接口形状属于 Phase 2/3；20 MiB、PDF 签名、SHA-256 实际内容校验和路径安全属于 Phase 10。
- 放弃：Phase 3 构建真实索引或保存文件。
- 代价：当前接口不具备文档处理能力，且 readiness 明确失败。

## 6. 测试分层

```text
Schema 单元测试
→ FastAPI TestClient HTTP 边界测试
→ Uvicorn 真实进程 Smoke Test
→ Spring Context + Jakarta Validation 测试
→ Maven verify/JAR 构建
```

这组测试覆盖 Skeleton 的真实职责，但不访问外部 API、模型、数据库或索引。
