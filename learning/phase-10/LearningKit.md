# Phase 10 Learning Kit

## 一句话讲清设计

Java 安全保存 PDF 原件和业务状态，事务外用 multipart 调 Python；Python 构建候选 FAISS 并原子发布，
因此跨数据库、文件系统和索引的失败能够被明确表达，而不会用长事务或共享路径制造伪一致性。

## 最重要的代码地图

### `DocumentService`

- 位置：Java 文档业务编排层。
- 输入/输出：当前用户、multipart 文件、TraceId → `DocumentView`。
- 依赖：`DocumentStorage`、`DocumentPersistenceService`、`KnowledgeClient`。
- 面试点：为什么 HTTP 调用在事务外，怎样区分 `FAILED` 与 `UNKNOWN`。
- 小改造：增加显式人工重建时，应复用状态机而不是在 Controller 直接调 Client。

### `LocalDocumentStorage`

- 位置：Java 受控原文件存储适配器。
- 输入/输出：不可信上传 → UUID 文件、SHA-256、大小等受信元数据。
- 依赖：本地文件系统与 `DocumentStorageProperties`。
- 面试点：路径穿越、临时文件、原子移动与补偿边界。
- 小改造：替换对象存储时保持 `DocumentStorage` 契约，不把对象存储细节泄漏给 Service。

### `HttpKnowledgeClient`

- 位置：Java 到 Python 的内部 HTTP 适配器。
- 输入/输出：metadata + Resource + TraceId → 内部索引响应。
- 依赖：专用 `RestClient` 与独立 300 秒超时。
- 面试点：流式 multipart、错误分类、为什么不用共享路径或 Base64 JSON。
- 小改造：增加重建接口时必须先冻结幂等与超时语义。

### `DefaultKnowledgeFacade`

- 位置：Python API 与知识服务之间的应用边界。
- 输入/输出：内部 multipart → 校验后的受控文件和索引结果。
- 依赖：`KnowledgeService`、临时目录、进程内 `RLock`。
- 面试点：下游为什么重复校验、临时副本如何清理、锁的真实保证范围。
- 小改造：增加恶意 PDF 扫描时放在解析前，并提供可观测的明确失败码。

### `VectorStoreManager`

- 位置：LangChain FAISS 生命周期管理。
- 输入/输出：候选 generation → 原子 current marker 和内存索引。
- 依赖：FAISS、Embedding、文件系统。
- 面试点：失败保旧、磁盘发布与内存切换、读写竞争。
- 小改造：多实例部署前先选择单写者或外部版本协调，不能继续依赖进程锁。

### `V2__create_knowledge_document_table.sql`

- 位置：MySQL 文档事实表迁移。
- 输入/输出：V1 schema → V2 文档表、约束和索引。
- 依赖：Flyway、`iap_user`。
- 面试点：为什么 migration 既测空库，也测保留数据的升级路径。
- 小改造：新增状态或字段要保持向前迁移，不能改写已发布 V2。

## 推荐讲解顺序

1. 先说明 Java/Python 的事实所有权和 HTTP 边界。
2. 再讲上传文件的纵深校验与受控存储。
3. 用状态机解释跨资源一致性和 `UNKNOWN`。
4. 用候选 generation 解释 FAISS 的零破坏发布。
5. 最后给出真实 MySQL、Java、Python 测试证据与已披露限制。

## 验收结论

四维审计为 PASS with WARNING，无未解决 ERROR。完整证据见 [Audit](./Audit.md)，边界覆盖见
[TestMatrix](./TestMatrix.md)。本阶段尚未提交或推送，等待用户决定。
