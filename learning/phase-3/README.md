# Phase 3 Learning Kit：双服务 Skeleton

## 阶段结论

Phase 3 已建立可构建、可启动、可测试的双服务骨架：

- Python FastAPI 暴露 Phase 2 冻结的 6 个内部路径；
- Java 使用 Java 17、Spring Boot 3.3.13 与 Maven，提供应用入口、内部 DTO 和 Client 端口；
- 两端均未接入真实聊天、知识库、MySQL、Redis 或认证链路；
- 未实现能力显式失败，不返回伪造 AI 结果；
- 未修改 Phase 2 冻结文档与现有 Python AI 核心。

阶段审计结论：**PASS with WARNING**，无 ERROR。WARNING 是 FastAPI readiness/业务 Facade
仍未接入真实资源，以及本机 Maven 未持久加入当前终端 PATH；前者属于 Phase 4，后者不影响已取得的实际构建证据。

## 交付导航

| 文件 | 用途 |
|---|---|
| `MiniCourse.md` | 4 个阶段必需概念 |
| `KnowledgeSummary.md` | 一页知识摘要 |
| `Interview.md` | 面试高频问答 |
| `Design.md` | 关键设计选择与取舍 |
| `Challenge.md` | 练习与自检题 |
| `CallGraph.md` | 当前真实启动与请求调用图 |
| `ReviewChecklist.md` | 构建、测试和四维审计证据 |

## 已执行验证

```text
python -m compileall -q api application tests
结果：exit 0

python -m pytest -q
最终结果：39 passed

python -m pytest tests/test_api_contracts.py tests/test_api_skeleton.py -q
最终复验：17 passed

python -m uvicorn api.main:app --host 127.0.0.1 --port 8765
GET /internal/v1/health/live
结果：HTTP 200，success=true，status=UP

mvn -B verify
结果：BUILD SUCCESS；5 tests，0 failures，0 errors，0 skipped；生成可执行 JAR
```

Python 测试不访问网络、API Key、BGE 或真实 FAISS。Maven 首次运行下载了依赖；最终复验使用缓存依赖并取得退出码 0。

## 阶段边界

Phase 3 到此停止。以下内容尚未实现：

- Phase 4：FastAPI lifespan 调用 bootstrap、真实 Agent/Knowledge Facade、requestId 短期防重；
- Phase 5：Java 统一公共响应、异常、校验与 OpenAPI；
- Phase 6：Controller → Service → AgentClient → Python 聊天链；
- Phase 7～10：MySQL、Redis、JWT、文档生命周期与索引安全；
- Phase 11：真实超时、重试、熔断和可观测性验证。
