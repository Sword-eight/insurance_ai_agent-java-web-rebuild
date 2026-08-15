# Insurance AI Platform 当前完成状态

> 更新日期：2026-08-15
> 当前分支：`fix/rag-dual-engine-runtime`
> Phase 12 提交：`a95dc0070c7037b230cfa3971dde89882a44abf9`
> 总体结论：全部 16 个正式阶段已完成；独立 RAG 双引擎运行时与公平评测均为 PASS with WARNING；产品链无未解决 ERROR，评测中的原始失败按证据保留

本文件汇总项目的当前状态。`MIGRATION_PLAN.md` 前半部分、早期 Learning Kit 和各阶段审计中的
“当前”“尚未实现”“未提交”等文字是对应阶段结束时的历史快照，不应覆盖本页所述现状。
冻结的服务边界仍以 [ARCHITECTURE.md](./ARCHITECTURE.md) 为唯一事实来源。

## 1. 阶段完成情况

| 顺序 | 阶段 | 交付结果 |
|---:|---|---|
| 1 | Phase 0 源码审计 | 完成 |
| 2 | Phase 0.5 Python 测试基线 | 完成 |
| 3 | Phase 1 架构冻结 | 完成 |
| 4 | Phase 2 API / MySQL / Redis 契约 | 完成 |
| 5 | Phase 3 Java 与 FastAPI Skeleton | 完成 |
| 6 | Phase 4 FastAPI 最小包装 | 完成 |
| 7 | Phase 5 Spring Boot 基础工程 | 完成 |
| 8 | Phase 6 Java→Python 聊天链 | 完成 |
| 9 | Phase 7 MySQL 会话与消息 | 完成 |
| 10 | Phase 8 Redis | 完成 |
| 11 | Phase 9 用户、登录与 JWT | 完成 |
| 12 | Phase 9.5 Vue Minimal Chat Client | 完成 |
| 13 | Phase 10 PDF 与知识库管理 | 完成 |
| 14 | Phase 10.5 Document Client Extension | 完成 |
| 15 | Phase 11 可观测性与容错 | 完成 |
| 16 | Phase 12 测试、联调与最终审计 | 完成并已推送 |

当前正式路线至 Phase 12 结束，不存在自动开始的下一 Phase。新增能力必须单独规划和审批。

Phase 12 之后的独立修复已补齐 FastAPI 启动级 RAG 引擎选择。`RAG_ENGINE` 仅接受
`langchain` 或 `llamaindex`，默认 `langchain`；非法值与缺少 LlamaIndex 可选依赖都会明确
中止启动，不会静默回退。该修复不是新的产品 Phase，也没有改变冻结服务边界。

随后完成独立 LangChain/LlamaIndex Fair Benchmark：两边使用同一受控语料、同一份预生成
chunks、BGE、FAISS、top_k、产品 Prompt 与真实 DeepSeek。该任务只增加评测数据、脚本、
artifact 和报告，不修改 Router、Retriever 算法或业务 API，也不是新的产品 Phase。

## 2. 已实现平台

正式用户链路已经落地：

```text
Vue 3 Web Client
  -> Java Spring Boot Backend
     -> MySQL 8（用户、会话、请求、消息、文档元数据）
     -> Redis（近期消息、限流、幂等加速）
     -> Python FastAPI AI Service
        -> LangGraph / Tool Calling / DeepSeek
        -> startup-selected LangChain or LlamaIndex / BGE / isolated FAISS
```

已实现能力：

- 注册、登录、JWT 校验和用户资源归属；
- 会话创建/查询、历史消息和同步聊天；
- MySQL 持久化、Redis cache-aside、限流及幂等结果复用；
- Java→Python 内部 HTTP、TraceId、超时、有限连接重试、双熔断器和健康探针；
- PDF 安全接收、SHA-256 校验、文档元数据、索引状态与原子索引发布；
- FastAPI 启动时选择 LangChain 或 LlamaIndex，运行时状态返回实际对象图引擎，两套索引物理隔离；
- LlamaIndex 使用 PyMuPDF 按页读取 PDF，公开 source score 归一化为 `0..1`，不再把原始 squared-L2 distance 当成相似度；
- Vue 登录/注册、会话/聊天、文档上传/列表和稳定错误展示；
- Streamlit 继续作为 Python Graph/Tool/RAG 调试入口；
- LoRA 保持独立离线训练/评估实验，未接入在线聊天。

