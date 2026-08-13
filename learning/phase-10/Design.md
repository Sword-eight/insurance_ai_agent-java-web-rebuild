# Phase 10 Design

## 设计结论

- Java 是原 PDF、文档元数据、用户归属和索引状态的事实来源。
- Python 只接收流式 multipart，负责解析、Embedding 与 FAISS，不访问 Java 业务数据库。
- FAISS 是可重建派生物；候选索引成功后才原子发布，构建失败继续提供旧索引。
- 远程 HTTP 不包在数据库事务中。每次状态写入都是独立短事务。

## 外部契约

```text
POST /api/v1/documents
GET  /api/v1/documents
GET  /api/v1/documents/{documentId}
```

上传只接受一个非空 PDF，最大 20 MiB。展示名与受控存储名分离，跨用户详情统一表现为
`DOCUMENT_NOT_FOUND`，避免泄露资源是否存在。

Python 内部契约：

```text
POST /internal/v1/knowledge/documents/index
POST /internal/v1/knowledge/index/rebuild
GET  /internal/v1/knowledge/index/status
```

Java 发送 `metadata` JSON、PDF 二进制和 TraceId，不发送本机路径。

## 数据与状态

V2 migration 创建 `iap_knowledge_document`，保存 UUID、用户归属、原始展示名、受控存储键、
SHA-256、大小、MIME、索引状态、错误信息和时间戳。

```text
UPLOADED -> INDEXING -> INDEXED
                     -> FAILED   （Python 明确拒绝或失败）
                     -> UNKNOWN  （超时或交付结果不确定）
```

`UNKNOWN` 不自动重试，因为请求可能已被 Python 成功处理；盲目重发可能制造重复副作用。

## 文件安全

- 客户端文件名仅用于展示；磁盘名固定为 `{documentId}.pdf`。
- Java 检查扩展名、声明 MIME、20 MiB、`%PDF-` 签名并计算 SHA-256。
- Python 再检查大小、签名和摘要，临时文件与失败候选均清理。
- 首次元数据写入失败时删除尚未发布的新文件；索引失败或超时时保留原文件用于显式恢复。

## 索引发布

LangChain 与 LlamaIndex 都先写 generation 目录。候选构建成功后原子替换 current marker，
再在进程内锁保护下切换内存引用。检索只看到旧版本或新版本，不看到半成品。

当前锁只保证单 Python 进程内互斥；它不是多实例分布式一致性方案。

## 明确不做

- 不新增公开删除 API；冻结契约没有该端点。
- 不实现异步 `task_id`、MQ、Worker、Nacos 或分布式事务。
- 不让 Python 访问 MySQL，也不共享 Java 文件目录。
- 不改 Vue 客户端；文档 UI 属于 Phase 10.5。
