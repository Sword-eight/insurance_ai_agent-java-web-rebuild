# Insurance AI Platform 迁移计划（Phase 0 草案）

> 项目目标名：Insurance AI Platform / 保险智能问答平台  
> 文档状态：DRAFT，等待 Review，不代表架构已经冻结  
> 审查日期：2026-07-30  
> 审查分支：`phase-0-source-audit`  
> 基线提交：`ce591cb56188b7fd6a0b27c8f3263f0872c82f1c`

## 1. Phase 0 范围

本阶段只做源码审查和迁移计划，不实现任何 Java、FastAPI 或业务功能。

### 本阶段计划

1. 检查 Git 分支、工作区和仓库边界。
2. 读取第一方源码、配置、测试、训练脚本和 Git 历史。
3. 复原当前入口、对象生命周期和完整调用链。
4. 对照项目描述，区分已实现、部分实现、未实现和无法验证。
5. 分类可保留、需包装、Java 新增和禁止迁移的模块。
6. 形成迁移阶段、风险和验收草案。

### 本阶段修改文件

- `AGENTS.md`：长期协作规则草案。
- `docs/MIGRATION_PLAN.md`：本文件。
- `learning/phase-0/MiniCourse.md`：源码审查与迁移思路教学。

### 本阶段明确不修改

- `app.py`
- `application/`
- `graph/`
- `tools/`
- `services/`
- `rag/`
- `memory/`
- `prompts/`
- `ui/`
- `finetune/`
- `tests/`
- `config.py`、`requirements.txt` 和任何数据、模型、索引、日志
- 不创建 `docs/ARCHITECTURE.md`；该文件属于 Phase 1
- 不创建 Spring Boot 或 FastAPI Skeleton

### Phase 0 验收标准

- 目录树来自当前工作区，而不是项目描述。
- 当前入口和聊天、RAG、知识库、会话、LoRA 调用链可追到具体文件。
- 四类迁移清单完整：直接保留、需要包装、Java 新增、禁止迁移。
- 所有描述与源码差异均有证据。
- 只新增文档，没有业务代码变更。
- 停在 Phase 0，等待 Review。

## 2. 审查基线与限制

### 2.1 Git 状态

审查开始时仓库位于 `main`，且已有用户修改。现已切换到独立分支
`phase-0-source-audit`，原有修改原样随分支保留。

审查前已存在的非本次改动包括：

- 已修改：`config.py`、数据集构建/训练脚本、训练统计、Adapter 配置和评估报告。
- 已删除：`finetune/outputs/checkpoint-18/` 中 3 个被跟踪文件。
- 未跟踪：`checkpoint-150/`、`checkpoint-186/`、两个评估/推理脚本、
  三个文档生成脚本和 `insurance_ai_agent.zip`。

这些文件没有被 Phase 0 修改、还原或提交。

### 2.2 审查覆盖

- 读取了 65 个第一方 Python 文件并完成 AST 语法解析，结果为 0 个语法错误。
- 读取了项目 README、微调设计文档、配置、依赖、测试和评估结果。
- 检查了 PDF 文本层、FAISS/LlamaIndex 索引、Adapter/checkpoint 元数据和归档内容。
- 检查了 Git 历史，用于核实单体拆分声明。
- `.env` 只读取变量名，未输出变量值。
- `LLaMA-Factory/` 是被根仓库忽略的独立上游 Git 仓库，共约 560 个文件；
  本阶段只核对其边界、来源和项目调用方式，不审计上游框架内部实现。
- 模型权重、checkpoint、向量索引、日志和 ZIP 属于生成物，只做清单与元数据检查。

### 2.3 本阶段未执行的动作

- 未调用 DeepSeek API，未产生外部费用。
- 未启动 Streamlit，未加载完整 Embedding/LoRA 模型。
- 未运行现有 `tests/`：当前环境没有安装 `pytest`，而三个测试文件把断言和索引构建写在模块顶层；
  `pytest --collect-only` 也会加载模型、读取或重建本地索引，不是安全的只读收集。
- 未对现有业务缺陷做修复。

## 3. 当前真实目录树

以下是当前工作区的项目树。缓存和大型生成物保留真实边界，但不展开每个二进制文件；
`LLaMA-Factory/` 的上游内部目录也不逐项展开。

