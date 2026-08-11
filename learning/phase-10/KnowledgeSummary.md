# Phase 10 Knowledge Summary

- 事实所有权：Java/MySQL/原 PDF 属于业务事实；Python/FAISS 属于派生计算。
- 服务通信：二进制使用流式 multipart，metadata 使用稳定 JSON，不共享本机路径。
- 文件安全：原名只展示，UUID 受控落盘；扩展名、MIME、大小、签名、SHA 分层验证。
- 一致性：远程调用在事务外；`UPLOADED/INDEXING/INDEXED/FAILED/UNKNOWN` 表达可观测结果。
- 超时语义：超时不代表下游失败；未知结果不得盲目自动重试。
- 索引生命周期：候选构建、验证、原子 marker、内存切换；失败保留旧索引。
- 并发边界：`RLock` 只覆盖单进程，不夸大为分布式锁。
- 授权：JWT 建立身份，文档 Service 用 `userId + documentId` 校验归属。
- 测试策略：H2 快速回归 + MySQL 8 migration，Java/Python 单元与边界集成组合。
- 范围控制：无公开删除、无异步任务、无 Vue 文档页、无 MQ。
