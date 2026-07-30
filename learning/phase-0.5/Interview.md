# Phase 0.5 面试题

## 1. 为什么把模块顶层断言改成 pytest 函数？

推荐回答：模块顶层代码在 pytest 收集阶段就会执行，旧测试因此会加载 BGE、读取甚至构建真实索引。改成独立测试函数后，收集和执行分离，每个场景能明确报告，测试也可以通过 Mock 隔离外部资源。

追问回答：`pytest --collect-only` 应只发现测试，不应发起 API、加载模型或改变文件。收集期有副作用通常说明测试边界设计错误。

## 2. 本项目为什么要验证对象身份，而不只验证计算结果？

推荐回答：`PremiumCalculatorTool` 即使重新创建 `PremiumService`，计算结果仍可能正确，但这会破坏 bootstrap 声明的生命周期。`tool._service is injected_service` 能直接证明依赖注入真实生效。

追问回答：还要验证 `calculate.assert_called_once_with(...)`，确认 Tool 没有改写参数，并且业务计算确实委托给 Service。

## 3. Mock LLM 测试到底测了什么？

推荐回答：它不测 DeepSeek 回答质量，而是测试真实 LangGraph 控制流：Agent 返回 tool call、Router 进入 tools、Tool 产生 `ToolMessage`、Agent 再次调用并结束。

追问回答：如果把 Graph 本身也 Mock 掉，就只能证明 Mock 按预设返回，无法验证边、状态合并和 Tool 循环。

## 4. 为什么单元测试不能调用真实 DeepSeek？

推荐回答：真实 API 受网络、Key、限额和模型版本影响，失败不能稳定定位为代码缺陷，还会增加成本。单元测试应验证本地确定性逻辑，真实 API 留给受控集成测试。

追问回答：集成测试也应单独标记、显式配置 Key，并设置超时，不能混入默认快速测试命令。

## 5. `safe_truncate_messages` 为什么要保护 ToolMessage 配对？

推荐回答：ToolMessage 必须响应前面某个 AI tool call。若截断后只剩 ToolMessage，模型 API 会认为协议非法。本项目既测试保留合法配对，也测试丢弃孤儿 ToolMessage。

追问回答：仅按消息数量切片不理解协议边界，因此容易在 AIMessage 与 ToolMessage 之间切断。

## 6. BaseRetriever 契约测试为什么不用两个真实引擎？

推荐回答：本阶段验证抽象返回 `RetrievalResult` 和 `RetrievalDocument` 的结构，而不是搜索质量。内存 Stub 能覆盖接口契约，同时避免 BGE、FAISS 和 LlamaIndex 环境成为测试前提。

追问回答：真实双引擎检索属于后续集成测试，需要固定测试索引和独立资源目录。

## 7. 为什么依赖要拆成四个文件？

推荐回答：在线默认链、开发测试、LlamaIndex 可选引擎和 LoRA 训练的成本不同。拆分后，跑单元测试的人不必安装训练栈，默认环境也不会因为可选功能缺包而说不清责任。

追问回答：每个可选文件通过 `-r requirements.txt` 复用核心依赖，避免复制公共版本约束。

## 8. 为什么不把 `pip freeze` 全部提交？

推荐回答：`pip freeze` 会包含操作系统、开发工具和大量间接依赖，难以说明每个包的项目用途。本阶段声明源码直接依赖，并用合理版本范围控制兼容性。

追问回答：生产部署未来可以另加锁文件，但锁文件和“顶层依赖清单”解决的是不同问题。

## 9. 无 DeepSeek Key 时什么应该成功、什么应该失败？

推荐回答：22 个离线单元测试应该成功，因为使用 Mock LLM；在线默认 LLM 客户端应明确失败，不能伪造回答。当前验证表明缺 Key 时在客户端构造阶段抛 Missing credentials。

追问回答：这也说明当前 Streamlit 初始化把配置错误暴露得较早，但它还会先加载 BGE，生命周期仍需 Phase 4 处理。

## 10. 为什么 FAISS 基线只用 `faiss.read_index()`？

推荐回答：目标是验证向量文件能被只读反序列化并记录维度、数量，不需要加载 Embedding 或 LangChain pickle。这样不会修改真实索引，也规避不受信 pickle 风险。

追问回答：当前两个索引都是 768 维，向量数分别是 23 和 19；这只证明文件可读，不证明检索质量。

## 11. `pytest.ini` 为什么限制 `testpaths`？

推荐回答：仓库目录里有被 Git 忽略的第三方 LLaMA-Factory，它也包含大量测试。默认递归会把第三方测试当成本项目测试，导致收集慢和依赖冲突。`testpaths=tests` 明确了项目测试边界。

追问回答：这不是隐藏失败，因为第三方源码不是当前项目测试目标；它自己的测试应在独立环境中运行。

## 12. 这次基线为什么是 PASS with WARNING？

推荐回答：核心目标已完成：DI 修复、22 个离线测试、依赖拆分、事实修正、FAISS 只读验证都通过。WARNING 来自未执行重型 Streamlit 完整启动，以及本机双 Python PATH和可选训练环境的已有依赖冲突；没有 Phase 0.5 范围内的 ERROR。

追问回答：WARNING 已有明确证据和后续归属，不影响进入 Review，但不能被表述为全系统已可生产运行。