```text
insurance_ai_agent/
├─ .env                              # 本地密钥文件，已被 .gitignore 忽略
├─ .gitignore
├─ .streamlit/
│  └─ config.toml
├─ app.py                            # 当前唯一在线 Web 入口：Streamlit
├─ config.py                         # LLM/Embedding/RAG/LoRA/日志/UI 配置
├─ requirements.txt
├─ README.md
├─ AGENTS.md                         # Phase 0 新增草案
│
├─ application/
│  ├─ __init__.py
│  ├─ bootstrap.py                   # 手动对象组装
│  ├─ handlers.py                    # chat/upload/rebuild/delete 事件处理
│  └─ session.py                     # Streamlit session_state 会话
├─ graph/
│  ├─ __init__.py
│  ├─ state.py
│  ├─ router.py
│  ├─ nodes.py
│  └─ graph_builder.py               # AgentGraphBuilder
├─ tools/
│  ├─ __init__.py
│  ├─ insurance_rag_tool.py
│  └─ premium_calculator_tool.py
├─ services/
│  ├─ __init__.py
│  ├─ knowledge_service.py
│  ├─ retrieval_service.py
│  └─ premium_service.py
├─ rag/
│  ├─ __init__.py
│  ├─ base_retriever.py
│  ├─ base_index_builder.py
│  ├─ embedding.py
│  ├─ loader.py                      # 向后兼容 re-export
│  ├─ retriever.py                   # 向后兼容 re-export
│  ├─ splitter.py                    # 向后兼容 re-export
│  ├─ vector_store.py                # 向后兼容 re-export
│  ├─ langchain/
│  │  ├─ __init__.py
│  │  ├─ embedding.py                # 共享 Embedding 的 re-export
│  │  ├─ loader.py
│  │  ├─ splitter.py
│  │  ├─ vector_store.py
│  │  ├─ retriever.py
│  │  └─ index_builder.py
│  └─ llamaindex/
│     ├─ __init__.py
│     ├─ retriever.py
│     └─ index_builder.py
├─ memory/
│  ├─ __init__.py
│  └─ state_manager.py
├─ prompts/
│  ├─ __init__.py
│  └─ system_prompt.py
├─ ui/
│  ├─ __init__.py
│  ├─ chat.py
│  ├─ components.py
│  └─ sidebar.py
├─ utils/
│  ├─ __init__.py
│  ├─ helpers.py
│  └─ logger.py
│
├─ config/
│  └─ premium_rate.json
├─ data/
│  ├─ pdf/
│  │  ├─ insurance_terms_test.pdf
│  │  ├─ 中国人寿重大疾病保险条款.pdf
│  │  └─ 中国人寿重大疾病保险条款.txt
│  └─ vectorstore/                   # 被忽略的生成索引
│     ├─ index.faiss
│     ├─ index.pkl
│     └─ llamaindex/
│        ├─ faiss.index
│        ├─ docstore.json
│        ├─ index_store.json
│        ├─ graph_store.json
│        ├─ default__vector_store.json
│        └─ image__vector_store.json
│
├─ finetune/
│  ├─ __init__.py
│  ├─ README.md
│  ├─ SYSTEM_DESIGN.md
│  ├─ dataset/
│  │  ├─ build_dataset.py
│  │  └─ verify_dataset.py
│  ├─ data/
│  │  ├─ insurance_train.json        # 253 条
│  │  ├─ insurance_test.json         # 64 条
│  │  └─ build_stats.json
│  ├─ scripts/
│  │  ├─ train.py
│  │  ├─ train_lora.py
│  │  ├─ train_lora.sh
│  │  ├─ run_train.py
│  │  ├─ evaluate.py
│  │  ├─ eval_real.py
│  │  ├─ evaluate_llm_judge.py       # 未跟踪
│  │  ├─ inference_rag_lora.py       # 未跟踪
│  │  ├─ llamafactory_dataset_info.json
│  │  ├─ _train_simple.py
│  │  └─ _test_load.py
│  ├─ outputs/
│  │  ├─ adapter/                    # 实际 0.5B LoRA Adapter
│  │  ├─ checkpoint-150/
│  │  ├─ checkpoint-186/
│  │  └─ lora_train_config.json
│  ├─ reports/
│  │  ├─ evaluation.md
│  │  └─ evaluation_results.json
│  └─ models/                        # 被忽略的本地模型缓存/未完成下载
│
├─ tests/
│  ├─ test_dual_engine.py
│  ├─ test_langchain_retriever.py
│  └─ test_llamaindex_retriever.py
├─ learning/
│  └─ phase-0/
│     └─ MiniCourse.md               # Phase 0 新增
├─ docs/
│  └─ MIGRATION_PLAN.md              # Phase 0 新增
├─ logs/                             # 6 个运行日志，被忽略
├─ LLaMA-Factory/                    # 独立上游 Git 仓库，被根仓库忽略
├─ generate_doc.py                   # 未跟踪，本机 DOCX 生成脚本
├─ generate_lora_qa.py               # 未跟踪，本机 DOCX 生成脚本
├─ supplement_qa.py                  # 未跟踪，本机 DOCX 追加脚本
└─ insurance_ai_agent.zip            # 未跟踪，约 249.6 MB 的项目归档
```

