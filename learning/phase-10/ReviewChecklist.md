# Phase 10 Review Checklist

## 功能

- [x] 单 PDF 上传返回冻结 Envelope 与 HTTP 201。
- [x] 本人文档列表和详情可查询，跨用户详情不泄露存在性。
- [x] 20 MiB + 1 byte 返回 413；伪 PDF 返回 415。
- [x] 成功、明确失败和超时分别落 `INDEXED`、`FAILED`、`UNKNOWN`。

## 架构

- [x] Controller 只调用 Service。
- [x] Java 通过内部 HTTP Client 调 Python，Python 不访问业务 MySQL。
- [x] DTO、Entity、VO、Mapper、Storage 职责分离。
- [x] LangGraph、RAG、Embedding、FAISS 仍留在 Python。

## 安全与一致性

- [x] 用户文件名不参与路径拼接。
- [x] Java/Python 双边验证大小、签名和 SHA-256。
- [x] Python HTTP 调用不处于数据库事务中。
- [x] 索引候选失败不替换 current marker 或已加载索引。
- [x] 未提交密钥、模型、索引、日志、本机绝对路径配置或测试数据。

## 生命周期

- [x] V2 在空 MySQL 8 数据库可执行。
- [x] V1 到 V2 升级保留既有用户行。
- [x] Python 全量测试通过。
- [x] Java 全量回归通过；最终新增边界用例另行定向通过。
- [x] 构建可产出 Spring Boot 可执行 JAR。

## 明确限制

- [ ] 真实 BGE 模型加载与真实 PDF 在线建索引未在本阶段自动测试环境执行。
- [ ] 可选 LlamaIndex 运行时未做真实模型建库，仅完成代码路径与语法验证。
- [ ] 多实例写入协调未实现；当前保证限定为单 Python 进程。
