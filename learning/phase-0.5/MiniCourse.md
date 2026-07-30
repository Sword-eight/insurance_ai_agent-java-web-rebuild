# Phase 0.5 MiniCourse：建立可重复验证的 Python 基线

> 目标：不依赖 DeepSeek、BGE 模型或真实 FAISS 索引，也能快速验证核心 Python 行为。
> 本阶段只讲 4 个知识点，不引入 FastAPI、Java、数据库或缓存。

## 课程 1：pytest 测试为什么不能在导入时执行

### 1. 当前项目为什么需要它

当前 `tests/` 中的断言全部写在模块顶层。pytest 导入测试模块时，这些代码会立刻创建
`VectorStoreManager`、加载 Embedding、读取甚至重建真实索引。这样“收集测试”和“执行测试”
没有边界，普通电脑或没有模型的 CI 环境无法安全运行。

### 2. 它解决了什么问题

规范 pytest 测试把行为放进以 `test_` 开头的函数：

```python
def test_calculate_returns_expected_premium():
    service = PremiumService()

    result = service.calculate(...)

    assert result["annual_premium"] == 1365.0
```

pytest 先收集函数，再逐个执行和报告。每个测试可以独立准备数据、调用目标并检查结果。

### 3. 不使用会怎样

- `pytest --collect-only` 也可能加载大模型或修改索引。
- 一个模块导入失败，会让整批测试无法收集。
- 测试依赖执行顺序和本机已有文件。
- 无法清楚看到哪个业务场景失败。
- “打印成功”可能被误认为 pytest 通过。

### 4. 本项目代码会如何使用

本阶段会把旧测试改成小而独立的测试：

- `PremiumService` 测计算公式和非法参数。
- `PremiumCalculatorTool` 测注入对象身份和委托参数。
- `BaseRetriever` 测统一返回结构。
- `safe_truncate_messages` 测消息截断和 ToolMessage 配对。
- Router 测三个分支。
- Graph 用 Mock LLM 和 Mock Tool 跑一次真实 LangGraph 循环。

这些测试不导入具体 LangChain/LlamaIndex 索引实现，因此不会加载 BGE 或触碰 FAISS 文件。

### 5. 简单类比

导入期断言像“打开试卷袋时所有考生就同时开始答题”；规范 pytest 像监考员先点名，再按题目逐项考试和记录结果。

### 6. 容易踩的坑

- 测试函数里仍然调用真实 API。
- 为了“通过”写 `assert True`。
- 多个测试共享可变全局对象，导致顺序依赖。
- 用 print 代替断言。
- 只测正常流程，不测无效年龄、性别、期限和职业。
- 通过 `_run()` 测 Tool 时忘记验证 Service 收到的参数。

### 7. 当前阶段需要掌握到什么深度

能写出 Arrange–Act–Assert 三段式测试：

1. Arrange：创建对象和输入。
2. Act：只调用一个待测行为。
3. Assert：检查业务结果、异常或协作关系。

不要求掌握 fixture 高级作用域、插件或覆盖率平台。

### 8. 5～10 分钟练习

为 `PremiumService.calculate()` 手算以下输入：

```text
年龄 30、男性、保额 50 万元、20 年、1 类职业
```

根据 `premium_rate.json` 写出计算式和期望年度/月度保费，再把它改写成两个明确断言。

## 课程 2：Mock 和依赖注入如何隔离外部资源

### 1. 当前项目为什么需要它

`AgentGraphBuilder` 默认创建 `ChatOpenAI`，RAG 实现默认可能加载 BGE 和 FAISS。如果测试直接使用
这些默认依赖，就需要 API Key、网络、模型文件和索引。核心控制流反而被外部环境掩盖。

### 2. 它解决了什么问题

Mock 只模拟当前测试真正需要的协议。例如 Graph 测试中的 LLM 只需要：

```text
model_name 属性
bind_tools(tools) 方法
invoke(messages) 方法
```

第一次 `invoke()` 返回一个带 `tool_calls` 的 `AIMessage`，第二次返回最终回答。Mock Tool
记录参数并返回固定文本。这样运行的仍是真实 `StateGraph` 和 Router，但不会访问网络。

