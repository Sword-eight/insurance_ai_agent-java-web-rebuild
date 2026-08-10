# Phase 9.5 Design

## 关键选择

1. 客户端固定为 Vue 3、TypeScript、Vite、Vue Router、Axios；不引入 Pinia 或 UI 框架。
   少量共享认证状态由一个组合式模块管理，代价是未来状态增多时可能需要重新评审。
2. 所有 API 使用相对 `/api/v1`。开发环境由 Vite proxy 转发 Java，生产环境要求同源反向代理；
   客户端永远不知道 Python 地址，也不需要修改 Java CORS。
3. 公共 `ApiResponse<T>`、分页和各业务 DTO 精确映射 Java record。类型禁止用 `any` 混合不同
   `data`，但仍防御非 `OK` 与空 `data`。
4. JWT 与公开用户视图保存到 `sessionStorage`，关闭标签页即清除。它比长期存储缩短暴露周期，
   但不能消除 XSS 风险；Java 仍是唯一安全边界。
5. 每次用户明确发送生成 UUID 幂等键，请求中禁用再次发送。网络错误、503、504 与
   `UNKNOWN` 不自动重发，避免制造第二次 AI 调用。

## UI 状态

```text
route guard -> login/register | protected chat
chat: idle -> loading -> success | stable error
401: any request -> clear session -> login
```

Vue 不保存长期会话事实。消息发送成功后优先重新读取 Java 历史；只有刷新失败时临时展示本次
真实响应，并提示用户重新选择会话。

## 代价与边界

- 不实现 refresh token、复杂 Markdown、流式输出、移动端完整适配或文档页面。
- 不自动恢复 `UNKNOWN`，不在客户端推断后端 `SUCCEEDED/FAILED`。
- 当前开发 proxy 固定 Java 本地端口；生产部署由同源网关负责 `/api` 路由。
