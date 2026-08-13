# Phase 10.5 Interview Questions

## 1. 为什么上传文件用 FormData？

它是浏览器原生的 multipart 表达，能直接传二进制并由浏览器生成 boundary，避免 Base64 的体积和复制开销。

## 2. 为什么不要手工设置 multipart `Content-Type`？

Header 必须包含与请求体一致的随机 boundary。手工写死通常会造成服务端无法解析，由 Axios/浏览器生成更可靠。

## 3. 上传进度 100% 为什么不代表索引完成？

它只代表请求字节已发送给 Java，后面还有落盘、MySQL、Python 解析、Embedding 和 FAISS 发布。

## 4. 为什么文档请求单独设置 305 秒超时？

冻结的 knowledge 读取预算是 300 秒。请求级覆盖容纳它，同时避免把聊天和普通 GET 的等待预算一并放大。

## 5. 前端为什么仍要校验 PDF？

用于快速反馈和减少无效流量，不承担安全职责。客户端可被绕过，所以 Java 必须再次执行完整验证。

## 6. `FAILED` 和 `UNKNOWN` 如何展示？

`FAILED` 是确定失败；`UNKNOWN` 是结果不确定。后者不能显示成失败，也不能触发自动上传。

## 7. 上传失败后为什么允许 GET 刷新？

GET 是只读状态查询，不制造第二次索引副作用；它可以读取 Java 已持久化的 `FAILED` 或 `UNKNOWN`。

## 8. JWT 在文档页面如何携带？

复用共享 Axios 请求拦截器。页面/API 模块不重复读取 Token，也不自己处理 401 生命周期。

## 9. 为什么没有引入 Pinia 或上传组件库？

状态只属于一个页面，原生 File/FormData 足够。新增框架会增加学习和维护成本，没有真实收益。

## 10. 如何证明 Vue 没有绕过 Java？

API 使用共享 `/api/v1` 基址，源码审计没有 Python/internal 地址，真实调用图也只指向 Java 公共端点。
