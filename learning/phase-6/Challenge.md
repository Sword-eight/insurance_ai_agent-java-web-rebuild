# Phase 6 Challenges

## Challenge 1：三类 ID

给出一次首次请求、客户端重试和服务端超时场景，分别写出 TraceId、Idempotency-Key、requestId 是否应该变化，并解释原因。

## Challenge 2：严格内部 JSON

为 `AgentChatRequest` 增加一个 `@AssertTrue isHistoryValid()` 方法，先观察 Jackson 输出，再用 `@JsonIgnore` 修复。测试必须断言字段集合精确等于四个冻结字段。

## Challenge 3：超时分类

分别模拟连接拒绝和服务端接受请求后延迟响应。要求前者映射 503、后者映射 504，并证明读取超时只调用一次。

## Challenge 4：协议错误

让 Python 替身依次返回：traceId 不一致、success=true 但 data=null、answer 为空、未知内部错误码。写出 Client/Service 应在哪一层拒绝以及最终公共错误。

## Challenge 5：HTTP 版本排障

对比 JDK HttpClient 默认版本与固定 HTTP/1.1 时发给 Uvicorn 的请求头和访问日志。解释 h2c upgrade、chunked body 和应用层 body missing 之间的排查路径。

## Challenge 6：幂等边界

同一 Key 同载荷请求两次，再用同一 Key 改消息。验证业务 data 稳定和 409 冲突。随后重启 Python，说明为什么 Phase 6 不能保证跨重启幂等。

## Challenge 7：进入 Phase 7 前的设计题

在不修改公共聊天 API 的前提下，设计 MySQL 会话、消息和幂等记录如何加入 `ChatService → Mapper`，并保持 Controller 和 AgentClient 边界不变。只做设计，不在 Phase 6 实现。
