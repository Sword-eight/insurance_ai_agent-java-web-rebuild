# Phase 5 MiniCourse：Spring Boot 公共 API 基础设施

> 目标：在不实现聊天业务的前提下，建立后续 Controller 可以直接复用的统一响应、校验、异常、TraceId 和 OpenAPI 基础。
> 本阶段只讲 5 个面试必需概念；Java→Python 聊天链属于 Phase 6，MySQL/Redis/JWT 分别属于 Phase 7～9。

## 概念 1：统一 Envelope 不等于“所有请求都返回 HTTP 200”

公共响应固定为：

```text
ApiResponse<T>
├─ code       稳定业务码，成功固定为 OK
├─ message    面向调用方的安全摘要
├─ data       成功为 VO，失败为 null
├─ traceId    与 X-Trace-Id 响应头一致
└─ timestamp  UTC ISO-8601 时间
```

Envelope 解决客户端解析一致性，HTTP status 仍表达协议语义：参数错误是 400，冲突是 409，
依赖不可用是 503，未预期异常是 500。把所有错误包装成 200 会破坏浏览器、网关、监控和重试判断。

面试要点：`code` 和 HTTP status 是两个维度；前者稳定表达业务语义，后者表达传输结果。

## 概念 2：全局异常处理器是边界翻译器

Controller 不应重复写 try/catch。`@RestControllerAdvice` 把三类失败统一翻译：

```text
Bean Validation / JSON 绑定错误 → VALIDATION_ERROR
BusinessException             → 其冻结 ErrorCode + HTTP status
未知异常                       → INTERNAL_ERROR
```

未知异常在服务端只记录 traceId 和受控诊断类型，不直接记录可能包含用户内容、路径或密钥的异常
message；响应只能返回安全固定文案。异常处理器负责 HTTP 映射，Service 仍负责业务决策，二者不能混在一起。

## 概念 3：Bean Validation 负责输入边界，不负责业务规则

Jakarta Validation 适合验证必填、长度、格式和嵌套 DTO：`@NotNull`、`@NotBlank`、`@Size`、
`@Pattern`、`@Valid`。Controller 使用 `@Valid` 后，非法请求在进入 Service 前失败。

“会话是否属于当前用户”“幂等键是否冲突”需要数据库或业务状态，必须留在 Service；不能写成
一个会查库的字段注解。这样校验层保持确定、快速、易测试，也不会绕过 `Controller → Service`。

## 概念 4：TraceId 是请求生命周期上下文，不是业务 ID

入口 Filter 处理 `X-Trace-Id`：

```text
合法 16～64 位 [A-Za-z0-9_-] → 原样采用
缺失或非法                    → Java 生成新值
→ 写入 request attribute + SLF4J MDC
→ 响应头和 Envelope 回传相同值
→ finally 清理 MDC
```

MDC 依赖当前线程，因此单例 Filter 不能把 traceId 放在实例字段中。`finally` 清理可以避免线程池
复用时把上一个请求的 traceId 串到下一个请求。TraceId 只关联日志，不承担用户身份、幂等或主键职责。

## 概念 5：OpenAPI 是公开契约视图，不是扫描全部内部实现

Springdoc 根据 Controller 和 Schema 生成 OpenAPI。Phase 5 只建立 `public-v1` 分组并匹配
`/api/v1/**`，避免未来 Java 内部或管理路径混入 Web Client 契约。

当前 Spring Boot 为 3.3.x，官方兼容矩阵对应 springdoc 2.6.x。为满足 YAGNI，本阶段只引入
OpenAPI JSON 能力；Swagger UI、鉴权定义和业务接口注解按真实需求后续增加。

面试要点：OpenAPI 可以验证“实现暴露了什么”，冻结文档说明“系统承诺什么”；两者必须通过测试保持一致，但生成文档不能替代业务契约 Review。

## 5～10 分钟自测

1. 为什么 `ApiResponse` 统一后仍必须返回正确的 4xx/5xx？
2. `BusinessException` 与未知 `RuntimeException` 的响应信息为什么不能相同处理？
3. 哪些校验应放 DTO，哪些必须进入 Service？
4. 为什么 TraceId Filter 必须在 `finally` 清理 MDC？
5. 如何用自动化测试证明 OpenAPI 只包含 `/api/v1/**`？
