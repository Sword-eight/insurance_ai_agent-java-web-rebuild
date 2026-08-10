# Phase 9.5 Review Checklist

- [x] Vue 仅请求 Java `/api/v1`，不存在 Python 地址。
- [x] Java 公共 Envelope、分页和 DTO 有明确 TypeScript 类型，无 `any` 业务模型。
- [x] JWT 只进入 Authorization Header；密码和 Token 不进入日志。
- [x] 401 清理登录态并跳转；403 保留业务错误语义。
- [x] 会话创建、切换、历史查询与同步聊天链已实现。
- [x] 每个聊天请求携带 UUID 幂等键，请求中禁止重复提交。
- [x] 504/UNKNOWN、503 等错误不触发自动重试并展示 traceId。
- [x] 未引入 Pinia、Element Plus、SSR、WebSocket 或知识库页面。
- [x] Typecheck、14 个 Vitest、Vite build 与 npm audit 实际通过。
- [ ] 真实浏览器点击/截图：环境无可用 Browser 实例，未执行并已披露。
- [x] `AGENTS.md`、`docs/CODEX_WORKFLOW.md` 用户改动未覆盖。
- [x] Java/Python、冻结 API/架构和数据库/Redis 契约未修改。
