# Phase 10.5 MiniCourse：Vue PDF 文档客户端

> 状态：开发前必修
> 目标：能解释 PDF 如何从浏览器经过 Java 到达 Python，并正确展示索引状态而不在前端复制后端状态机。

## 1. `File`、`FormData` 与 multipart 上传

浏览器文件输入提供 `File` 对象。客户端把它作为名为 `file` 的 part 放入 `FormData`，再提交到
Java 的 `POST /api/v1/documents`。不要手工拼 multipart boundary，也不要把 PDF 转成 Base64 JSON；
Axios/浏览器会生成正确的请求体和 `Content-Type`。

```text
input[type=file] -> File -> FormData(file) -> Axios -> Java DocumentController
```

客户端的 `.pdf`/MIME 检查只用于快速反馈，不能替代 Java 对大小、扩展名、MIME 和 `%PDF-`
签名的安全校验。

## 2. 上传进度、loading 与重复提交保护

`onUploadProgress` 只表示浏览器向 Java 发送了多少字节，不表示 Python 已完成解析、Embedding 或
FAISS 发布。上传期间页面必须禁用选择和提交，避免连续点击产生两个独立文档；如果进度总量
不可得，则退化为明确的 loading，而不是伪造百分比。

```text
idle -> uploading -> Java/Python processing -> success | stable error
```

Phase 10 的知识库读取超时是 300 秒，而聊天客户端全局超时是 65 秒。文档 API 应使用请求级
约 305 秒预算，不能为了上传修改所有聊天请求的时间预算。

## 3. 公共 Envelope、分页与 TypeScript DTO

Vue 只解析 Java 的 `ApiResponse<T>`，文档列表是
`ApiResponse<PageResponse<DocumentData>>`，单次上传是 `ApiResponse<DocumentData>`。稳定字段为：

```text
documentId, originalFilename, sizeBytes, indexStatus, createdAt
```

TypeScript 类型用于在编译期防止把分页 data、登录 data 和文档 data 混用；运行时仍必须通过
统一 `unwrap` 防御非 `OK` 或空 data。

## 4. 索引状态是后端事实，Vue 只展示

Java/MySQL 拥有文档业务状态，Python 拥有 FAISS 索引实现。Vue 可以把状态转换为可读标签，
但不能自行推进或恢复状态：

- `INDEXED`：索引完成；
- `FAILED`：后端确认失败；
- `UNKNOWN`：结果暂时无法确认；
- `UPLOADED` / `INDEXING`：后端记录的处理中状态；
- `DELETED`：只兼容展示；当前没有公开删除 API。

尤其不能把 `UNKNOWN` 显示为 `FAILED`，也不能因 504 自动重新上传。

## 5. 正式链路与错误边界

正式链路固定为：

```text
DocumentView.vue
  -> documentApi
  -> shared Axios/JWT
  -> Java DocumentController/DocumentService
  -> Python Knowledge API
```

客户端不得知道 Python 地址。401 继续由共享拦截器清理登录态；413、415、502、503、504 使用
Java 的稳定业务码和 TraceId 展示。失败提示不是重试策略，页面不自动重发文件。

## 快速自测

1. 为什么不能手工设置 multipart boundary？
2. 上传进度达到 100% 为什么不代表 FAISS 已构建完成？
3. 为什么文档上传需要请求级超时，而不能修改 Axios 全局聊天超时？
4. `FAILED` 与 `UNKNOWN` 在页面语义上有什么区别？
5. 为什么浏览器的 PDF 校验只能改善体验，不能承担安全边界？
