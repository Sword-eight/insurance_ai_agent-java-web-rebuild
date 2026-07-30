# Phase 0.5 设计骨架与测试矩阵

> 状态：**IMPLEMENTED**
>
> 本文件先用于 Skeleton Review，第二次批准后已按此范围实现。实际验证结果见
> `docs/PYTHON_BASELINE.md` 和本目录 `ReviewChecklist.md`。

## 1. 本阶段目标

用最小改动把现有 Python 工程变成可离线、快速、重复验证的迁移基线：

```text
pytest
  ├─ 纯业务：PremiumService
  ├─ 协作关系：PremiumCalculatorTool → 注入的 PremiumService
  ├─ 数据契约：BaseRetriever → RetrievalResult
  ├─ 控制流：truncate / router
  └─ 最小集成：真实 LangGraph + Mock LLM + Mock Tool
```

禁止测试触发 DeepSeek、BGE 模型加载、真实 FAISS 读写或知识库重建。

## 2. 业务接口变更

唯一业务实现变更位于 `tools/premium_calculator_tool.py`。

计划构造函数：

```python
def __init__(
    self,
    premium_service: PremiumService,
    **kwargs: Any,
) -> None:
    ...
```

不变量：

- `self._service is premium_service`；
- Tool 内部不得调用 `PremiumService()`；
- `_run()` 原样向注入对象委托五个业务参数；
- 保留当前格式化文本和异常转为文本的行为，避免扩大变更范围；
- `application/bootstrap.py` 已使用关键字 `premium_service=`，不需要改变调用语义。

替代方案“保留可选参数并在 Tool 内创建默认 Service”会继续保留隐藏生命周期，因此本阶段不采用。

## 3. pytest 文件骨架

实现阶段将移除三个会在导入期加载真实模型/索引的旧测试：

- `tests/test_dual_engine.py`
- `tests/test_langchain_retriever.py`
- `tests/test_llamaindex_retriever.py`

并建立以下测试模块。Skeleton 阶段不创建空 `test_*` 函数，避免产生零断言或跳过式伪测试。

### `tests/test_premium_service.py`

| 测试名 | 场景 | 核心断言 |
|---|---|---|
| `test_calculate_returns_expected_breakdown_and_premium` | 30 岁、男、50 万、20 年、1 类 | 公式结果、月费、breakdown 字段 |
| `test_calculate_accepts_boundary_ages` | 16 和 60 岁 | 边界均可计算 |
| `test_calculate_rejects_invalid_age` | 15 或 61 岁 | `ValueError` 与错误原因 |
| `test_calculate_rejects_invalid_gender` | 未知性别 | `ValueError` |
| `test_calculate_rejects_invalid_term` | 未知期限 | `ValueError` |
| `test_calculate_rejects_invalid_occupation` | 未知职业类别 | `ValueError` |

### `tests/test_premium_tool.py`

| 测试名 | 场景 | 核心断言 |
|---|---|---|
| `test_tool_keeps_injected_service_instance` | 注入 Mock Service | 对象身份相同 |
| `test_tool_delegates_all_arguments_to_service` | 调用 `_run()` | `calculate()` 恰好调用一次且参数未改写 |
| `test_tool_formats_service_result` | Mock 返回固定字典 | 输出包含年度/月度保费 |
| `test_tool_returns_failure_text_when_service_raises` | Mock 抛异常 | 现有错误文本行为保持 |

### `tests/test_retrieval_contract.py`

用一个只返回内存数据的 `StubRetriever(BaseRetriever)` 验证：

- 可通过抽象接口调用；
- 返回值是 `RetrievalResult`；
- 每项是 `RetrievalDocument`；
- query、engine、total、documents 和 metadata 结构保持；
- 不导入具体 LangChain/LlamaIndex Retriever。

### `tests/test_graph_nodes.py`

| 场景 | 核心断言 |
|---|---|
| 消息未超过轮数 | 内容和顺序保持 |
| 普通消息超过轮数 | 保留最近完整窗口 |
| AI tool call + `ToolMessage` | 不拆散请求与响应 |
| 截断边界落在 `ToolMessage` | 向前补齐对应 AI tool call，或按当前算法的明确契约验证 |

实现前会重新读取 `graph/nodes.py`，以真实算法为准，不为迎合测试擅改截断逻辑。