### 3. 不使用会怎样

- 测试会因为余额、网络或模型下载失败，而不是代码错误而失败。
- 测试速度从毫秒变成分钟。
- 真实索引可能被重建或删除。
- 很难构造“LLM 一定调用某个 Tool”的稳定场景。
- CI 无法复现本机结果。

### 4. 本项目代码会如何使用

Premium Tool 的正确依赖关系应是：

```text
bootstrap 创建 PremiumService
→ PremiumCalculatorTool(premium_service)
→ Tool 保存同一个对象
→ _run() 委托 service.calculate()
```

测试会验证两件事：

```python
assert tool._service is injected_service
injected_service.calculate.assert_called_once_with(...)
```

Graph 测试则验证：

```text
HumanMessage
→ Mock LLM 请求 Tool
→ Mock Tool 执行
→ ToolMessage
→ Mock LLM 返回最终 AIMessage
```

### 5. 简单类比

汽车方向盘测试不需要真的上高速。用测试台模拟车轮阻力，但方向盘、转向轴和控制逻辑仍然是真实部件。

### 6. 容易踩的坑

- Mock 掉被测类本身，最后只是在测试 Mock。
- Mock 返回的数据形状与真实 `AIMessage` 不一致。
- 只验证最终字符串，不验证 Tool 是否真的被调用。
- 构造器接收了依赖，却在内部又创建新实例。
- 一个 Mock 同时承担 LLM、Tool、Retriever 多种职责。

### 7. 当前阶段需要掌握到什么深度

能判断哪些对象应该真实、哪些应该替换：

- 真实：`PremiumService` 计算、Router、消息截断、LangGraph 图结构。
- Mock：DeepSeek LLM、Tool 外部协作、Retriever 数据源。
- 本阶段不实例化：BGE、真实 FAISS、LlamaIndex Builder。

不要求实现通用 Mock 框架。

### 8. 5～10 分钟练习

写一个只有 `calculate(**kwargs)` 方法的 Mock Service，让它返回固定保费字典。然后回答：

1. 如何证明 Tool 使用了这个对象而不是新建 Service？
2. 如何证明参数没有被 Tool 改写？
3. 如果 Service 抛出异常，Tool 当前会返回什么？

## 课程 3：为什么要拆分核心、测试、可选 RAG 和训练依赖

### 1. 当前项目为什么需要它

现在只有一个 `requirements.txt`，却混合了在线 Streamlit、LangChain、Embedding 等依赖，
同时漏掉 LlamaIndex、pytest 和 LoRA 训练依赖。结果是“安装成功”不代表某条链路真的能运行，
而只想跑单元测试的人可能被迫安装大型训练环境。

### 2. 它解决了什么问题

依赖按使用场景拆分：

```text
requirements.txt
  在线默认链路：Streamlit + LangGraph + LangChain + BGE + FAISS

requirements-dev.txt
  快速测试：pytest

requirements-llamaindex.txt
  可选双引擎：llama-index + FAISS adapter

requirements-finetune.txt
  离线训练：OpenAI SDK + torch + transformers + peft + datasets
```

用户只安装自己要运行的能力，同时每个 import 都能找到所属清单。

### 3. 不使用会怎样

- 默认安装缺 LlamaIndex，却宣称支持双引擎。
- 测试命令写好了，但环境没有 pytest。
- LoRA 脚本在运行时才发现缺 `peft` 或 `datasets`。
- 核心服务环境被训练依赖放大。
- 为解决一次导入错误而不断随意追加库。

### 4. 本项目代码会如何使用

分类必须来自真实 import：

- `langgraph` 来自 `graph/graph_builder.py`。
- `langchain-text-splitters` 来自 `rag/langchain/splitter.py`。
- `llama-index-vector-stores-faiss` 来自 `rag/llamaindex/index_builder.py`。
- `pytest` 只用于测试。
- `torch`、`transformers`、`peft`、`datasets` 只用于 LoRA 脚本。
- `openai` 直接用于数据生成和 LLM Judge，属于训练/评估工具链。

