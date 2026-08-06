# Phase 5 Interview Q&A

## 1. 为什么统一响应后仍使用正确 HTTP status？

业务码让客户端获得稳定语义，HTTP status 让浏览器、网关、监控和标准 Client 正确识别成功、参数错误、冲突和依赖故障。两者互补，全部返回 200 会破坏基础设施语义。

## 2. 为什么不用 `ResponseBodyAdvice` 自动包装所有响应？

自动包装容易误伤文件下载、OpenAPI、健康检查或已经包装的响应，并隐藏 Controller 契约。本项目要求公共 Controller 显式返回 `ApiResponse<VO>`，异常由 Advice 统一处理，行为更容易审查。

## 3. DTO 校验和 Service 校验怎么划分？

空值、长度、格式、嵌套结构使用 Bean Validation；资源归属、幂等冲突、业务状态等依赖持久化事实的规则进入 Service。字段注解不查数据库，也不调用 Python。

## 4. 为什么未知异常不直接返回 `exception.getMessage()`？

异常 message 可能包含 SQL、路径、SDK 错误、Token 或用户输入。未知异常固定返回 `INTERNAL_ERROR`，日志也只记录 traceId 和异常类型；调用方凭 traceId 协助排查。

## 5. TraceId 为什么放 MDC？

MDC 让同一线程上的日志自动带链路标识，不必逐层传日志参数。但线程池会复用线程，所以必须在 Filter 的 `finally` 删除；MDC 也不等于跨线程自动传播，异步场景需要单独设计。

## 6. 为什么 Filter 不能把 traceId 存在成员字段？

Filter 默认是单例，多请求并发共享实例字段会串链。请求数据必须放 request attribute、MDC 或方法局部变量。

## 7. 只建 `GroupedOpenApi` 为什么不够？

分组端点会过滤，但默认 `/v3/api-docs` 仍可能扫描全部 Controller。还需配置全局 `paths-to-match`，并测试默认与分组两个入口都排除非公共路径。

## 8. Phase 5 为什么没有真实 Controller？

本阶段只负责公共基础设施。聊天业务链冻结在 Phase 6，提前创建会把 Controller、Service 和 AgentClient 职责混入基础阶段。test-only Controller 足以验证框架行为且不会进入生产 JAR 的业务 API。
