# Phase 2 Knowledge Summary

## 一张图

```text
Web Client
  │ /api/v1 + Idempotency-Key
  ▼
Java Controller → Service → MySQL（完整事实）
                         ↘ Redis（短期加速/保护）
                 → AgentClient / KnowledgeClient
                    │ /internal/v1 + requestId + X-Trace-Id
                    ▼
Python Router → Facade → existing Graph / KnowledgeService
                              ├→ DeepSeek / Tools / RAG
                              └→ Embedding / FAISS
```

## 两套 API

- 公共：`/api/v1`，返回 Java VO 和统一 `{code,message,data,traceId,timestamp}`。
- 内部：`/internal/v1`，返回 `{success,data/error,traceId}`，不暴露 Python 堆栈。

## 三个 ID

- Idempotency-Key：客户端同一次提交意图，UUID。
- requestId：Java 生成的一次业务执行，贯穿 MySQL 和 Python。
- traceId：日志链路，可变化，不参与幂等。

## 聊天核心规则

- history：最新 5 轮/10 条 user/assistant，最多 12000 字符。
- 事务 A：request + 用户消息 → PROCESSING；事务外调 Python；事务 B：助手消息 + SUCCEEDED，或 FAILED/UNKNOWN。
- 读取超时：504 + UNKNOWN + 不写助手消息 + 不自动重试。
- 仅能证明请求未送达时，复用 requestId 最多一次连接级重试。

## 五张逻辑表

`iap_user`、`iap_conversation`、`iap_chat_request`、`iap_chat_message`、`iap_knowledge_document`。

关键约束：`(user_id,idempotency_key)` 唯一；`(chat_request_id,role)` 唯一；完整聊天只在 MySQL。

## 三类 Redis Key

| 用例 | TTL | 故障行为 |
|---|---:|---|
| 最近 10 条消息 | 30 分钟 | 回源 MySQL |
| 每用户聊天 20 次/分钟 | 120 秒 | fail-closed，返回 503 |
| chat 幂等摘要 | 24 小时 | 回源 MySQL 唯一约束 |

## 知识库所有权

- Java：PDF 原文件、documentId、元数据、INDEXING/INDEXED/FAILED/UNKNOWN。
- Python：解析、切分、BGE、FAISS。
- 传输：单 PDF 流式 multipart，上限 20 MiB，不共享任意绝对路径。

## 五条必须记住的规则

1. Controller 不直接调用 Mapper 或 Python。
2. MySQL 是长期事实，Redis 是可丢失加速，FAISS 是 AI 派生索引。
3. TraceId 不等于 requestId，也不等于 Idempotency-Key。
4. 远程 HTTP 不包在长数据库事务中。
5. POST 读取超时不证明未执行，所以不能无条件重试。

## 三个限制

- 同步索引最长 300 秒，不适合大规模文档。
- 固定窗口限流有边界突发。
- Python 10 分钟进程内防重不支持多实例一致性。

当前仍未实现 Java Backend 或 FastAPI；Phase 2 的 PASS 是设计和文档验收，不是运行功能验收。

