# Phase 9.5 Knowledge Summary

```text
Vue View -> typed API module -> Axios -> Java /api/v1
                               Authorization: Bearer JWT
                               Idempotency-Key: UUID (chat only)
```

- Vue 负责表单、展示、loading 和机械重复提交保护，不负责权限事实或聊天状态机。
- `ApiResponse<T>` 映射 Java 公共 Envelope；登录、会话、消息和聊天使用不同 `T`。
- Axios request interceptor 附加 JWT；response interceptor 只对 401 清理 session 并跳转。
- Vue Router guard 是体验层，不是安全边界。
- JWT 放在当前标签页 `sessionStorage`，不写日志、不进 URL、不保存密码。
- 聊天为同步请求。每个新请求生成幂等 UUID，pending 时禁用发送，失败不自动重发。
- 504/`UNKNOWN` 明确提示结果无法确认；Vue 不把它改成 FAILED。
- 成功后重新读取消息，因为 Java/MySQL 是唯一长期事实来源。
- Vite 开发代理和生产同源代理都只把 `/api` 发给 Java，浏览器不获得 Python 地址。
- 当前非目标：Pinia、Element Plus、文档页面、SSR、WebSocket、流式输出、复杂 Markdown。
