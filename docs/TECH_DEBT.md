# 技术债清单

> 当前状态更新：2026-08-13。下面第一张表保留 Phase 0/0.5 的历史发现；当前处置以紧随其后的
> 状态表为准。完整平台状态见 [PROJECT_STATUS.md](./PROJECT_STATUS.md)。

| 技术债 | 当前风险 | 计划处理阶段 | Phase 0.5 处理方式 |
|---|---|---|---|
| FastAPI 内部 HTTP 包装缺失 | Python 不能作为独立 AI 服务被 Java 调用 | Phase 3 Skeleton、Phase 4 实现 | 不处理 |
| Spring Boot 工程缺失 | 目标 Java 后端尚不存在 | Phase 3 Skeleton、Phase 5 实现 | 不处理 |
| 仓库目录迁移/双服务目录布局未冻结 | 提前移动会破坏现有 import 与语义 | Phase 1 决策、Phase 3 落地 | 不移动、不重命名 |
| Streamlit rerun 重建 Graph/checkpointer | 生命周期不稳定，多轮上下文可能丢失 | Phase 4 | 不处理 |
| `InMemorySaver` 不适合生产持久化 | 重启丢失、不能跨实例 | Phase 1/2 冻结边界，Phase 8 实现上下文方案 | 不处理 |
| 上传文件缺少路径、大小、MIME 和内容校验 | 存在文件安全与资源滥用风险 | Phase 10 | 不处理 |
| 索引 rebuild/delete 与检索缺少并发保护 | 服务化后可能产生竞态或损坏 | Phase 10 | 不处理 |
| RAG 来源追踪条件与 Tool 实现不匹配 | `retrieved_docs` 基本不会写入 | Phase 11/12 | 不处理 |
| LlamaIndex `total_vectors` 统计固定为 0 | 监控和 UI 数据失真 | Phase 10 | 不处理 |
| LoRA Adapter 未接入在线 Agent | 在线能力仍只使用 DeepSeek | 后续独立演进；当前 12 个 Phase 不承诺接入 | 仅纠正文档事实 |
| Redis 缓存、限流和短期上下文缺失 | 目标后端能力未实现 | Phase 8 | 不处理 |
| JWT 和用户系统缺失 | 无用户身份与资源归属校验 | Phase 9 | 不处理 |
| MySQL 会话和聊天记录缺失 | 业务数据无法持久化 | Phase 7 | 不处理 |
| Agent 缺少显式最大工具循环限制 | 异常模型输出可能长时间循环 | Phase 11 | 不处理 |
| Tool 失败监控只识别“错误”前缀 | 部分失败会被记录为成功 | Phase 11 | 不处理 |
| `StateManager.clear_session()` 未真正清理 | 方法命名与行为不一致 | Phase 8 | 不处理 |
| FAISS LangChain 加载启用危险 pickle 反序列化 | 未受信索引可能导致代码执行风险 | Phase 10/11 | 测试不加载真实 pickle；基线仅做受信任 FAISS 只读检查 |
| LoRA 脚本存在本机绝对路径 | 环境不可移植且可能泄露本机结构 | 后续独立训练维护任务 | Phase 0.5 只修错误模型标记，不做脚本重构 |
| 本机 PATH 同时暴露 Python 3.12/3.13 | 安装命令可能落入错误解释器 | 后续环境维护；当前用 `.venv` 规避 | 已记录解释器确认命令和实际测试版本 |
| 可选训练环境存在包冲突 | `pip check` 报 LLaMA-Factory/TRL 与 Transformers、Numba 与 NumPy 不兼容 | 后续独立 LoRA 环境维护 | 不大规模升级；默认离线测试不依赖该环境 |

## Phase 12 结束时状态

状态含义：`RESOLVED` 表示计划范围已实现并验证；`MITIGATED` 表示平台主链已处理但旧调试链或
信任边界仍有限制；`OPEN` 表示尚未实现，不得宣称完成。

