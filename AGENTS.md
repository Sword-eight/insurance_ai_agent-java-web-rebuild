# Insurance AI Platform 协作规则（草案）

> 状态：生效。规则适用于仓库根目录及全部子目录。

## 事实来源与阶段

- `docs/ARCHITECTURE.md` 在 Phase 1 冻结后是架构唯一事实来源。
- 若代码与 `docs/ARCHITECTURE.md` 不一致，先报告架构漂移，不自行选边或修正。
- 每次只处理一个 Phase；未经用户明确批准，不进入下一阶段。
- 不修改当前 Phase 之外的业务模块，不借机重构。

## 每个 Phase 的三道门

1. 开始前：读取架构和相关源码，给出计划、修改/不修改文件、验收标准，并先生成本阶段 MiniCourse；随后等待批准。
2. 首次批准后：只生成接口和 Skeleton；随后等待 Review。
3. 再次批准后：实现、构建、测试正常与异常流程、生成调用图、执行四维审计并生成 Learning Kit；随后停止。

Phase 0 只有源码审查与文档草案，不创建业务 Skeleton。

## 架构边界

- Java：Controller → Service → Client / Mapper → Python AI Service / MySQL / Redis。
- Controller 不直接调用 Python Client 或 Mapper；Entity、DTO、VO 分离。
- Python 保留 `graph/`、`tools/`、`services/`、`rag/` 的现有语义和调用关系。
- LangGraph、Tool Calling、RAG、Embedding、FAISS、DeepSeek、LoRA 和索引构建留在 Python。
- Python 不访问 Java 业务数据库；Java 与 Python 仅通过内部 HTTP API 通信。
- 当前版本聊天保持同步；MQ、Worker、异步 `task_id` 只进入演进文档，不实现。

## 变更原则

- 遵守 YAGNI、SOLID、单一职责、依赖倒置、最小改动和可运行优先。
- 不伪造功能、测试、指标或运行结果；文档声明必须有源码或实际命令证据。
- 不为统一分层而粗暴移动 Python 核心目录，不引入无真实需求的抽象。
- 不引入 MQ、Nacos、Kubernetes、分布式事务或完整 DDD。
- 改变冻结架构前，先说明原因、范围、收益、成本、替代方案和兼容性，等待批准。

## Git 与安全

- 工作必须在独立分支进行；修改前检查 `git status`，保留用户已有改动。
- 不执行破坏性 Git 命令，不重写历史，不自动 push。
- Phase 通过验收后再建立独立提交；Review 前不自行提交。
- 密钥、Token、密码和真实用户数据只通过环境变量或本地忽略文件配置。
- 不提交 `.env`、模型权重、索引、checkpoint、日志或本机绝对路径配置。

## 验收

- 每阶段按功能、架构、设计、生命周期四维审计，结论为 PASS / WARNING / ERROR。
- ERROR 未解决不得进入下一阶段。
- 测试必须真实执行；跳过、依赖本机状态或仅在导入期运行的“测试”必须明确披露。

## 面试冲刺模式

当用户明确宣布进入面试冲刺模式时，执行
`docs/CODEX_WORKFLOW.md` 中的冲刺流程。

冲刺模式只压缩审批和文档篇幅，不降低以下要求：

- 不违反冻结架构
- 不覆盖用户修改
- 功能必须真实运行
- 测试必须实际执行
- 不伪造已实现能力
- 出现 ERROR 不得继续