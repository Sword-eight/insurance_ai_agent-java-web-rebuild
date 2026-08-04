# Phase 3 Review Checklist

## 1. 范围与 Git

- [x] 当前分支：`java-web-rebuild`。
- [x] 修改前检查工作区和 Phase 2 既有差异。
- [x] 未覆盖 `docs/API.md`、`DATABASE.md`、`DECISIONS.md`、`REDIS.md`、`TECH_DEBT.md` 或 `learning/phase-2/`。
- [x] 未提交、未 push、未进入 Phase 4。
- [x] Maven `target/` 已忽略，构建产物不进入版本控制。

## 2. 功能验收

| 检查项 | 结果 | 证据 |
|---|---|---|
| Python 应用可导入 | PASS | compileall exit 0 |
| Uvicorn 可启动 | PASS | 真实进程 liveness HTTP 200 |
| 冻结内部路径齐全 | PASS | OpenAPI 枚举 6 个 `/internal/v1` 路径 |
| TraceId/UUID/message/history 校验 | PASS | Python Schema/HTTP 测试 |
| multipart Skeleton | PASS | metadata/file 绑定后明确失败 |
| Java Spring Context | PASS | SpringBootTest 1/1 |
| Java Client DTO/端口 | PASS | Jakarta Validation 与接口测试 4/4 |
| JAR 构建 | PASS | Maven `BUILD SUCCESS` |
| 真实 AI/DB/Redis | 不属于本阶段 | 未接入且未伪造 |

## 3. 测试证据

### Python

```text
全量最终复验：39 passed
Phase 3 最终针对性复验：17 passed
compileall：exit 0
```

正常流程：liveness、合法 Schema、合法 Java DTO、Spring Context。

异常流程：缺失 TraceId、非法 UUID、空/超长 message、不完整 history、超总预算、AI 未 ready、Agent/Knowledge Facade 未接入。

### Java

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
生成 insurance-platform-backend-0.3.0-SNAPSHOT.jar
```

第一次 Maven 执行因默认本地仓库被错误解析到不可写根目录而失败；显式使用当前用户 Maven 仓库后完成依赖下载。首次 verify 超过工具等待窗口，但生成了 5/5 通过报告和 JAR；使用缓存依赖复验取得明确退出码 0 与 `BUILD SUCCESS`。上述失败未被省略。

## 4. 四维审计

### 功能：PASS

- Java/Python Skeleton 均可构建和启动。
- 正常与异常边界均有真实测试。
- 占位能力没有伪装成业务成功。

### 架构：PASS

- Python HTTP Skeleton 静态检查没有导入 `graph/tools/services/rag/memory`。
- Router → Facade 端口；没有 Router → Graph/FAISS。
- Java 只有 Client 端口，没有 Controller 绕过 Service 的可能链路。
- 没有 MySQL、Redis、MyBatis、JWT、MQ 或异步 task 依赖。
- 现有 Python 核心目录未移动、重命名或修改。

### 设计：PASS

- Java Client DTO、Python Pydantic Schema、Application Command 分离。
- Client/Facade 接口均有明确后续调用方，没有引入完整 DDD 或通用任务抽象。
- 内部错误使用 Phase 2 已冻结错误码，没有新增临时稳定契约。
- 文件内容通过 `BinaryIO` 端口传递，不引入共享绝对路径。

### 生命周期：PASS with WARNING

- PASS：FastAPI lifespan 挂载点存在且每个请求不创建 AI 对象；Spring Context 可独立启动。
- PASS：live 与 ready 分离；未初始化资源时 ready 明确为 503。
- WARNING：真实 bootstrap、资源关闭、requestId registry 和并发安全尚未实现，分别属于 Phase 4/10。
- WARNING：本机 Java/Maven 已安装并完成验证，但当前终端 PATH 未持久刷新；本次使用显式工具路径，未写入仓库配置。

## 5. 最终结论

ERROR：无。

Phase 3 结论：**PASS with WARNING**。WARNING 均为后续冻结阶段的预期实现差距或本机工具路径问题，不改变 Phase 3 Skeleton 验收；不得据此宣称聊天、知识库或 Java→Python 端到端链路已经实现。