没有源码用途的库不因为“以后可能需要”而保留或新增。

### 5. 简单类比

旅行行李按场景打包：日常背包、摄影包、露营装备。不能因为将来可能露营，就每天背着帐篷上班。

### 6. 容易踩的坑

- 只看 `pip freeze`，把所有间接依赖复制进项目。
- 随意升级大版本，掩盖兼容问题。
- 把可选依赖仍然放进核心文件。
- 忘记 `-r requirements.txt`，导致可选环境缺核心依赖。
- 声称支持 Python 3.10+，却使用更高版本才有的语法。

### 7. 当前阶段需要掌握到什么深度

能说明每个顶层依赖由哪个源码 import 需要，以及为什么属于核心、开发、LlamaIndex 或 LoRA。
不要求手工锁定所有间接依赖；本阶段也不做大版本升级。

### 8. 5～10 分钟练习

从以下包中选出不应放入“快速单元测试最小环境”的包，并说明原因：

```text
pytest、torch、streamlit、llama-index、pydantic、peft
```

再回答：为什么“测试 Graph 控制流”不需要真实安装/加载 BGE 模型？

## 课程 4：运行基线是命令、前提和预期结果的组合

### 1. 当前项目为什么需要它

一句“项目能运行”缺少可验证含义。当前环境是 Python 3.12.6，但项目目标是 Python 3.10+；
Streamlit 需要 DeepSeek Key，单元测试不应需要；FAISS 验证又必须区分“检查文件存在”和
“真正反序列化索引”。

### 2. 它解决了什么问题

运行基线为每一步记录四项：

```text
命令
前置条件
预期成功结果
预期失败结果
```

例如：

```text
命令：python -m pytest
前置：安装核心 + dev 依赖
成功：全部离线单元测试通过
失败：任何网络、模型下载或真实索引访问都算测试设计错误
```

### 3. 不使用会怎样

- 开发者不知道先装哪个 requirements 文件。
- 没有 API Key 时把预期配置错误误认为代码缺陷。
- Streamlit 页面打开就被误认为聊天链可用。
- FAISS 文件存在就被误认为索引一定兼容、可加载。
- 面试时无法回答“你怎么证明项目可运行”。

### 4. 本项目代码会如何使用

本阶段计划记录：

- Python 支持版本和本机验证版本。
- 创建虚拟环境和四类安装命令。
- `python -m pytest` 的离线测试命令。
- `streamlit run app.py` 的启动命令。
- `DEEPSEEK_API_KEY`、`LOG_LEVEL`、`RAG_ENGINE` 的用途。
- 无 Key 时：离线单元测试应通过；真实聊天应明确失败，不能伪造回答。
- FAISS 最小验证先检查 `index.faiss`/`index.pkl`，再在已安装核心依赖且信任索引来源时加载；
  测试套件本身不加载真实索引。

### 5. 简单类比

运行手册不是只写“按绿色按钮”，还要写电源是否接通、正常灯是什么颜色、红灯时说明什么。

### 6. 容易踩的坑

- 把本机已有包当成 requirements 已完整。
- 测试时读取真实 `.env` 并意外调用 API。
- 为验证 FAISS 而重建生产/演示索引。
- 不记录命令退出码和测试数量。
- 阻塞时只写“未测试”，不写缺少的包、文件或权限。

### 7. 当前阶段需要掌握到什么深度

能在一台新电脑上按文档完成：

1. 创建环境；
2. 安装所需依赖；
3. 无 Key 跑离线测试；
4. 配置 Key 后启动 Streamlit；
5. 对受信任索引做最小只读验证。

不要求容器化或部署自动化。

### 8. 5～10 分钟练习

为“无 `DEEPSEEK_API_KEY`”写两条预期：

- 执行 `python -m pytest` 时应发生什么？
- 在 Streamlit 中发送真实聊天消息时应发生什么？

要求答案区分“测试基线可用”和“在线能力不可用”，不能把两者混为一谈。
