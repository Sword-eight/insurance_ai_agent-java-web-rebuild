# Phase 4 Learning Kit：FastAPI 最小包装

## 阶段结论

Phase 4 已把 Phase 3 HTTP Skeleton 接到现有 Python AI 对象图，并保持冻结架构边界：

- FastAPI lifespan 在进程启动时创建一次 Runtime，请求复用同一组 Facade、Graph 和 Service；
- Agent Router 通过 Facade 调用现有 LangGraph，接收有限历史并返回真实最终 AIMessage；
- requestId 登记表提供容量限制、10 分钟 TTL、并发原子注册、完成/失败缓存和冲突检测；
- 每次 HTTP Graph 执行使用 requestId 隔离 checkpoint，并在结束后清理临时状态；
- live 与 ready 分离，初始化失败时不泄露异常细节；
- Knowledge status 只返回安全统计；上传索引和 rebuild 因 Phase 10 数据安全边界而明确失败。

阶段结论：**PASS with WARNING**，无 ERROR。WARNING 是真实 DeepSeek/Embedding/FAISS 启动链未在离线测试中执行，以及知识写入、索引并发和完整 readiness 探针仍属于后续冻结阶段。

验证结果：Python `63 passed`；Java `5 tests`、零失败、`BUILD SUCCESS`；Python compileall 与 `git diff --check` 均为退出码 0。

## 交付导航

| 文件 | 用途 |
|---|---|
| `MiniCourse.md` | 本阶段 5 个必需概念 |
| `KnowledgeSummary.md` | 一页知识摘要 |
| `Interview.md` | 面试高频问答 |
| `Design.md` | 设计选择、替代方案与边界 |
| `Challenge.md` | 编码与推演练习 |
| `CallGraph.md` | 当前真实调用图与异常分支 |
| `ReviewChecklist.md` | 构建、测试与四维审计证据 |

## 阶段边界

本阶段不修改 Phase 2 冻结文档，不修改 Java 业务代码，不实现 Java→Python 调用、MySQL、Redis、JWT、MQ、异步 taskId、文件落盘、索引重建并发或生产韧性策略。Phase 4 到此停止，不自动进入 Phase 5。
