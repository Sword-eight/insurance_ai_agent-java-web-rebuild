# Phase 4 Review Checklist

## 1. 范围与 Git

- [x] Phase 3 已独立提交：`3e4fcc1`。
- [x] Phase 4 只修改 Python FastAPI 包装、Application 适配、Graph 调用边界、测试和本阶段 Learning Kit。
- [x] 未修改 Phase 2 冻结文档。
- [x] 未修改 Java 业务源码。
- [x] 未提交、未 push、未进入 Phase 5。
- [x] 未读取或写入真实上传内容，未构建、删除或重建索引。

## 2. 功能验收

| 检查项 | 结果 | 证据 |
|---|---|---|
| lifespan 工厂只初始化一次 | PASS | TestClient 生命周期计数测试 |
| shutdown 关闭 Runtime | PASS | 同步 close callback 测试 |
| chat 有限历史与真实 AIMessage | PASS | Facade + Graph 离线替身测试 |
| requestId 并发防重 | PASS | 8 线程原子 begin 测试 |
| TTL、容量、缓存、摘要冲突 | PASS | RequestRegistry 单元测试 |
| 迟到 lease 不覆盖新执行 | PASS | stale/current lease 回归测试 |
| checkpoint 请求隔离与清理 | PASS | 相同 session、不同 request 测试 |
| live/ready 降级 | PASS | bootstrap failure HTTP 测试 |
| 稳定错误 envelope 与 trace header | PASS | HTTP 正常/异常测试 |
| knowledge status 安全过滤 | PASS | path/document key 不透出测试 |
| knowledge index/rebuild 数据安全 | PASS | 不读 content、不调用 Service 写方法 |

## 3. 实际验证

```text
python -m compileall -q api application graph tests
结果：exit 0

python -m pytest -q
结果：63 passed in 8.24s

git diff --check
结果：exit 0（仅 LF→CRLF 工作区环境提示）
```

Python 测试不访问网络、API Key、BGE 或真实 FAISS；不得把离线替身测试表述为真实 DeepSeek、Embedding 或索引集成结果。

Java 回归已实际执行：

```text
mvn -Dmaven.repo.local=<existing-user-cache> -o test
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

首次 Maven 命令把本地仓库错误解析到不可写的 `C:\.m2\repository`，因此未执行测试；显式指定现有用户缓存后成功。该环境错误未被省略，也未写入项目配置。

## 4. 四维审计

### 功能：PASS

- 同步聊天、错误映射、防重、健康检查与只读知识状态均有正常/异常测试。
- 无有效 AIMessage 时明确失败；没有伪造回答、来源或 Tool 调用。

### 架构：PASS

- Router → Facade → existing Graph/KnowledgeService，HTTP 层不直接调用 Tool、RAG 或 Builder。
- graph/tools/services/rag 的核心语义未重写或搬移。
- Python 不访问 Java MySQL/Redis；未引入 MQ、Worker 或 taskId。

### 设计：PASS

- Schema、Command/Result、Facade 和 HTTP Envelope 分离。
- ApplicationError 不依赖 FastAPI；状态码在 HTTP 边界集中映射。
- request registry 容量受限，锁内原子注册，单调时钟 TTL，并用 lease 防止迟到写。
- Knowledge 写路径以稳定失败保留契约，没有绕过 Phase 10 安全要求。

### 生命周期：PASS with WARNING

- PASS：Runtime 初始化一次、请求复用、关闭回调逆序执行。
- PASS：HTTP request checkpoint 在 finally 清理，异常时也不会残留。
- PASS：启动不自动构建缺失索引；live/ready 正确分离。
- WARNING：真实 DeepSeek/Embedding/FAISS 默认启动链未在当前离线测试中执行，不能宣称在线集成已验证。
- WARNING：Knowledge 上传、原子索引发布和 rebuild/retrieval 并发属于 Phase 10；细粒度 readiness、LLM timeout adapter 与观测属于 Phase 11。

## 5. 结论

ERROR：无。Phase 4 结论：**PASS with WARNING**。WARNING 是已冻结的后续阶段工作或当前环境下未执行的外部集成，不影响本阶段最小包装的离线可验证性。
