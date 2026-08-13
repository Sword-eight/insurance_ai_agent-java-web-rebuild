# Vue Web Client 范围、实现与学习规划

> 状态：Phase 9.5 / 10.5 已实现；Phase 12 Chrome E2E 已通过
> 适用阶段：Phase 9.5 / Phase 10.5
> 架构依据：[ARCHITECTURE.md](./ARCHITECTURE.md)
> 公共契约依据：[API.md](./API.md)
> 路线依据：[MIGRATION_PLAN.md](./MIGRATION_PLAN.md)
> 当前状态：[PROJECT_STATUS.md](./PROJECT_STATUS.md)

本文件最初冻结“极简但可交互”的正式平台客户端范围。`web-client/` 已按该范围实现：Phase 9.5
完成登录、注册、会话和聊天，Phase 10.5 增加 PDF 文档页面；Phase 12 完成真实 Chrome 联调。

## 1. 目标与定位

正式平台链路为：

```text
Vue Web Client → Java Spring Boot Backend → Python FastAPI AI Service
```

客户端只需能交互、完成项目演示、展示 Java 后端能力，并让非前端主方向的开发者能看懂和
完成小修改。视觉标准是“能用、清晰、不混乱、能完成面试演示”，不追求专业 UI/UX。

Streamlit 不删除。它继续承担 Python Agent 本地调试、旧版功能验证和 Graph/Tool/RAG 调试，
链路是 `Streamlit → Python`。它不参与 Java JWT、ChatService、MySQL 会话/消息、Redis、
AgentClient、Java 聊天状态机或文档业务状态管理。

## 2. 技术栈与最小目录

冻结的必需技术栈：Vue 3、TypeScript、Vite、Vue Router、Axios。

- Element Plus 默认不引入；只有明显减少表单、上传或对话框开发量时才评审引入。
- Pinia 默认不引入；少量状态优先使用组件状态或简单组合式函数。只有跨页面共享状态已经
  明显复杂时才评审引入。

当前实现采用以下最小目录（省略测试文件）：

```text
web-client/
├── src/
│   ├── api/
│   │   ├── http.ts
│   │   ├── auth.ts
│   │   ├── chat.ts
│   │   ├── conversation.ts
│   │   └── document.ts
│   ├── views/
│   │   ├── LoginView.vue
│   │   ├── RegisterView.vue
│   │   ├── ChatView.vue
│   │   └── DocumentView.vue
│   ├── components/
│   │   ├── ConversationList.vue
│   │   ├── MessageList.vue
│   │   └── ChatInput.vue
│   ├── router/
│   ├── types/
│   ├── utils/
│   ├── App.vue
│   └── main.ts
├── package.json
├── vite.config.ts
└── tsconfig.json
```

不引入 Nuxt、SSR、微前端、GraphQL、WebSocket、复杂状态框架、国际化框架、CSS 动画框架、
前端微服务或大型设计系统。

## 3. 职责边界

Vue 负责页面展示、表单输入、基础校验、调用 Java API、携带 JWT、展示会话/消息/loading/
业务状态/错误，并在请求进行中禁用按钮以阻止机械重复提交。

Vue 不负责：

- 直接调用 Python、MySQL、Redis 或 FAISS；
- 保存长期聊天事实；
- 判断 Python 是否最终成功，或决定 `SUCCEEDED / FAILED / UNKNOWN`；
- 自动重试 AI 请求、恢复 `UNKNOWN`、绕过 Java 鉴权；
- 构造一套与 Java 不一致的业务状态机。

## 4. Phase 9.5：Vue Minimal Chat Client

### 前置条件（已满足）

Phase 6～9 已完成并验证：Java→Python 同步聊天、MySQL 会话与消息、Redis、注册登录、JWT、
资源归属，以及登录/会话/历史/聊天公共 API 已稳定。Phase 6 不提前制作临时无鉴权前端；
该阶段用 Swagger、测试或 Postman 验证链路即可。

### 实现范围（已完成）

1. 登录页和注册页。
2. 聊天主页面、会话列表、新建与切换会话。
3. 历史消息展示和发送消息。
4. 基础 loading、错误文本、请求中禁用重复提交。
5. 401 清理登录态并跳转登录页。

