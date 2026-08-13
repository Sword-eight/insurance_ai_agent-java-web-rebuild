# Phase 6 Learning Kit：Java→Python 同步聊天链

## 阶段结论

Phase 6 已打通无数据库的同步聊天链：

```text
POST /api/v1/chat/messages
→ ChatController
→ ChatService
→ HttpAgentClient
→ POST /internal/v1/agent/chat
→ Python AgentFacade / Graph
```

实现包含公共/内部 DTO 转换、TraceId 透传、连接与读取超时分类、内部错误到公共错误的安全翻译、严格来源映射，以及单进程短生命周期的幂等行为。JDK HTTP Client 固定为 HTTP/1.1，以兼容当前 Uvicorn 的明文 HTTP 链路。

四维审计结论：**PASS with WARNING**，无 ERROR。WARNING 是 Phase 6 不持久化幂等状态，且双进程冒烟使用离线 SmokeGraph，不代表真实 DeepSeek、Embedding 或 FAISS 已联调。

## 验证摘要

```text
Maven verify：33 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS
Python pytest：63 passed
双进程 smoke：200 首次成功 / 200 同键复用 / 409 同键冲突
Python 内部访问日志：200 / 200 / 409
```

## 导航

| 文件 | 用途 |
|---|---|
| `MiniCourse.md` | 本阶段 5 个必需概念 |
| `KnowledgeSummary.md` | 一页知识摘要 |
| `Interview.md` | 高频面试问答 |
| `Design.md` | 关键设计、兼容修复与取舍 |
| `Challenge.md` | 编码与故障推演练习 |
| `CallGraph.md` | 当前真实调用图 |
| `TestMatrix.md` | 正常、异常与双进程测试矩阵 |
| `ReviewChecklist.md` | 构建证据与四维审计 |

## 阶段边界

本阶段不访问 MySQL、Redis，不实现会话归属、JWT、Vue、MQ、异步 `task_id`、知识库写入或真实模型联调。Phase 6 到此停止，不自动进入 Phase 7。
