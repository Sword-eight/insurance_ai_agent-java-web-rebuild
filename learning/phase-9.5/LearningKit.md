# Phase 9.5 Learning Kit

## 一句话复盘

Vue 用严格公共 DTO 调 Java，Axios 集中携带 JWT，Router 改善登录体验，聊天页面用 UUID 幂等
键和 pending 状态阻止重复发送，但真正的认证、授权和幂等仍属于 Java/MySQL。

## 当天最值得学习的 6 个文件

| 文件 | 调用链位置 | 输入与输出 | 依赖 | 面试追问 | 建议小修改 |
|---|---|---|---|---|---|
| `src/types/api.ts` | Java/TS 契约边界 | JSON 字段类型 | 无 | 泛型 Envelope 为什么不是 any | 为一个 DTO 增加只读展示字段 |
| `src/api/http.ts` | 所有 API 之前/之后 | Axios config / `ApiClientError` | Axios、session | 401 与 403 如何分流 | 增加 429 Retry-After 展示模型 |
| `src/auth/session.ts` | 登录与受保护路由之间 | `LoginView` / token+user | Vue reactive、sessionStorage | XSS 与存储权衡 | 增加 token 到期时间的纯展示判断 |
| `src/router/index.ts` | 页面入口 | route / redirect | Vue Router、session | 前端守卫为何不安全 | 增加无 Token 路由单测 |
| `src/views/ChatView.vue` | UI 业务编排 | 用户操作 / 页面状态 | conversation/chat API | 幂等、UNKNOWN、事实来源 | 增加会话本地筛选 |
| `src/components/ChatInput.vue` | 聊天输入末端 | props / emits | Vue | 双击保护为何不够 | 增加剩余字符计数 |

## 学习顺序

1. 从 `types/api.ts` 看清 Java 公共契约。
2. 沿 `LoginView -> auth.ts -> http.ts` 理解 JWT。
3. 沿 `ChatView -> chat.ts -> Java ChatController` 理解幂等与同步调用。
4. 回到 `MessageList` 理解为什么 UI 不成为长期消息事实来源。
5. 用 `Interview.md` 口述 401、403、503、504/UNKNOWN。

## 验收命令

```text
npm ci
npm run typecheck
npm test
npm run build
npm audit --audit-level=high
```

Node 与 npm 缓存可放在仓库外的 D 盘；不得提交本机绝对路径、`node_modules`、构建产物、Token
或 `.env`。开发时只让 Vite proxy 指向 Java，不配置 Python 地址。
