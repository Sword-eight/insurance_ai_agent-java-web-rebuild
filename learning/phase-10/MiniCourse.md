# Phase 10 MiniCourse：PDF 上传与知识库一致性

> 状态：开发前必修
> 目标：能解释一个 PDF 如何在 Java、MySQL、本地文件系统和 Python/FAISS 之间安全流转，并能判断失败状态是否可信。

## 1. multipart 是服务边界，不是共享路径

浏览器把单个 PDF 交给 Java；Java 校验并保存受控原文件后，再以流式 `multipart/form-data` 把二进制内容和 JSON metadata 发送给 Python。metadata 只包含 `requestId`、`documentId`、展示文件名和 SHA-256，不包含 Java 本机绝对路径。

```text
Web file part
  -> Java controlled storage
  -> KnowledgeClient multipart(metadata + file)
  -> Python temporary file
  -> KnowledgeService
```

面试要点：

- 共享路径会把部署拓扑、权限和操作系统路径耦合在两个服务之间。
- JSON Base64 会增加体积和内存复制；流式 multipart 更适合 20 MiB 上限的二进制文件。
- Java 已经校验过文件不代表 Python 可以信任它；服务边界两侧都要校验。

## 2. PDF 校验需要纵深防御

用户文件名只用于展示，不能参与存储路径拼接。Java 生成 UUID 存储键，并同时检查：单文件、非空、`.pdf` 扩展名、`application/pdf` 声明 MIME、20 MiB 上限和 `%PDF-` 文件签名；保存时计算 SHA-256。Python 再检查大小、签名和摘要，防止传输损坏或绕过上游入口。

面试要点：

- 扩展名和 MIME 都是声明，单独使用不能证明内容是 PDF。
- 文件签名是低成本格式门槛，但不等价于恶意内容扫描或完整 PDF 语义验证。
- 受控目录、随机存储键、路径归一化检查和临时文件清理共同防止路径穿越与残留泄漏。

## 3. 不用分布式事务也能表达可靠状态

MySQL、文件系统和 Python/FAISS 不能放进一个 ACID 事务。Phase 10 使用短事务与显式状态：先保存文件，再以短事务写入 `UPLOADED`，另一个短事务转为 `INDEXING`；事务外调用 Python，最后用短事务写 `INDEXED`、`FAILED` 或 `UNKNOWN`。

```text
save file -> DB UPLOADED -> DB INDEXING -> HTTP outside transaction
                                         |- success -> INDEXED
                                         |- explicit failure -> FAILED
                                         `- read timeout -> UNKNOWN
```

如果首次元数据事务失败，只删除本次新建且尚未对外发布的文件；Python 明确失败或超时时保留原文件，便于以后显式重建。`UNKNOWN` 代表“Java 没收到确定结果”，不能自动重发索引请求。

## 4. FAISS 是可重建派生物，但替换必须原子

MySQL 文档状态和 Java 原文件是业务事实，FAISS 是 Python 管理的派生索引。重建不能先删除线上索引再慢慢生成：应在临时位置构建完整候选索引，验证成功后在受控临界区切换；失败时保留旧索引。

面试要点：

- “可重建”不等于可以暴露半写索引或让检索随机失败。
- 进程内锁解决单实例内的重建/检索竞争，不伪装成多实例分布式锁。
- 磁盘原子替换和内存引用切换要一起考虑；读取者应看到旧版本或新版本，而不是中间状态。

## 5. 测试要跨越四种边界

本阶段不能只测 Controller。测试至少覆盖：

- HTTP：JWT、multipart、统一 Envelope、413/415、本人列表与跨用户详情；
- Java 编排：远程调用发生在事务外，成功/明确失败/读取超时分别落正确状态；
- 文件系统：受控命名、SHA-256、失败补偿、临时文件清理；
- Python：二次校验、Facade 调用 `KnowledgeService`、并发重建互斥、失败不破坏旧索引；
- 数据库：H2 快速回归之外，用真实 MySQL 8 验证空库初始化和从 Phase 7 schema 升级到文档表。

## 快速自测

1. 为什么 Java 不能把本机 PDF 绝对路径传给 Python？
2. 扩展名、MIME、文件签名和 SHA-256 分别防什么问题？
3. 为什么 Python 读取超时必须映射为 `UNKNOWN`，且不能自动重试？
4. 如何确保索引重建失败时已有检索仍可工作？
5. 为什么真实 MySQL 的空库 migration 与版本升级路径都要验证？
