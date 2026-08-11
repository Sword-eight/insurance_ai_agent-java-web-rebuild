# Phase 10 Call Graph

## 上传成功

```text
Client
  -> JwtAuthenticationFilter
  -> DocumentController.upload
  -> DocumentService.upload
       -> LocalDocumentStorage.store
            -> 校验 + 临时文件 + SHA-256 + 原子移动
       -> DocumentPersistenceService.createUploaded       [短事务]
       -> DocumentPersistenceService.markIndexing         [短事务]
       -> HttpKnowledgeClient.indexDocument               [事务外]
            -> POST /internal/v1/knowledge/documents/index
            -> DefaultKnowledgeFacade.index_document
                 -> Python 二次校验 + 受控临时副本
                 -> KnowledgeService.rebuild
                      -> LangChain/LlamaIndex builder
                      -> candidate generation
                      -> atomic current marker
                      -> in-memory swap under RLock
       -> DocumentPersistenceService.markIndexed          [短事务]
  <- ApiEnvelope<DocumentView> + HTTP 201
```

## 异常分支

```text
文件格式/大小错误
  -> 413/415；不写 DB；不调用 Python

原文件已保存，但首次 DB 写入失败
  -> 仅补偿删除本次未发布文件

Python 明确失败
  -> 短事务写 FAILED；保留 Java 原文件；返回 DOCUMENT_INDEX_FAILED

Python 超时/结果不确定
  -> 短事务写 UNKNOWN；不自动重试；保留原文件

候选 FAISS 构建失败
  -> 删除候选 generation；current marker 与已加载索引不变
```

## 查询

```text
DocumentController
  -> DocumentService
       -> CurrentUserProvider
       -> KnowledgeDocumentMapper (user_id + document_id)
  <- 本人列表或详情
```

Controller 不直接访问 Mapper 或 Python Client，Python 不访问 Java 数据库。
