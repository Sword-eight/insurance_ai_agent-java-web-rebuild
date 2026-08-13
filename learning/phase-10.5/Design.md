# Phase 10.5 Design

## 关键选择

1. 继续使用 Vue 3、TypeScript、Vue Router 和共享 Axios，不引入 Pinia、Element Plus 或新依赖。
2. 文档上传使用 `FormData` 的 `file` part，不手工设置 multipart boundary，不使用 Base64 JSON。
3. 聊天全局超时保持 65 秒；文档上传单次覆盖为 305 秒，以容纳 Java→Python knowledge 的
   300 秒读取预算，不扩大其他请求的等待时间。
4. 页面在上传进行中禁用文件选择和提交。上传进度只表示浏览器发送进度，不冒充索引进度。
5. 上传成功或失败后只执行 GET 刷新 Java 状态；不自动重新上传，不在 Vue 中恢复状态机。

## 页面状态

```text
mount -> GET documents -> list | empty | stable error
file select -> client feedback -> upload pending -> success | stable error
                                           -> GET documents (safe refresh)
```

`INDEXED`、`FAILED`、`UNKNOWN` 等标签只是 Java DTO 的展示映射。`UNKNOWN` 始终显示“结果暂时
无法确认”，不能变成 `FAILED`；当前无公开删除或重建按钮。

## 安全与边界

- 浏览器的扩展名/MIME/20 MiB 检查只改善体验；Java 仍执行权威安全校验。
- JWT 由共享 Axios 拦截器携带，401 沿用现有清理会话与跳转行为。
- Vue 代码中没有 Python 地址、内部路径、MySQL、Redis 或 FAISS 访问。
- 不修改 Phase 10 Java/Python 业务实现和冻结契约。
