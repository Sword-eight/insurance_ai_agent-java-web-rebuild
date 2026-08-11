# Phase 10.5：Document Client Extension

> 状态：实现与验证完成，等待用户决定是否提交。
> 前置条件：Phase 9.5 客户端和 Phase 10 文档业务 API 已完成。

本阶段在现有 Vue 客户端上增加 PDF 选择、上传进度、文档列表和索引状态展示。客户端只调用
Java 公共 API，不直连 Python，也不自动恢复 `UNKNOWN`。

## 阅读顺序

1. [MiniCourse](./MiniCourse.md)
2. [Design](./Design.md)
3. [CallGraph](./CallGraph.md)
4. [TestMatrix](./TestMatrix.md)
5. [Audit](./Audit.md)
6. [LearningKit](./LearningKit.md)
7. [Interview](./Interview.md) / [Challenge](./Challenge.md)
8. [ReviewChecklist](./ReviewChecklist.md)
9. [KnowledgeSummary](./KnowledgeSummary.md)

冻结范围依据：[Vue Web Client 规划](../../docs/WEB_CLIENT_PLAN.md)。
