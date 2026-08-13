# Phase 10.5 Call Graph

## 正常上传

```text
DocumentView.vue
  -> validateFile                       [浏览器快速反馈]
  -> documentApi.uploadDocument
       -> FormData(file)
       -> shared Axios interceptor      [Authorization: Bearer]
       -> POST /api/v1/documents        [Java public API, timeout 305s]
       -> Java DocumentController
       -> Java DocumentService
       -> Python Knowledge API
  <- ApiResponse<DocumentData>
  -> GET /api/v1/documents              [刷新 Java 事实]
  -> 展示 indexStatus
```

## 异常链路

```text
非 PDF / 空文件 / >20 MiB
  -> 页面快速拒绝；不发送请求

401
  -> shared Axios interceptor -> clearSession -> /login

413 / 415 / 502 / 503
  -> ApiClientError -> message + TraceId -> 不自动上传

504 / CLIENT_TIMEOUT
  -> “结果暂时无法确认” -> GET documents -> 不自动上传
```

## 页面导航

```text
ChatView --知识库--> /documents --route guard--> DocumentView
DocumentView --返回聊天--> /chat
```

所有业务请求都使用相对 `/api/v1`，浏览器不知道 Python 内部地址。
