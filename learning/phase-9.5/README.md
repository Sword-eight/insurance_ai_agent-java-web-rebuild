# Phase 9.5 Learning Kit：Vue Minimal Chat Client

> 状态：实现、验收、提交与推送均已完成
> 前置条件：Phase 6～9 的聊天、持久化、Redis、登录与 JWT 已完成
> 范围依据：[Vue Web Client 规划](../../docs/WEB_CLIENT_PLAN.md)

## 阶段结果

- 创建 Vue 3 + TypeScript + Vite + Vue Router + Axios 客户端。
- 实现注册、登录、JWT、401 清理、会话、历史消息和同步聊天。
- 实现 loading、机械双击保护、UUID 幂等键、稳定错误与 `UNKNOWN` 展示。
- 不引入 Pinia、Element Plus、SSR、WebSocket、知识库页面或复杂状态框架。

## 学习入口

- [MiniCourse](./MiniCourse.md)
- [Design](./Design.md)
- [Test Matrix](./TestMatrix.md)
- [Call Graph](./CallGraph.md)
- [Interview](./Interview.md)
- [Challenge](./Challenge.md)
- [Knowledge Summary](./KnowledgeSummary.md)
- [Review Checklist](./ReviewChecklist.md)
- [Audit](./Audit.md)
- [Learning Kit](./LearningKit.md)

## 最短阅读顺序

```text
MiniCourse → Design → web-client/src/views/ChatView.vue
           → web-client/src/api/http.ts → CallGraph → Interview
```

本阶段到此停止，不自动进入 Phase 10。
