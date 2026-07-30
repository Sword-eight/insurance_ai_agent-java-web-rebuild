# Phase 0.5 练习

## 1. 手写 PremiumService 异常测试

不复制现有代码，手写一个参数化 pytest，同时覆盖非法性别、期限和职业类别。要求断言异常类型和错误信息片段。

## 2. 修改 DI 并让测试先失败

在临时练习分支中把 Tool 改回内部 `PremiumService()`，运行 `test_tool_keeps_injected_service_instance`，解释为什么计算结果测试可能仍通过，而身份测试会失败。练习后恢复代码。

## 3. 调试孤儿 ToolMessage

构造只有 `ToolMessage`、没有前置 AI tool call 的消息列表，单步执行 `safe_truncate_messages`，画出 `tool_call_ids` 和 `cleaned` 的变化。

## 4. 扩展 Router 测试

新增一个最后消息是普通 `AIMessage(content="直接回答")` 的用例。口述它与 `HumanMessage` 无 tool call 用例在语义上的区别，以及为什么路由结果相同。

## 5. 画最小 Graph 调用图

不看 `CallGraph.md`，画出 Human → Agent → Router → Tool → Agent → END，并在每条边标注承载的消息类型。

## 6. 制造并定位依赖污染

临时去掉 `pytest.ini` 的 `testpaths`，执行 `pytest --collect-only`，观察第三方 LLaMA-Factory 被收集。说明这是测试失败、依赖失败还是测试边界失败。

## 7. 口述运行基线

用两分钟回答：新电脑如何确认 Python、安装依赖、无 Key 跑测试、配置 Key 启动 Streamlit，以及如何只读验证 FAISS。必须同时说出成功标准和失败含义。