| 技术债 | 当前状态 | 证据或剩余限制 |
|---|---|---|
| FastAPI 内部 HTTP 包装缺失 | RESOLVED | Phase 3/4 已实现 router、facade、lifespan 与内部 Envelope |
| Spring Boot 工程缺失 | RESOLVED | Phase 3/5 已建立 Java 17 Spring Boot 后端 |
| 双服务目录和边界未冻结 | RESOLVED | Phase 1 架构已冻结；Phase 12 边界扫描通过 |
| Streamlit rerun 重建 Graph/checkpointer | MITIGATED | FastAPI 使用进程级 lifespan；旧 Streamlit 入口仍按 rerun 装配并只作调试用途 |
| `InMemorySaver` 不适合生产持久化 | MITIGATED | 正式历史由 Java/MySQL 传入；HTTP execution checkpoint 用后清理，Streamlit 仍是内存态 |
| 上传文件校验不足 | RESOLVED | Java/Python 已验证大小、扩展名、MIME、PDF signature、SHA-256 与受控路径 |
| 索引 rebuild/delete 与检索并发 | RESOLVED | Phase 10 使用写锁、候选 generation 和原子发布，并有异常回归测试 |
| RAG 来源追踪条件失效 | OPEN | 当前只记录 RAG Tool 调用摘要，尚未形成真实文档级 sources 证据 |
| LlamaIndex `total_vectors` 固定为 0 | RESOLVED | 当前实现从 FAISS `ntotal` 读取统计 |
| LoRA Adapter 未接入在线 Agent | OPEN | 独立离线实验保持不变，正式在线 Agent 仍使用 DeepSeek |
| Redis 缓存、限流和幂等缺失 | RESOLVED | Phase 8 实现，Phase 12 使用 Redis 7.4 验收状态与 TTL |
| JWT 和用户系统缺失 | RESOLVED | Phase 9 实现注册、登录、JWT 和资源归属 |
| MySQL 会话和聊天记录缺失 | RESOLVED | Phase 7 实现；Phase 12 使用 MySQL 8.4 验收 |
| Agent 缺少显式最大工具循环限制 | OPEN | 当前 Graph 调用未显式设置项目级 recursion/tool-loop 上限 |
| Tool 失败监控只识别“错误”前缀 | OPEN | `工具执行失败:` 仍可能被 monitor 标为成功 |
| `StateManager.clear_session()` 名实不符 | OPEN | 旧 Streamlit helper 仍只返回成功文案，没有删除 thread |
| FAISS 危险 pickle 反序列化 | MITIGATED | 仅允许受控本地索引；代码仍启用 dangerous deserialization，不支持未知来源索引 |
| LoRA 脚本本机绝对路径 | OPEN | 训练脚本和既有产物仍含本机路径，未纳入平台迁移范围 |
| Python PATH 多解释器风险 | MITIGATED | Phase 12 使用独立 D 盘 Windows CPU venv 和显式 executable |
| 可选训练环境包冲突 | OPEN | 平台隔离 venv 的 `pip check` 已通过；LoRA 环境仍需独立维护 |

另外，真实 DeepSeek/BGE 在线验收、Maven/Python 专用 CVE 扫描、生产容量/灾备/渗透测试和
`UNKNOWN` 迟到结果对账仍属于后续独立工作，不在已完成的同步 v1 Phase 范围内。

## Phase 2 后续演进记录

- `UNKNOWN` 结果对账与回收：当前同步 v1 不具备读取超时后回收 Python 迟到结果的能力。问题、限制、候选方向和进入实现前必须回答的问题统一记录在 `docs/DECISIONS.md` 的 `P2-FUTURE-001`；该记录不改变当前 UNKNOWN 不自动重试规则，也不代表任何候选方案已经获批或实现。

## 不属于技术债的 Phase 0.5 修复

以下问题已被本阶段明确授权，因此不延后：

- 将导入期断言迁移为规范、离线、可重复的 pytest；
- 修复 `PremiumCalculatorTool` 忽略注入 `PremiumService` 的生命周期错误；
- 按真实 import 拆分核心、开发测试、LlamaIndex 和 LoRA 依赖；
- 统一 0.5B、近 500 行和 LoRA 未接入在线链路等文档事实；
- 记录并尽可能验证 Python 运行基线。