数据事实：

- `insurance_terms_test.pdf`：1 页，文本层 17 字符。
- `中国人寿重大疾病保险条款.pdf`：1 页，文本层为空。
- 同名 TXT 是当前主要可检索/可生成训练数据的文本来源。
- `build_stats.json` 写着“3 个 PDF”，实际代码把 PDF 和 TXT 都累计到
  `pdfs_processed`，因此字段名/含义不准确。

## 4. 当前启动入口

### 4.1 在线应用入口

唯一在线入口是：

```bash
streamlit run app.py
```

仓库中没有第一方 `FastAPI()`、`APIRouter` 或 `uvicorn` 启动代码，也没有 Java
源文件、`pom.xml` 或 Gradle 文件。

### 4.2 离线入口

| 入口 | 作用 | 当前状态 |
|---|---|---|
| `python -m finetune.dataset.build_dataset` | PDF/TXT → DeepSeek 生成 QA | 当前改版移除了 README 所述 `--resume` |
| `python -m finetune.dataset.verify_dataset` | 清洗、去重、80/20 拆分 | 会覆盖 train/test JSON |
| `python finetune/scripts/run_train.py` | 本机 0.5B LoRA 训练 | 本机绝对模型路径 |
| `python finetune/scripts/train.py` | 通用 HuggingFace/PEFT 训练入口 | 默认 1.5B |
| `python -m finetune.scripts.train_lora` | 生成 LLaMA-Factory 配置 | 不执行训练 |
| `python finetune/scripts/eval_real.py` | 0.5B Adapter 真实推理评估 | 本机绝对模型路径 |
| `python -m finetune.scripts.evaluate` | Mock 评估流水线 | 默认把参考答案当预测答案 |
| `python finetune/scripts/inference_rag_lora.py` | RAG + 0.5B LoRA 实验 | 未接入在线 Agent，且未跟踪 |

根目录三个 `generate*.py`/`supplement_qa.py` 只生成本机桌面 DOCX，不属于运行链路。

## 5. 当前对象图与完整调用链

### 5.1 Streamlit 启动和对象生命周期

```text
streamlit run app.py
  ├─ import config
  │   └─ load_dotenv(.env, override=True)
  ├─ st.set_page_config(...)
  ├─ application.bootstrap.init_services(rag_engine)
  │   ├─ EmbeddingManager()                         # 进程级单例，加载 BGE
  │   ├─ 按引擎创建 Builder + Retriever
  │   │   ├─ langchain:
  │   │   │   VectorStoreManager
  │   │   │   → LangChainRetriever
  │   │   │   → LangChainIndexBuilder
  │   │   └─ llamaindex:
  │   │       LlamaIndexBuilder
  │   │       → LlamaIndexRetriever
  │   ├─ KnowledgeService(builder)
  │   │   └─ 索引不存在则 build；否则 load
  │   ├─ RetrievalService(retriever)
  │   ├─ PremiumService()
  │   ├─ InsuranceRAGTool(service)
  │   ├─ PremiumCalculatorTool(premium_service=...)
  │   │   └─ 实际忽略传入对象，再 new 一个 PremiumService
  │   ├─ AgentGraphBuilder(tools)
  │   │   ├─ ChatOpenAI(DeepSeek-compatible)
  │   │   ├─ InMemorySaver
  │   │   └─ compile StateGraph
  │   └─ StateManager(compiled graph)
  ├─ application.session.init()
  ├─ ui.sidebar.render(...)
  └─ ui.chat.render(...)
```

