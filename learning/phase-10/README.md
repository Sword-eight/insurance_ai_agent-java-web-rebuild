# Phase 10：PDF 与知识库管理

> 状态：实现与验证完成，等待用户决定是否提交。

Phase 10 增加了 PDF 上传、本人文档查询、Java 原文件与 MySQL 元数据管理，以及 Java 到
Python 的流式索引链路。Java 保存业务事实，Python 管理解析、Embedding 和 FAISS 派生索引。

## 阅读顺序

1. [MiniCourse](./MiniCourse.md)：开发前必须掌握的五个概念；
2. [Design](./Design.md)：边界、状态机与一致性设计；
3. [CallGraph](./CallGraph.md)：正常和异常调用链；
4. [TestMatrix](./TestMatrix.md)：验收覆盖面；
5. [Audit](./Audit.md)：真实命令证据与四维审计；
6. [LearningKit](./LearningKit.md)：代码地图和面试复盘入口；
7. [Interview](./Interview.md) / [Challenge](./Challenge.md)：问答与动手练习；
8. [ReviewChecklist](./ReviewChecklist.md)：Review 清单；
9. [KnowledgeSummary](./KnowledgeSummary.md)：一页知识摘要。

本阶段没有实现公开删除、异步任务、MQ、分布式锁或 Vue 文档页面；这些不属于冻结的
Phase 10 范围。
