# Phase 10.5 学习计划：Document Client Extension

> 状态：未来阶段，尚未实现文档客户端
> 前置条件：Phase 9.5 客户端和 Phase 10 文档业务 API 已完成并稳定
> 详细范围：[Vue Web Client 规划](../../docs/WEB_CLIENT_PLAN.md)
> 总索引：[学习路线](../README.md)

## 必需概念

1. 浏览器文件选择与 `multipart/form-data`。
2. Axios 上传进度或基础 loading。
3. Java 公共 `DocumentData` 与索引状态的 TypeScript 建模。
4. 上传失败、502/503 与 `UNKNOWN` 的只展示边界。
5. `Vue → Java DocumentService → Python Knowledge API` 的职责分离。

## 验收式学习目标

- 能说明文件如何从 `DocumentView` 进入 Java，而不是直达 Python。
- 能修改文件选择、上传按钮或一个文档 DTO 字段。
- 能解释文档业务状态由 Java 持久化、FAISS 索引由 Python 管理。
- 能区分上传 loading、明确失败与结果暂时无法确认。

本阶段只扩展 Phase 9.5 的现有客户端，不重新设计前端。正式开始时仍须按三道门生成
MiniCourse、Skeleton 和最终 Learning Kit。