注意：`init_services()` 没有被 `st.cache_resource` 缓存。Streamlit 每次 rerun 会重新创建
Builder、Retriever、Service、Tools、Graph 和 `InMemorySaver`。Embedding 模型因单例而复用，
但 LangGraph checkpointer 会重置，所以“界面消息历史”和“Agent 实际上下文”是两套生命周期。

### 5.2 同步聊天主链

```text
ui.chat.render
  → _handle_user_input(prompt)
  → app._on_send(prompt)
  → application.handlers.handle_chat_message
  → AgentGraphBuilder.invoke(user_message, session_id)
  → compiled StateGraph.invoke(initial_state, thread_id)
  → AgentGraphBuilder._agent_node
  → safe_truncate_messages
  → ChatOpenAI.bind_tools(...).invoke(...)
  → graph.router.route_after_agent
```

直接回答分支：

```text
route_after_agent → "__end__"
  → application.handlers._parse_agent_result
  → ui.chat.render_agent_response
  → application.session.add_message
```

工具调用分支：

```text
route_after_agent → "tools"
  → AgentGraphBuilder._tools_node
  → tool_instance._run(**tool_args)
  → ToolMessage
  → agent
  → LLM 根据 ToolMessage 继续决策
  → 可再次 tools，或 "__end__"
```

当前图没有最大 Tool 循环次数或显式 recursion limit 配置。

### 5.3 LangChain RAG 链

```text
InsuranceRAGTool._run(query)
  → RetrievalService.search(query)
  → LangChainRetriever.retrieve(query)
  → VectorStoreManager.load_index()                 # 未加载时
  → FAISS.similarity_search_with_score
  → 距离转换为 1 / (1 + distance)
  → RetrievalDocument[]
  → RetrievalResult
  → RetrievalService.format_for_llm
  → 格式化来源文本返回 Agent
```

索引构建链：

```text
KnowledgeService.build_knowledge_base/rebuild
  → LangChainIndexBuilder.build
  → PDFLoader.load_all_pdfs                         # PDF + TXT
  → DocumentSplitter.split_documents
  → VectorStoreManager.build_index
  → FAISS.from_documents
  → save_local(index.faiss + index.pkl)
```

### 5.4 LlamaIndex RAG 链

```text
InsuranceRAGTool._run(query)
  → RetrievalService.search
  → LlamaIndexRetriever.retrieve
  → LlamaIndexBuilder.load_index                    # 未加载时
  → VectorStoreIndex.as_retriever(...).retrieve
  → NodeWithScore → RetrievalDocument
  → RetrievalResult
  → RetrievalService.format_for_llm
```

索引构建链：

```text
KnowledgeService.build_knowledge_base/rebuild
  → LlamaIndexBuilder.build
  → SimpleDirectoryReader
  → SentenceSplitter
  → VectorStoreIndex + FaissVectorStore
  → StorageContext.persist
```

`LlamaIndexBuilder` 会修改全局 `llama_index.core.Settings.embed_model/llm`；
当前统计接口把 `total_vectors` 固定返回 0。

### 5.5 保费链

```text
PremiumCalculatorTool._run
  → PremiumService.calculate
  → config/premium_rate.json
  → Tool 格式化为文本
  → Agent 生成最终回答
```

当前 `bootstrap.py` 创建的 `PremiumService` 没有被 Tool 使用。实测
`PremiumCalculatorTool(premium_service=s)._service is s` 为 `False`，且费率配置被加载两次。

### 5.6 知识库管理链

上传：

```text
ui.sidebar.file_uploader
  → application.handlers.handle_pdf_upload
  → 直接保存到 data/pdf/<uploaded_file.name>
  → KnowledgeService.rebuild
  → Builder.delete
  → Builder.build
```

重建和删除也由 UI → handler → `KnowledgeService` → `BaseIndexBuilder` 实现。
上传文件名未做路径规范化、类型内容校验或大小限制；这是迁移为服务接口前必须处理的安全债。

### 5.7 会话链

当前有两套会话状态：

1. `application/session.py`：仅存于 `st.session_state`，用于 UI 历史和线程切换。
2. LangGraph `InMemorySaver`：以 `session_id` 作为 `thread_id` 保存 Agent 消息。

两者没有统一持久化；`StateManager.clear_session()` 只返回成功消息，没有真正删除状态。
目标架构中，Java/MySQL 应保存业务会话和聊天记录；Python 只接收完成 AI 推理所需的上下文或内部
Agent thread 标识，不访问 Java 数据库。

