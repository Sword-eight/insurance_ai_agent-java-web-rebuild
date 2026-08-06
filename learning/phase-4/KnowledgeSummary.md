# Phase 4 Knowledge Summary

## 一句话架构

```text
FastAPI Router → Application Facade → existing Graph / KnowledgeService
```

Router 负责 HTTP 契约；Facade 负责消息与错误适配；Graph、Tool、Service、RAG 保持原有语义。Python 不访问 Java 的 MySQL 或 Redis。

## 生命周期

`create_app()` 接收可注入的 `runtime_factory`。lifespan 启动时调用一次工厂并把 Runtime 放入 `app.state`；关闭时先把 ready 置为 false，再逆序执行资源关闭回调。初始化失败不杀死 liveness：live 返回 200，ready 和业务接口返回稳定的 503。

FastAPI 启动调用 `init_services(build_missing_index=False)`。已有索引可以加载，缺失索引不会在启动阶段自动构建，从而避免隐式数据写入。

## 聊天与上下文

HTTP history 已由 Pydantic 校验为最多 5 个完整 user/assistant 对、10 条、12000 字符。Facade 转为 LangChain Message，Graph 将有限历史和当前消息各注入一次。

业务 `sessionId` 保留在 state；`sessionId:requestId` 只作为本次 checkpoint namespace。执行结束后删除临时 checkpoint，避免跨请求污染与内存无界增长。Streamlit 不提供 requestId 时仍沿用旧 session checkpoint 行为。

## requestId 状态机

```text
ABSENT → IN_PROGRESS(lease, TTL)
       → SUCCEEDED(result, TTL)
       → FAILED(safe error, TTL)
```

- 相同摘要且进行中：409；
- 相同摘要且已完成：返回缓存，不重复执行；
- 不同摘要复用 requestId：409；
- 容量满：优先淘汰最旧终态，不淘汰仍有效的执行；
- TTL 到期后可新建 lease，旧执行不能覆盖新状态。

## 安全降级

响应只使用冻结错误码和安全消息。Graph 日志记录 session 与消息长度，不记录完整问题。没有可靠来源证据时 `sources=[]`；没有真实回答时返回错误，不生成固定“成功”文案。

知识 status 会过滤文件路径和文档列表。index/rebuild 在 Phase 4 返回 `KNOWLEDGE_INDEX_FAILED`，不读取上传内容、不删除索引、不构建索引。
