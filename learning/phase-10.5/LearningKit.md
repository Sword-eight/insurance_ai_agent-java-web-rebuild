# Phase 10.5 Learning Kit

## 一句话讲清设计

Vue 用 FormData 把 PDF 交给 Java，并只展示 Java 返回的文档状态；上传中禁止重复提交，超时后
只做安全 GET 刷新，因此客户端既能交互，也不会复制或破坏后端索引状态机。

## 最值得学习的核心文件

### `DocumentView.vue`

- 位置：用户文档交互入口。
- 输入/输出：File、点击事件、Java DTO → loading、错误、列表和状态标签。
- 依赖：document API、共享错误格式化、auth session、Router。
- 面试点：进度和索引状态的区别、重复提交保护、UNKNOWN 语义。
- 小修改：增加一个只读状态筛选，不引入新的全局状态库。

### `api/document.ts`

- 位置：Vue 到 Java 文档公共 API 的适配层。
- 输入/输出：File/进度回调 → `DocumentData`；无输入 → 文档分页。
- 依赖：共享 Axios、Envelope unwrap、FormData。
- 面试点：multipart boundary、请求级超时、为什么不自动重试 POST。
- 小修改：把列表 page/size 参数显式化，同时保持返回类型稳定。

### `types/api.ts`

- 位置：Java 公共 DTO 的 TypeScript 镜像。
- 输入/输出：编译期类型，不产生运行时业务状态。
- 依赖：冻结 API 字段。
- 面试点：Envelope 泛型与业务 DTO 分离、联合类型怎样限制状态。
- 小修改：为状态展示写一个穷尽映射并让新增状态触发编译提示。

### `api/http.ts`

- 位置：共享 Axios、JWT、401 和稳定错误边界；本阶段复用未修改。
- 输入/输出：Axios 响应/错误 → data 或 `ApiClientError`。
- 依赖：auth session。
- 面试点：为什么业务 API 不各自解析 Token，为什么 504 不自动重试。
- 小修改：给错误展示增加一个纯函数测试，不改变后端业务码。

### `DocumentView.test.ts`

- 位置：页面交互与异常语义证据。
- 输入/输出：受控 Java 契约结果 → DOM 断言与调用次数。
- 依赖：Vue Test Utils、Vitest、document API mock。
- 面试点：如何证明双击只产生一次 POST，以及 UNKNOWN 没有自动重传。
- 小修改：补一个错误 MIME 文件的零 API 调用测试。

## 建议讲解顺序

1. 从 `DocumentData` 解释公共契约。
2. 沿 `DocumentView -> documentApi -> Axios -> Java` 讲 multipart。
3. 区分字节进度、后端状态和索引发布。
4. 用 504 测试讲 UNKNOWN 与只读刷新。
5. 最后展示 typecheck、20 tests、build、audit 和浏览器 WARNING。

## 验收结论

四维审计为 PASS with WARNING，无未解决 ERROR。真实结果与限制见 [Audit](./Audit.md)。本阶段
尚未提交或推送，等待用户 Review。