### 5.8 LoRA 离线链

```text
data/pdf/*
  → DatasetBuilder
  → DeepSeek API 生成 Alpaca QA
  → DatasetVerifier 清洗/去重/拆分
  → insurance_train.json (253) + insurance_test.json (64)
  → run_train.py / train.py / LLaMA-Factory
  → LoRA Adapter + checkpoints
  → eval_real.py
```

在线 `AgentGraphBuilder` 始终创建 `ChatOpenAI` 并调用 DeepSeek-compatible API。
LoRA 只在独立实验脚本中使用，尚未成为在线服务能力。

## 6. 可直接保留的 Python 模块

“直接保留”表示迁移时不重写核心语义；不表示这些模块当前没有缺陷。

| 模块 | 保留理由 | 后续允许的最小调整 |
|---|---|---|
| `graph/` | LangGraph Agent、Router、Tool 循环已存在 | 增加服务入口适配、循环上限、可测试依赖 |
| `tools/` | LangChain Tool 协议适配已经独立 | 修复 Premium DI 和结果状态表达 |
| `services/retrieval_service.py` | 双引擎统一检索与格式化入口 | 增加稳定的服务返回 DTO 转换 |
| `services/knowledge_service.py` | 索引生命周期门面 | 并发保护、错误契约由后续阶段处理 |
| `services/premium_service.py` | 纯本地计算业务 | 保留在 Python Tool 内，不迁到 Java |
| `rag/base_*.py` | Retriever/Builder 抽象真实存在 | 冻结接口前补测试 |
| `rag/langchain/` | PyMuPDF + Splitter + FAISS 完整链 | 安全加载和统计修复 |
| `rag/llamaindex/` | LlamaIndex + FAISS 完整链 | 全局 Settings、统计和依赖修复 |
| `rag/embedding.py` | BGE 单例和双框架适配真实存在 | 生命周期交给 FastAPI startup |
| `prompts/` | Agent 行为与路由 Prompt | 保持 Python 管理 |
| `memory/state_manager.py` | 可作为内部图状态查询封装 | 需修正“清理成功但未清理”语义 |
| `finetune/` | 独立训练/评估子系统 | 清理配置漂移和本机绝对路径 |
| `utils/` | 日志、计时、JSON 基础设施 | 未来接入 TraceId，不整体重写 |

`ui/` 和 `app.py` 可以保留为本地 AI 调试客户端，但不再作为目标平台的正式入口。

## 7. 需要包装的 Python 模块

以下是“新增薄包装”，不是移动或重写现有核心：

| 包装点 | 包装对象 | 职责 | 计划阶段 |
|---|---|---|---|
| FastAPI 应用入口 | `application.bootstrap.init_services` | 在 startup/lifespan 中创建一次对象图 | Phase 3–4 |
| Chat Router | `AgentGraphBuilder.invoke` 或薄 Application Service | HTTP 校验、同步返回、错误映射 | Phase 4 |
| Knowledge Router | `KnowledgeService` | 内部上传、构建、状态查询 | Phase 4/10 |
| Pydantic 请求/响应模型 | handler/graph 返回字典 | 稳定 Java↔Python 契约 | Phase 2/4 |
| Health Router | 模型、索引、依赖状态 | readiness/liveness，不做业务逻辑 | Phase 4/11 |
| LLM Adapter（必要时） | `ChatOpenAI` | timeout、retry、模型配置和错误归一化 | Phase 11 |
| 文件安全适配 | `KnowledgeService` 前置层 | 文件名、大小、MIME、临时文件和原子落盘 | Phase 10 |
| Trace 上下文 | logger / 请求入口 | 接收并回传 Java 的 TraceId | Phase 11 |

目录名和接口路径要到 Phase 1/2 冻结；本草案不提前决定包名。

## 8. Java 侧需要新增的模块

当前仓库没有任何 Java 工程。以下为目标职责清单，不是 Phase 0 创建清单。

