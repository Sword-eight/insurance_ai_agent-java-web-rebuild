# Phase 9.5 MiniCourse：Vue Minimal Chat Client

> 状态：开发前必修；只覆盖阻塞本阶段实现的 5 个概念。

## 1. Vue 单文件组件与响应式状态

Vue 单文件组件把模板、TypeScript 逻辑和局部样式放在一个 `.vue` 文件中。本阶段使用
`<script setup lang="ts">` 与 Composition API：`ref` 保存表单、loading、错误、当前会话和
消息列表；计算结果由 `computed` 派生，不重复保存同一事实。

组件拆分只服务真实交互：页面负责加载和编排，`ConversationList`、`MessageList`、`ChatInput`
通过 `props` 接收数据、通过 `emits` 报告用户操作。子组件不直接修改父组件状态，也不自行
调用后端。

面试要点：响应式状态变化驱动 DOM 更新；组件输入单向流动，事件向上报告，减少隐藏耦合。

## 2. TypeScript 公共契约与 Java Envelope

Vue 只依赖 Java 公共 DTO，不解析 Python `InternalEnvelope`。公共外层定义为泛型：

```ts
interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
  traceId: string
  timestamp: string
}
```

登录、会话、分页消息和聊天响应分别拥有自己的 `data` 类型；不能因为外层统一就把内部字段
写成 `any`。Java UUID 与 UTC 时间在浏览器边界都是字符串，页面只在展示时格式化时间。

面试要点：TypeScript 类型提供编译期约束，不会替代运行时校验；客户端仍要防御空 `data`、
非 `OK` 业务码和网络失败。

## 3. Axios 实例、JWT 与统一错误处理

只创建一个 Axios 实例，基础路径指向 Java `/api/v1`。请求拦截器从最小认证状态读取 Token，
添加 `Authorization: Bearer <jwt>`；登录和注册即使带不上 Token 也应正常工作。响应错误转换为
页面可展示的稳定错误，不向用户显示堆栈或内部网络细节。

401 是认证状态失效：清除 Token 和用户视图，跳转登录页。403 是已认证但无权访问资源，不能
错误地当成“重新登录”。开发服务器通过 Vite proxy 转发到 Java，浏览器不直连 Python。

面试要点：拦截器集中处理横切逻辑，但不能吞掉错误；Token 不写日志、不进入 URL，也不成为
业务数据事实来源。

## 4. Vue Router 与最小登录状态

路由至少包含登录、注册和聊天页。导航守卫根据是否存在 Token 决定能否进入聊天页；401
响应再次执行清理和跳转，因为“本地有 Token”不代表 Token 仍有效。

本阶段不引入 Pinia。少量跨页面状态使用一个简单组合式模块，并将 Token 持久化到
`sessionStorage`：关闭标签页后自动清除，仍须承认浏览器脚本环境中的 XSS 风险。服务端始终
负责真正鉴权和资源归属，路由守卫只改善交互体验。

面试要点：前端守卫不是安全边界；真正的拒绝必须发生在 Java SecurityFilterChain 与 Service。

## 5. 同步聊天、幂等键与 UI 状态机

每次用户明确发送一条新消息时生成一个 UUID `Idempotency-Key`，请求进行中禁用输入和发送
按钮。网络错误、503 或 `UNKNOWN` 都不能触发自动重发；同一次受控重放必须复用原 Key，而
用户主动发起的新请求才生成新 Key。

页面只维护最小 UI 状态：`idle → loading → success/error`。成功后刷新或合并服务端消息；错误
保留输入，展示 Java 的稳定 `message` 和可复制的 `traceId`。`UNKNOWN` 必须明确显示“结果
暂时无法确认”，不能改写为 `FAILED`。

面试要点：禁用按钮只阻止机械双击，数据库幂等仍由 Java/MySQL 保证；前端不能复制后端
聊天状态机，也不能用自动重试制造第二次 AI 调用。

## 快速自测

1. 为什么 `ApiResponse<LoginData>` 和 `ApiResponse<ChatData>` 不能共用一个模糊的 `data: any`？
2. 为什么导航守卫不能替代后端 JWT 校验？
3. 401 与 403 在客户端处理上有什么不同？
4. 为什么发送按钮禁用后仍然必须携带 `Idempotency-Key`？
5. Java 返回 `UNKNOWN` 时，为什么客户端不能自动重发？