页面只需基本左右布局、会话区域、消息区域、输入框、发送按钮、表单、间距、边框和滚动条。
本阶段不实现文档页面。

### 调用链

```text
LoginView → authApi → Java AuthController → AuthService → MySQL → JWT → Vue 登录状态
ChatView → chatApi → Axios → Java ChatController → ChatService → AgentClient
         → Python FastAPI → Java 写入状态/MySQL → Vue 展示公共响应
ChatView → conversationApi → Java ConversationController → Service → MySQL → Vue 展示历史
```

### 验收（已通过）

- 可以注册、登录并携带 JWT；401 会清理登录态并跳转。
- 可以创建/切换会话、查看历史、发送消息并显示 loading 与稳定错误。
- 请求进行中不会因连续点击机械发送多个 AI 请求。
- 所有 API 只访问 Java；能画出 Vue→Java→Python 聊天链。
- 学习者能完成一个小页面或 DTO/API 调用修改。

## 5. Phase 10.5：Document Client Extension

### 前置条件与范围（已完成）

Phase 10 的 Java PDF 上传、文档列表、文档状态、索引状态和 Python Knowledge 调用已经完成并
稳定。本阶段只在现有客户端上新增：文件选择、PDF 上传、上传进度或 loading、文档列表、
索引状态和上传失败提示，不重做客户端结构或视觉。

```text
DocumentView → documentApi → Java DocumentController → DocumentService
             → Python Knowledge API → Java 保存文档/索引状态 → Vue 展示公共响应
```

验收结果：可上传 PDF、查看文档和索引状态、看到明确失败提示；Vue 不直连 Python Knowledge
API。Phase 12 Chrome E2E 已验证未登录路由保护、正常聊天、503、timeout/UNKNOWN 与 PDF 上传。

## 6. Envelope、DTO 与错误展示

```text
Python InternalEnvelope = Python 与 Java 的内部通信契约
Java Public API Envelope = Java 与 Vue 的公共通信契约
```

Vue 只依赖 Java 公共 DTO。TypeScript 可使用 `ApiResponse<T>` 泛型外层，例如
`ApiResponse<LoginData>`、`ApiResponse<ChatData>`、`ApiResponse<ConversationData>` 和
`ApiResponse<DocumentData>`；统一外层不等于所有 `data` 使用同一类型。

客户端至少按 Java 公共响应处理：400 参数错误、401 登录失效、403 禁止访问、404 资源不存在、
409 冲突/处理中、429 过于频繁、500 Java 内部错误、502 Java 调 Python 失败、503 AI 服务
不可用，以及业务状态 `UNKNOWN`。

对于 `UNKNOWN`，页面明确提示“结果暂时无法确认”，不自动重发、不显示为 `FAILED`。是否提供
重新生成由 Java 业务设计决定；用户明确重新生成时必须作为带新幂等键的新业务请求。

## 7. Phase 9.5 两遍学习法

第一遍在开发前用 2～3 小时快速预习，可拆两次：浏览器与前后端分离、Vue 单文件组件、
`ref`、`props`/`emits`、Vue Router、Axios、JWT 请求头和 TypeScript 接口。只理解用途，不深入
框架源码。

第二遍在代码完成后沿真实链路逐节点学习：

```text
ChatView.vue → chat.ts → http.ts → Java ChatController
```

每次只讲一个文件或节点。停止条件是能看懂基础组件、修改输入框/按钮、增加一个 API 调用、
修改一个 TypeScript DTO、解释 JWT 携带和 Vue 不直连 Python 的原因，并能分析一次 401、503
或 `UNKNOWN`。

## 8. 时间与非目标

| 阶段 | 开发与联调 | 学习和小修改 |
|---|---:|---:|
| Phase 9.5 | 1～2 天 | 1～2 天 |
| Phase 10.5 | 0.5～1 天 | 0.5～1 天 |

总增量：快速 2～3 天，正常节奏 3～5 天，需要补 JavaScript 基础时 5～7 天。该范围不是完整
前端项目，不按 1～3 周估算。

非目标包括：从零独立手写大型 Vue 工程、专业前端面试能力、虚拟 DOM/响应式源码、完整前端
自动化测试体系、精细响应式布局、流式聊天、高级 Markdown、拖拽上传、多语言和移动端适配。
边缘知识记录到复习清单，不阻塞迁移主线。