| Java 模块 | 主要类（候选名） | 职责 | 首次落地阶段 |
|---|---|---|---|
| 基础工程与 common | `InsurancePlatformApplication`、统一响应、异常、校验 | Spring Boot 3 / Java 17 基线 | Phase 3/5 |
| Python Client | `AgentClient`、`KnowledgeClient` | 内部 HTTP、timeout、retry、circuit breaker | Phase 3/6/11 |
| Chat | `ChatController`、`ChatService` | 同步聊天编排，不直接调 Mapper/Python | Phase 6 |
| Conversation | `ConversationService`、`ConversationMapper` | 会话生命周期与归属 | Phase 7 |
| Message | `ChatMessageMapper` | 用户/助手消息持久化 | Phase 7 |
| User/Auth | `UserController`、`UserService`、`JwtFilter` | 注册、登录、JWT | Phase 9 |
| Knowledge Metadata | `KnowledgeController`、`KnowledgeService`、`DocumentMapper` | 文档元数据和 Python 索引调用编排 | Phase 10 |
| File Storage | 本地文件存储适配 | 上传、校验、保存；第一版不引入对象存储 | Phase 10 |
| Redis | cache、rate limit、session context | 缓存、限流和短期上下文 | Phase 8 |
| Observability | TraceId filter、日志 MDC、health | 可观测性与健康检查 | Phase 11 |
| OpenAPI | SpringDoc 配置 | 对外 REST 文档 | Phase 5/6 |

包结构遵守 Controller → Service → Client/Mapper。当前没有需求支持 Domain Service、
Factory、Command Bus、MQ、Nacos、Kubernetes 或分布式事务。

## 9. 不应该迁移到 Java 的模块

- `graph/`：Agent 状态、节点、Router、Tool 循环。
- `tools/`：LLM Tool schema 和 Tool 执行协议。
- `rag/` 全部：加载、切分、Embedding、FAISS、Retriever、IndexBuilder。
- DeepSeek/ChatOpenAI 调用和 Prompt。
- BGE/SentenceTransformer 模型及生命周期。
- LlamaIndex 全局设置与索引文件。
- LoRA 数据生成、训练、Adapter、推理和评估。
- LangGraph checkpointer 的内部实现。
- AI 检索结果归一化和 LLM 上下文格式化。

Java 可以保存知识库文档的业务元数据和索引任务结果，但不读取 FAISS 文件，也不重写
RAG/Agent 算法。

## 10. 描述与源码核对结果

### 10.1 已由源码证实

| 描述 | 结论 | 证据 |
|---|---|---|
| LangGraph Agent 工作流 | 已实现 | `graph/graph_builder.py` |
| Agent → Router → Tools 循环 | 已实现 | `agent → conditional edge → tools → agent` |
| LangChain + LlamaIndex 双 RAG | 已实现 | `rag/langchain/`、`rag/llamaindex/` |
| `BaseRetriever` / `BaseIndexBuilder` | 已实现 | 两个 ABC 文件及双引擎实现 |
| FAISS + BGE Embedding | 已实现 | `VectorStoreManager`、`EmbeddingManager` |
| DeepSeek API | 已接线，未在线验证 | `ChatOpenAI(base_url=https://api.deepseek.com)` |
| RAG Tool → Service → Repository | 已实现 | `InsuranceRAGTool → RetrievalService → BaseRetriever` |
| bootstrap 手动组装 | 已实现 | `application/bootstrap.py` |
| Streamlit UI | 已实现 | `app.py`、`ui/` |

### 10.2 不一致、部分实现或无法证明

