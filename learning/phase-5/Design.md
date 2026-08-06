# Phase 5 Design Notes

## 1. 显式 Envelope

选择 Java record 表达不可变 `ApiResponse<T>`，并提供 success/failure 工厂。失败工厂只接受冻结 `ErrorCode` 和明确标记为安全的 message。

没有选择全局自动包装器，因为它会隐式改变 OpenAPI、文件和其他框架响应；未来 Controller 必须显式声明稳定返回类型。

## 2. 错误码作为有限集合

`ErrorCode` 枚举完整表达 Phase 2 的 17 个公共错误码、HTTP status 和安全默认 message。没有添加通用任务、数据库或 Python 内部错误码。Python 内部 code 未来由 Client 转为 Java Client 异常，再由 Service 决定公共错误。

## 3. 异常翻译

Advice 按确定性从具体到一般映射：业务异常、校验/绑定、媒体类型、文件大小、未知异常。未知异常不回显 message；日志只留 traceId 和异常类型，避免 throwable 日志泄露敏感 message。

## 4. TraceId

Filter 负责一次请求的创建、挂载、回传和清理。`TraceIdContext` 只提供纯校验/规范化和当前 MDC 读取，不保存静态可变请求状态。生成值是 32 位 UUID hex，满足冻结字符集和长度。

## 5. OpenAPI

当前 Spring Boot 3.3.x 使用官方兼容的 springdoc 2.6.x。只引入 `webmvc-api`，不引入 UI。全局路径限制和 public-v1 分组形成双保险，测试直接读取两个 JSON 入口。

参考：[springdoc compatibility matrix](https://springdoc.org/faq.html)。

## 6. 明确未选择

- 不引入 Swagger UI、Knife4j 或额外文档门户；
- 不创建 Phase 6 ChatController/ChatService；
- 不接 MySQL、Redis、JWT 或 Security Filter；
- 不实现 Java→Python timeout/retry/circuit breaker；
- 不使用 TraceId 作为幂等键或用户身份；
- 不新增冻结契约之外的公共错误码。