### `tests/test_graph_router.py`

| 场景 | 期望 |
|---|---|
| 最后一条 `AIMessage` 有 `tool_calls` | `"tools"` |
| 最后一条消息无 `tool_calls` | `"__end__"` |
| `messages` 为空 | `"__end__"` |

### `tests/test_graph_builder.py`

使用真实 `AgentGraphBuilder` 和 LangGraph 图，替换边界依赖：

```text
HumanMessage
  → Mock LLM 返回 AIMessage(tool_calls=[...])
  → Mock Tool 记录参数并返回固定文本
  → ToolMessage
  → Mock LLM 返回最终 AIMessage
  → END
```

核心断言：

- Mock LLM 调用两次；
- Mock Tool 调用一次且参数正确；
- 最终消息包含真实 `ToolMessage` 与最终 `AIMessage`；
- 无网络、无默认 `ChatOpenAI`、无 Retriever/Embedding/FAISS；
- 使用独立 session id，测试之间不共享可变状态。

## 4. 测试替身边界

| 对象 | 测试中使用 |
|---|---|
| `PremiumService` 业务公式 | 真实对象 |
| Router、截断算法、LangGraph 编排 | 真实代码 |
| DeepSeek LLM | Mock |
| Tool 外部协作 | Mock/记录型假对象 |
| Retriever 契约 | 内存 Stub |
| BGE、真实 FAISS、具体索引 Builder | 不实例化 |

Mock 只替换外部边界，不替换被测控制流。

## 5. 依赖拆分骨架

第二道门批准后，根据全仓库真实 import 复核并形成：

| 文件 | 范围 |
|---|---|
| `requirements.txt` | 默认 Streamlit + LangGraph + LangChain RAG 运行链 |
| `requirements-dev.txt` | pytest 等只用于开发测试的直接依赖，并引用核心清单 |
| `requirements-llamaindex.txt` | LlamaIndex 引擎和 FAISS adapter，并引用核心清单 |
| `requirements-finetune.txt` | LoRA 数据生成、训练、推理、评估的直接依赖 |

规则：

- 只加入源码真实 import 的顶层包；
- 不把 `pip freeze` 的间接依赖全部写入；
- 不借本阶段大规模升级现有版本；
- 每个新增或移动的包在最终报告中给出 import 证据和分类理由。

## 6. 文档事实修正矩阵

| 事实 | 目标表述 |
|---|---|
| 实际 Adapter | Qwen2.5-0.5B-Instruct |
| 旧 1.5B 标记 | 错误配置改为 0.5B；确属历史记录时明确标记历史遗留 |
| 单体行数 | “近 500 行单体应用”；Git 可验证重构前 `app.py` 为 497 个物理行 |
| LoRA 在线状态 | 当前未接入 DeepSeek Agent 主链路，只属于离线训练/实验能力 |

只修改能由源码或 Git 历史证明的表述，不实现 LoRA 在线推理。

## 7. 计划修改与不修改

### 第二道门后计划修改

- Premium Tool 的构造注入；
- 旧测试替换和新 pytest；
- 四类依赖清单；
- README、迁移文档及 LoRA 中错误的模型/行数/在线能力表述；
- `docs/PYTHON_BASELINE.md` 的实际验证记录；
- 本 Phase Learning Kit。

### 明确不修改

- `graph/`、`services/`、`rag/` 的目录和业务架构；
- FastAPI、Spring Boot、Java；
- Streamlit 生命周期；
- `InMemorySaver`；
- 上传、索引并发、RAG 来源、LlamaIndex 统计；
- LoRA 在线链路；
- MySQL、Redis、JWT；
- 用户现有 checkpoint、评估结果和其他未提交实验改动。

## 8. 实现顺序与停止点

第二次批准后按以下顺序执行：

1. 复核用户未提交差异并定位重叠文件；
2. 完成 pytest 与 Premium Tool DI 修复；
3. 运行快速离线测试；
4. 按真实 import 整理依赖；
5. 修正文档事实并完成运行基线验证；
6. 检查正常/异常流程、生成调用图；
7. 四维审计并完成整个 `learning/phase-0.5/`；
8. 报告结果并暂停，不进入 Phase 1、不自动提交或 push。

验收状态只能依据实际命令结果标记为 `PASS`、`WARNING` 或 `ERROR`。