| 原描述/文档声明 | 源码事实 | 影响 |
|---|---|---|
| 已是 Java Backend → Python AI Service 双服务 | 当前只有 Streamlit 单进程 Python 应用 | 目标架构尚未开始 |
| Python AI Service 已可 HTTP 调用 | 无 FastAPI Router/Schema/启动入口 | Phase 4 必须做薄包装 |
| “全部依赖在 bootstrap 注入” | `PremiumCalculatorTool` 忽略传入 `premium_service` 并自行 `PremiumService()` | 生命周期和 DI 声明不真实 |
| “LoRA Qwen2.5-0.5B” | 实际 Adapter/`run_train.py` 是 0.5B；`config.py`、README、LLaMA-Factory 配置仍是 1.5B | 配置和产物漂移 |
| LoRA 是在线能力 | 在线 Graph 只调用 DeepSeek；RAG+LoRA 仅存在于未跟踪实验脚本 | 不能对外宣称在线 LoRA |
| “548 行单体已重构” | Git 中重构前 `app.py` 可核实为 497 个物理行；当前为 70 行 | 拆分事实成立，548 这一数字无历史证据 |
| README `app.py (68行)` | 当前为 70 行 | 文档轻微过期 |
| README 提供 `cp .env.example .env` | 仓库没有 `.env.example` | 新环境不可按文档启动 |
| 双引擎依赖可由 `requirements.txt` 安装 | 未声明 `llama-index` 和 FAISS adapter | 环境不可复现 |
| LoRA 依赖已声明 | 未声明 `torch`、`peft`、`datasets`；`transformers` 仅作为间接依赖出现 | 微调环境不可复现 |
| 使用 pytest | 环境未安装 pytest；测试文件没有测试函数，断言均在导入期执行 | 不是规范 pytest 测试 |
| 多轮对话由 Checkpoint 管理 | Streamlit rerun 重建 `InMemorySaver`；UI 历史与 Graph 历史分离 | Agent 多轮上下文不可靠 |
| StateManager 可清除会话 | `clear_session()` 只返回成功，不删除状态 | 功能名与行为不一致 |
| RAG 来源记录完整 | Graph 只在 Tool 有 `_retriever` 属性时写 `retrieved_docs`，实际 Tool 只有 `_service` | `retrieved_docs` 基本不会写入 |
| Tool 失败监控准确 | 只把 `"错误"` 开头判失败；Tool 返回 `"知识库检索失败"`/`"保费估算失败"` 会被记成功 | 监控状态失真 |
| 文档列表覆盖知识库文档 | LangChain loader/stats 含 TXT，但 `list_documents()` 只列 PDF | UI 统计与列表不一致 |
| LlamaIndex 向量统计可见 | `total_vectors` 固定为 0 | 开发者面板数据失真 |
| DatasetBuilder 支持 `--resume` | 当前工作树实现已移除该参数和断点恢复 | 微调 README 过期 |
| “3 个 PDF”训练统计 | 目录是 2 个 PDF + 1 个 TXT，代码把两类都累计为 `pdfs_processed` | 指标字段命名错误 |
| DeepSeek 模型为稳定配置 | 当前工作树从 `deepseek-chat` 改为 `deepseek-v4-flash`，未执行 API 验证 | 运行有效性待验证 |

额外风险：

- `FAISS.load_local(..., allow_dangerous_deserialization=True)` 会反序列化 `index.pkl`；
  只能加载受信任、本服务生成的索引。
- 上传直接使用客户端文件名，没有路径、大小、MIME 和内容校验。
- 知识库重建/删除没有并发控制，服务化后可能与检索并发冲突。
- Agent 没有显式最大工具循环限制。
- 多个训练脚本硬编码本机 `C:\Users\Administrator\...` 路径。
- 配置与生成文件中存在本机绝对路径，不适合提交为可移植配置。

## 11. 目标边界草案（等待 Phase 1 冻结）

```text
Web Client
    → Java REST API
        → Controller
        → Service
            ├─ Mapper → MySQL
            ├─ Redis → cache / rate limit / short context
            └─ AgentClient / KnowledgeClient
                    → internal HTTP
                        → FastAPI Router
                        → Python application wrapper
                        → existing graph / services / tools / rag
```

约束：

- Web Client 不直接访问 Python。
- Java Controller 不直接访问 Mapper 或 Python Client。
- Python 不访问 Java MySQL/Redis 业务数据。
- Java 传递 `userId`、`conversationId`、`requestId/traceId` 和必要上下文；
  Python 返回 AI 结果、工具摘要、引用来源和内部耗时。
- 第一版聊天同步返回。
- HTTP timeout、有限重试、熔断和幂等语义必须在 Phase 2/6/11 明确；
  当前代码没有这些能力。本草案不提前冻结具体数值。

## 12. 分阶段迁移路线

### Phase 0：源码审查与迁移计划（当前）

- 输出真实基线、调用链、差异、模块分类和迁移路线。
- 不改业务代码。

### Phase 1：冻结架构

- 创建唯一事实来源 `docs/ARCHITECTURE.md`。
- 冻结服务边界、依赖方向、目录职责、部署关系和同步链路。
- 明确 Streamlit 是保留的开发 UI 还是逐步退役的入口。

### Phase 2：冻结契约和存储设计

- 冻结 API、错误码、MySQL 表、Redis key、TTL、限流算法。
- 定义 Java↔Python 内部 DTO、TraceId、超时/重试/熔断语义。
- 不写实现。

### Phase 3：双服务 Skeleton

