# Phase 5 Test Matrix

> 状态：实现完成。矩阵中的行为已由 Phase 5 单元测试或 MockMvc 集成测试覆盖；完整证据见 `ReviewChecklist.md`。

## 1. 统一响应

| 场景 | 期望 |
|---|---|
| 成功且 data 非空 | HTTP 2xx；`code=OK`；message、data、traceId、UTC timestamp 齐全 |
| 成功且 data 为空 | Envelope 结构不变；不伪造业务字段 |
| 错误响应 | `data=null`；HTTP status 与冻结 ErrorCode 一致 |
| JSON 序列化 | timestamp 为 ISO-8601；字段名与 Phase 2 完全一致 |

## 2. 校验与异常

| 场景 | 期望 |
|---|---|
| `@Valid` DTO 合法 | 进入测试 Controller，返回统一成功响应 |
| 空字段、超长字段、非法 JSON | HTTP 400 + `VALIDATION_ERROR`，不进入方法体 |
| `BusinessException` | 使用异常携带的冻结 ErrorCode 与 HTTP status |
| 未预期异常包含路径/密钥文本 | HTTP 500 + `INTERNAL_ERROR`；响应不泄露原异常文本或堆栈 |
| 不支持的 Content-Type | HTTP 415 + `UNSUPPORTED_MEDIA_TYPE` |

## 3. TraceId 生命周期

| 场景 | 期望 |
|---|---|
| 合法 `X-Trace-Id` | Header、Envelope、MDC 使用同一原值 |
| Header 缺失 | 生成符合 16～64 位规则的新值 |
| Header 非法 | 丢弃并生成新值 |
| 请求发生异常 | 仍回传 TraceId |
| 请求完成 | MDC 中不残留上一请求 TraceId |

## 4. OpenAPI

| 场景 | 期望 |
|---|---|
| 获取 public-v1 JSON | OpenAPI 文档可解析，标题和版本稳定 |
| 测试公共路径 `/api/v1/**` | 出现在 public-v1 paths |
| 测试内部路径 `/internal/**` | 不出现在 public-v1 paths |
| 无 Swagger UI starter | 不宣称或暴露 UI 能力 |

## 5. 生命周期与架构回归

- Spring Context 在没有 MySQL、Redis、JWT 和 Python 服务时启动。
- Filter、Advice、OpenAPI Config 是无请求可变字段的 Spring 单例。
- 不创建 Chat Controller/Service，不调用 AgentClient。
- Maven 全量测试真实执行，记录通过、失败、错误和跳过数量。

最终结果：Java 20/20 通过；Python 63 项分两批全部通过；无跳过。
