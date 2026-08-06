# Phase 9.5 学习计划：Vue Minimal Chat Client

> 状态：未来阶段，尚未创建 Vue 工程
> 前置条件：Phase 6～9 完成，登录、JWT、会话、消息和聊天公共 API 稳定
> 详细范围：[Vue Web Client 规划](../../docs/WEB_CLIENT_PLAN.md)
> 总索引：[学习路线](../README.md)

## 开发前第一遍：2～3 小时

只学习 8 个直接阻塞开发的概念：浏览器与前后端分离、Vue 单文件组件、`ref`、
`props`/`emits`、Vue Router、Axios、JWT 请求头和 TypeScript 接口。目标是知道它们各自解决
什么问题，不深入 Vue 源码，也不扩展到完整前端工程化。

## 实现后第二遍：沿真实链路复盘

```text
ChatView.vue → chat.ts → http.ts → Java ChatController
```

每次只讲一个文件或节点，并用一次真实请求解释公共 Envelope、JWT、幂等键、loading、401、
503 与 `UNKNOWN`。Vue 只展示 Java 决定的状态，不直连 Python、不自动重发 AI 请求。

## 学习停止条件

- 能看懂基础 Vue 组件并修改输入框或按钮。
- 能增加一个 Java 公共 API 调用，修改一个 TypeScript DTO。
- 能解释 JWT 怎样进入请求头、401 怎样清理登录态。
- 能解释 Vue 为什么不能直连 Python。
- 能分析一次 401、503 或 `UNKNOWN`。
- 能画出 Vue→Java→Python 聊天链并完成一个小页面修改。

正式进入本 Phase 时仍须先生成 MiniCourse 和实施计划，等待批准后才创建 Skeleton；本文件不
替代阶段三道门，也不表示 Phase 9.5 已经完成。
