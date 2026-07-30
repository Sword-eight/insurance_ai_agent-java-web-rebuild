# Phase 0.5 知识总结

## 得到的稳定基线

- pytest 只收集根目录 `tests/`，共 22 项。
- 默认测试命令完全离线，不需要 DeepSeek Key。
- Premium Tool 保存并使用 bootstrap 注入的同一个 PremiumService。
- Retriever 抽象可以用内存 Stub 验证，不依赖具体向量引擎。
- LangGraph 控制流可以用 Mock LLM/Tool 做真实最小循环测试。
- 两个 FAISS 索引可以通过原生接口只读加载，均为 768 维。

## 最重要的设计认识

1. 测试结果正确不等于生命周期正确；DI 需要身份和协作断言。
2. Mock 应放在网络、模型、磁盘等边界，核心控制流尽量使用真实代码。
3. “项目能运行”必须写清解释器、安装命令、环境变量、命令和预期失败。
4. 可选功能依赖应与默认运行环境分离。
5. 文档中的模型、规模和调用链必须能被源码或 Git 历史证明。

## 当前仍不能宣称的能力

- 不能宣称 LoRA 已接入在线 Agent。
- 不能宣称完整 Streamlit 启动已在无 Key 环境通过。
- 不能宣称 LLaMA-Factory 本机训练环境无依赖冲突。
- 不能宣称 Python 3.10、3.11、3.13 已全部执行兼容测试。
- 不能宣称 FAISS 可读就代表检索质量正确。

## 面试口述版

“我先把旧项目中导入即执行、会加载模型和真实索引的断言迁移成 22 个离线 pytest。测试覆盖业务公式、异常参数、Tool 的真实 DI、Retriever 数据契约、消息截断、Router 和一条 Mock LLM/Tool 的真实 LangGraph 循环。然后按默认、测试、LlamaIndex、LoRA 四类拆分依赖，并只读验证两个 FAISS 索引。最终基线是 PASS with WARNING：核心离线测试通过，但重型 Streamlit 生命周期和本机训练环境冲突留给后续阶段处理。”
