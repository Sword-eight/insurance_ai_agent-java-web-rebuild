# Insurance AI Platform 学习路线索引

> 正式交付阶段：16 个
> 当前说明：Phase 0～11 已完成实现与验证；Phase 12 为规划
> 事实来源：[架构](../docs/ARCHITECTURE.md) / [迁移路线](../docs/MIGRATION_PLAN.md) /
> [Vue 客户端规划](../docs/WEB_CLIENT_PLAN.md)

当前仓库没有正式的 Phase 0.75。分支与迁移隔离属于 Git 工作流事实，不追补成一个没有原始
阶段交付物的 Phase。新增的正式阶段是 Phase 9.5 和 Phase 10.5。

| 顺序 | 阶段 | 状态 | 学习入口 |
|---:|---|---|---|
| 1 | Phase 0 源码审计 | 已完成 | [MiniCourse](./phase-0/MiniCourse.md) |
| 2 | Phase 0.5 测试基线 | 已完成 | [Learning Kit](./phase-0.5/README.md) |
| 3 | Phase 1 架构冻结 | 已完成 | [Learning Kit](./phase-1/README.md) |
| 4 | Phase 2 API / Database / Redis 契约 | 已完成 | [Learning Kit](./phase-2/README.md) |
| 5 | Phase 3 Java 与 FastAPI Skeleton | 已完成 | [Learning Kit](./phase-3/README.md) |
| 6 | Phase 4 Python FastAPI 最小包装 | 已完成 | [Learning Kit](./phase-4/README.md) |
| 7 | Phase 5 Java Spring Boot 基础工程 | 已完成 | [Learning Kit](./phase-5/README.md) |
| 8 | Phase 6 Java→Python 聊天链 | 已完成 | [Learning Kit](./phase-6/README.md) |
| 9 | Phase 7 MySQL 会话与消息 | 已完成 | [Learning Kit](./phase-7/README.md) |
| 10 | Phase 8 Redis | 已完成 | [Learning Kit](./phase-8/LearningKit.md) |
| 11 | Phase 9 用户、登录与 JWT | 已完成 | [Learning Kit](./phase-9/LearningKit.md) |
| 12 | Phase 9.5 Vue Minimal Chat Client | 已完成 | [Learning Kit](./phase-9.5/README.md) |
| 13 | Phase 10 PDF 与知识库管理 | 已完成 | [Learning Kit](./phase-10/LearningKit.md) |
| 14 | Phase 10.5 Document Client Extension | 已完成 | [Learning Kit](./phase-10.5/LearningKit.md) |
| 15 | Phase 11 可观测性与容错 | 已完成实现与验证，待提交 | [Learning Kit](./phase-11/LearningKit.md) |
| 16 | Phase 12 测试、联调与最终审计 | 规划 | 本阶段开始时生成 |

依赖关系不可调换：

```text
聊天 + 会话/消息 + Redis + JWT → Phase 9.5
Phase 9.5 + 文档业务 API → Phase 10.5
Vue + Java + Python 完成 → Phase 11/12 端到端验证
```

未来 Phase 的 README 只是学习计划，不表示代码或验收已经完成。每个 Phase 仍按
`AGENTS.md` 的 MiniCourse、Skeleton Review、实现与最终 Learning Kit 三道门执行。
