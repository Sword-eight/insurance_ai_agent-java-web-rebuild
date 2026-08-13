# Phase 12 Design：最终验收证据设计

## 目标

Phase 12 不新增业务能力，而是用可重复证据验证既有 Vue、Java、Python、MySQL 与 Redis 协作，
并完成冻结架构、依赖、安全和生命周期审计。

## 分层证据

- Python 契约测试使用真实 FastAPI router、facade、Envelope、TraceId 和文件校验；Graph/Knowledge
  使用确定性替身，因此不宣称真实 DeepSeek、Embedding 或索引构建成功。
- Java 平台 E2E 使用真实 Spring Boot HTTP、MySQL 8.4、Redis 7.4 和独立 Python HTTP 进程，验证
  注册、登录、JWT、会话、聊天、幂等、TTL、PDF、503 与 504。
- 浏览器验收使用真实 Vue 页面、Java、MySQL、Redis 和确定性 Python，验证用户可见主链与错误提示。
- 三端全量回归分别证明各自既有能力未被最终测试工程破坏。

## 隔离和安全

MySQL 使用临时容器和 tmpfs，Redis 使用临时容器；Java/Python 文档目录分离，凭据每次随机生成。
启动脚本只清理状态文件记录且带 Phase 12 标签的资源，不连接本机已有数据库，不读取 `.env`。
Python 与 Node 可执行文件由调用者显式传入，仓库不保存本机绝对路径。

## 依赖策略

通用依赖给 Transformers 4 和 Torch 2.4～2.7 设置经过验证的边界；Windows CPU 开发验收入口使用
`requirements-windows-cpu.txt` 固定 `torch==2.7.1+cpu`。独立 LoRA/CUDA 清单和在线链路不在本阶段改造。

## 非目标

- 不引入 MQ、Worker、异步 task、Nacos 或 Kubernetes。
- 不把测试替身接入生产启动入口。
- 不把 H2 MySQL mode 作为最终数据库结论。
- 不以本阶段结果宣称生产容量、安全渗透或真实在线模型质量已验收。
