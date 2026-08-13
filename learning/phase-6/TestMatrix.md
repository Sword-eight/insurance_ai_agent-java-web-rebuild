# Phase 6 Test Matrix

> 状态：实现完成。完整命令证据见 `ReviewChecklist.md`。

## 1. Controller 与公共契约

| 场景 | 期望 |
|---|---|
| 合法 conversation/message/Idempotency-Key | HTTP 200 + `code=OK` + ChatResponse |
| 缺少或非法 Idempotency-Key | HTTP 400 + `VALIDATION_ERROR` |
| 空消息、超长消息、非法 UUID/JSON | HTTP 400，不调用 Service/Client |
| 合法 TraceId | Header、公共 Envelope、Python 内部调用一致 |

## 2. Service 编排

| 场景 | 期望 |
|---|---|
| 成功响应 | conversationId 保持；稳定生成三个 ID；answer trim |
| sources 为空 | 公共 sources 为空列表 |
| source 字段缺失、类型错误或 snippet 超长 | `AI_EXECUTION_FAILED` |
| requestId 不匹配、answer 空、duration 为负 | `AI_EXECUTION_FAILED` |
| Client 连接失败/读取超时 | 分别映射 503/504 |
| 内部请求处理中/冲突 | 分别映射公共 409 code |

## 3. HTTP Client

| 场景 | 期望 |
|---|---|
| 请求 JSON | 只有 requestId/sessionId/message/history 四字段 |
| TraceId | 使用 `X-Trace-Id` 透传 |
| 成功 Envelope | status、traceId、data、error 形状全部合法才接受 |
| 失败 Envelope | 只保留内部 code，不泄露内部 message |
| malformed/trace mismatch | 协议错误 |
| read timeout | 一次调用、无自动重试、分类 TIMEOUT |

## 4. 双进程冒烟

| 场景 | 实际结果 |
|---|---|
| Python `/health/live` + 合法 TraceId | HTTP 200 |
| 首次公共聊天 | HTTP 200；answer=`smoke:waiting period` |
| 相同 Key、相同载荷 | HTTP 200；业务 data 完全一致 |
| 相同 Key、不同载荷 | HTTP 409；`CHAT_IDEMPOTENCY_CONFLICT` |
| Python 内部访问日志 | 200、200、409 |
| 测试结束 | Java/Python 进程停止；监听器为 0 |

冒烟使用真实 Java JAR、真实 FastAPI/AgentFacade/RequestRegistry 和真实进程间 HTTP，但 Graph 是 test-only 离线替身，不访问模型或索引。