- 只创建 Java 和 Python 服务骨架、配置结构、接口和占位实现。
- 不接入业务链。

### Phase 4：Python FastAPI 最小包装

- lifespan 只初始化一次现有对象图。
- 提供最小 chat/knowledge/health 内部接口。
- 不重写 graph/tools/services/rag。

### Phase 5：Java Spring Boot 基础工程

- Java 17、Spring Boot 3、Maven、统一响应、校验、异常、OpenAPI。
- 暂不接 MySQL/Redis/用户系统。

### Phase 6：Java 调 Python 聊天链

- `ChatController → ChatService → AgentClient → Python`。
- 保持同步，先完成无数据库的端到端链路。

### Phase 7：MyBatis-Plus、MySQL 与聊天记录

- 会话、消息持久化和查询。
- 正常与 AI 调用失败时的消息状态要有明确事务边界。

### Phase 8：Redis

- 缓存、限流和短期会话上下文。
- 不让 Redis 成为聊天记录唯一事实来源。

### Phase 9：用户与 JWT

- 注册、登录、密码散列、JWT filter、资源归属校验。

### Phase 10：PDF 与知识库管理

- Java 管文档元数据和上传入口，Python 管文件解析与索引。
- 处理文件安全、重建并发和失败状态。

### Phase 11：可观测性与容错

- Java/Python TraceId 贯通、结构化日志、超时、重试、熔断和健康检查。
- 验证不会因重试产生重复消息或重复索引操作。

### Phase 12：测试与最终审计

- Java 单元/集成测试、Python pytest、内部契约测试、端到端异常测试。
- 最终四维审计和架构漂移检查。

## 13. 已知债务的处理建议

| 优先级 | 债务 | 建议处理阶段 |
|---|---|---|
| 高 | Premium Tool 假 DI、对象重复创建 | Phase 4 前的 Skeleton Review 后，经批准修复 |
| 高 | Streamlit rerun 重建 Graph/checkpointer | Phase 4 lifespan 包装时解决 |
| 高 | 依赖清单无法复现、pytest 缺失 | Phase 3/4 与 Phase 12 |
| 高 | 上传文件安全和索引并发 | Phase 10 |
| 高 | HTTP timeout/retry/circuit breaker 缺失 | Phase 6/11 |
| 中 | Agent 无显式循环上限 | Phase 4/11 |
| 中 | Tool 失败被记成功、来源追踪条件失效 | Phase 4/11 |
| 中 | LoRA 0.5B/1.5B 配置漂移和绝对路径 | 独立 AI 维护任务；不得夹带到 Java Phase |
| 中 | LlamaIndex 统计为 0、文档列表漏 TXT | Phase 10 |
| 中 | `StateManager.clear_session()` 名实不符 | Phase 4/8 |
| 低 | 兼容 re-export 文件和过期 README 行数 | 最终文档清理阶段 |

任何修复都必须进入对应 Phase 的计划、Skeleton Review 和实现批准，Phase 0 不改。

## 14. Phase 0 四维审计

### 功能：PASS（针对 Phase 0 交付）

- 已完成源码和产物边界审查。
- 已复原正常聊天和两条工具分支。
- 未伪造运行测试；明确记录了为什么没有运行现有测试。

### 架构：PASS / WARNING

- PASS：迁移边界遵循 Java 业务后端、Python AI 核心，不把 RAG/LangGraph 搬到 Java。
- WARNING：`docs/ARCHITECTURE.md` 尚不存在，当前无法执行“代码对冻结架构”的漂移审计；
  这是 Phase 1 的预期工作，不是自行补建的理由。
- WARNING：当前 Python 基线存在 Premium Tool 跨生命周期创建等声明漂移，已登记。

### 设计：WARNING

- RAG 的抽象和分层可以保留。
- Premium Tool DI、两套会话、Tool 结果状态和部分文档/实现不一致需要后续按 Phase 修复。

### 生命周期：WARNING

- EmbeddingManager 是进程单例。
- Streamlit rerun 会重建 Graph/checkpointer。
- PremiumService 当前创建两次。
- LlamaIndex 使用全局 Settings。
- FastAPI lifespan、HTTP Client 生命周期尚未实现。

### 总结

Phase 0 文档交付结论为 **PASS with WARNING**。没有 Phase 0 范围内的 ERROR；
所有基线 WARNING 已进入债务表。等待用户 Review，不进入 Phase 1。
