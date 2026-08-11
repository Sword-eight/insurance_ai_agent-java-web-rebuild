# Phase 10.5 Knowledge Summary

- 文件上传：`File -> FormData(file) -> Axios -> Java`，boundary 交给浏览器。
- 客户端校验：用于体验；Java 的大小、MIME、签名和受控存储才是安全边界。
- 进度语义：字节发送进度不等于索引进度，更不等于 FAISS 已发布。
- 超时预算：聊天保持 65 秒；文档上传单次 305 秒，不修改全局行为。
- 类型契约：上传返回 `DocumentData`，列表返回 `PageResponse<DocumentData>`。
- 状态所有权：Java/MySQL 决定业务状态，Python 管索引，Vue 只展示。
- UNKNOWN：结果不确定，不改写、不自动重传；只允许安全 GET 刷新。
- 认证：共享 Axios 携带 JWT，401 统一清理 session 并跳转登录。
- 架构：Vue 只访问 Java `/api/v1`，不出现 Python internal URL。
- YAGNI：不新增依赖、Pinia、UI 框架、拖拽、批量上传、删除或重建。