## 3. 最终验证证据

| 验证范围 | 当前实际结果 |
|---|---|
| Java 全量构建与测试 | 92 tests，BUILD SUCCESS |
| Python 隔离环境全量测试 | 102 passed（最新双引擎及 PDF/score 修复回归） |
| Vue 测试与生产构建 | typecheck PASS；8 files / 27 tests；build PASS |
| 平台集成 | Spring HTTP + MySQL 8.4 + Redis 7.4 + Python HTTP PASS |
| Chrome E2E | 登录、会话、正常聊天、503、timeout/UNKNOWN、PDF、路由保护 PASS |
| 依赖检查 | Python `pip check` PASS；npm audit 0 vulnerabilities |
| 生命周期 | E2E 停止后无受管进程或容器残留 |
| RAG 双引擎 | 两种引擎均通过真实 BGE/FAISS 检索及正式 FastAPI→Graph→Tool→Retriever→sources 链路 |
| 公平 Retrieval Benchmark | 50 cases、48 个有效 retrieval labels；两引擎 Recall@1/3/5 与 MRR 完全相同 |
| 真实 DeepSeek Generation | 15 cases/引擎；LangChain 14 Correct + 1 timeout，LlamaIndex 14 Correct + 1 Partial |
| 公共 API E2E Benchmark | LangChain 6/6；LlamaIndex 5/6，1 次失败发生在 Retriever 之前的 Agent/LLM 节点 |

完整证据、命令与证明边界见 [Resume Evidence Validation](./RESUME_EVIDENCE.md)、
[RAG 双引擎验证](./RAG_ENGINE_VALIDATION.md) 和 [RAG Engine Benchmark](./RAG_ENGINE_BENCHMARK.md)。

## 4. 当前 WARNING 与非目标

以下项目未阻断当前平台验收，但不能被描述为已完成：

1. 初始双引擎集成验证使用确定性 LLM 替身；后续 Fair Benchmark 已使用真实 DeepSeek，但只覆盖 1 个受控 TXT、5 chunks、15 个 generation case 和 6 个/引擎 E2E case，仍不能外推通用效果或生产 SLA。
2. LoRA Adapter 未接入在线 Agent；训练环境仍应与平台运行环境隔离维护。
3. FAISS LangChain 加载仍依赖受信任 pickle 边界；不得加载未知来源索引。
4. `StateManager.clear_session()` 的旧 Streamlit 调试语义、Agent 显式循环上限和 Tool 失败分类仍有技术债。
5. Flyway 10.10 对 MySQL 8.4 有高于声明测试版本的提示；真实 migration 与 E2E 已通过。
6. 尚未完成专用 Maven/Python CVE 扫描、生产容量、灾备、渗透测试和生产部署自动化。
7. 同步 v1 的 `UNKNOWN` 不自动重试；迟到结果对账仍是未批准的未来演进项。
8. RAG 引擎只支持 FastAPI 启动时选择，不支持 Vue/Java 动态切换、运行时热切换、每请求选择或双引擎同时服务。
9. Fair Benchmark 保留 LangChain 1 次 generation timeout、LlamaIndex 1 个 Partial 和 1 次 E2E Agent/LLM 失败；这些不是经过重跑挑优后的结果。

## 5. 文档导航

- [冻结架构](./ARCHITECTURE.md)
- [API 契约](./API.md)
- [数据库设计](./DATABASE.md)
- [Redis 设计](./REDIS.md)
- [Vue 客户端范围](./WEB_CLIENT_PLAN.md)
- [迁移路线与历史审计](./MIGRATION_PLAN.md)
- [技术债](./TECH_DEBT.md)
- [Python 基线](./PYTHON_BASELINE.md)
- [RAG 双引擎验证](./RAG_ENGINE_VALIDATION.md)
- [RAG Engine Benchmark](./RAG_ENGINE_BENCHMARK.md)
