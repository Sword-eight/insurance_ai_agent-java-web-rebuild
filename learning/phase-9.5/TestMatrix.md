# Phase 9.5 Test Matrix

| 层级 | 场景 | 结论 |
|---|---|---|
| TypeScript | Vue SFC、DTO、API、路由严格类型检查 | PASS |
| Session | 保存 JWT 与公开用户，不保存密码 | PASS |
| Session | logout/401 清除响应式状态与 `sessionStorage` | PASS |
| Envelope | `OK + data` 解包；空 data 拒绝 | PASS |
| Error | 401 清理登录态并触发跳转 | PASS |
| Error | 504 显示 `UNKNOWN` 指引与 traceId | PASS |
| Auth UI | 登录成功保存状态并进入聊天页 | PASS |
| Auth UI | 注册密码不一致时不调用 Java | PASS |
| Auth UI | 注册成功返回登录页且不持久化密码 | PASS |
| Chat API | 调用 Java `/chat/messages` 并携带幂等 Header | PASS |
| Chat UI | Enter 发送、Shift+Enter 换行 | PASS |
| Chat UI | 请求中禁用输入与发送 | PASS |
| Chat UI | 连续提交只发起一次同步聊天 | PASS |
| Chat UI | 超时不自动重试，显示稳定错误与 traceId | PASS |
| Build | Vite 生产构建 | PASS：99 modules transformed |
| Dependency | npm high/critical 漏洞审计 | PASS：0 vulnerabilities |
| HTTP smoke | Vite `/login` 返回 HTML | PASS |
| Browser | 真实浏览器点击、截图和视口检查 | WARNING：浏览器运行时无可用实例，未执行 |
| Live integration | Vue→真实 Java→MySQL/Redis→Python | 未在本阶段重复执行；Phase 6～9 后端链已有测试，本阶段 API 使用受控测试替身 |

## 实际命令

```text
npm run typecheck
npm test
npm run build
npm audit --audit-level=high
```

- 有效类型检查：退出码 0。
- 最终 Vitest：6 files，14 tests，全部通过，无 skipped。
- 最终构建：退出码 0。
- 首次便携 Node 类型检查因子进程 PATH 未包含 `node` 而未进入编译；仅对当前命令注入 D 盘
  Node 路径后通过，没有修改系统 PATH。
