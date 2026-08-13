# Resume Evidence Validation

> 验收日期：2026-08-13
> 分支：`phase-12-final-validation`
> 基线提交：`a95dc00 feat: complete phase 12 final validation`
> 最终判定：**ERROR（Online Evidence 可用；LoRA 证据链不可用）**

本次是独立的简历证据验收，不是产品 Phase。没有修改冻结架构、业务逻辑、Prompt、模型、
训练数据、API、MySQL 或 Redis 设计；没有重新训练 LoRA，也没有 commit 或 push。

## 1. Evidence Summary

| 项目 | 结果 | 是否可写简历 | 证据 |
|---|---|---:|---|
| Java tests | 90 tests，0 failure/error/skipped，BUILD SUCCESS | 是 | `baseline_summary.json` |
| Python tests | 77 passed，0 failed/skipped，2 warnings | 是 | `baseline_summary.json` |
| Vue tests/build | 8 files / 26 tests；typecheck/build PASS | 是 | `baseline_summary.json` |
| MySQL/Redis 集成 | MySQL 8.4 + Redis 7.4 + Spring/Python HTTP PASS | 是 | 本轮 Java Testcontainers；Phase 12 TestMatrix |
| Chrome E2E | Phase 12 确定性链路 PASS；本轮真实 DeepSeek 浏览器聊天 PASS | 是，注明本地验收 | `browser_smoke_summary.json` |
| DeepSeek 真实在线调用 | `deepseek-chat`，34/34 公共 API 请求成功；另有 Chrome smoke | 是，注明小样本 | `online_ai_results.json` |
| BGE 真实加载 | `BAAI/bge-base-zh-v1.5`，CPU，768 维 | 是 | `bge_faiss_summary.json` |
| FAISS 真实检索 | 3 个受控文档、23 chunks/vectors、Top-5，查询命中等待期 | 是 | `bge_faiss_summary.json` |
| Tool Routing | 严格有序调用序列 25/34（73.53%） | 可写原始实测，不宜包装成高准确率 | `online_ai_summary.json` |
| RAG Sources | 预期 RAG 的 18 次请求均未返回公共 `sources`：0% | 否；应披露缺口 | `online_ai_summary.json` |
| 在线请求延迟 | 平均 5.832s，P50 5.636s，P95 10.829s | 可写“本地小样本” | `online_ai_summary.json` |
| LoRA Base 指标 | 未运行：原 eval set 缺失 | 否 | `lora_eval_results.json` |
| LoRA Adapter 指标 | 未运行：Adapter 权重缺失 | 否 | `lora_eval_results.json` |

机器文件位于 [`artifacts/resume_evidence/`](../artifacts/resume_evidence/)，完整命令与命令修正见
[`commands.md`](../artifacts/resume_evidence/commands.md)。所有路径均为仓库相对路径或脱敏说明，
没有保存 DeepSeek Key。

## 2. Backend Evidence

本轮先重新执行工程基线，结果如下：

| 层级 | 实际命令 | 结果 | 墙钟耗时 |
|---|---|---:|---:|
| Java | Maven `clean package`，D 盘本地仓库与隔离 Python | 90 tests，BUILD SUCCESS | 141.830s |
| Python | 隔离 venv `python -m pytest -q` | 77 passed，2 warnings | 29.328s |
| Vue typecheck | `npm run typecheck` | PASS | 2.960s |
| Vue test | `npm test -- --run` | 8 files / 26 passed | 10.978s |
| Vue build | `npm run build` | PASS | 8.515s |

Java 全量测试包含真实 MySQL 8.4 / Redis 7.4 Testcontainers。第一次 Java 执行因 Docker 未启动产生
5 个环境 error；启动 Docker 后按原命令重跑为 90/90。Vue 第一次并发执行出现 worker startup
timeout；隔离串行重跑全部通过。这些环境修正没有修改产品代码。

结合本轮重跑、源码与 Phase 12 冻结验收，以下能力有证据支撑：

- Spring Boot 公共 API、JWT 注册登录与用户资源归属；
- MyBatis-Plus / Flyway / MySQL 会话、请求、消息和文档元数据持久化；
- Redis 近期消息 cache-aside、用户级限流和幂等摘要；MySQL 保持最终防重与事实源；
- `Idempotency-Key`、Java requestId 与 TraceId 分离，TraceId 跨 Java/Python 日志关联；
- Java→Python 3/60 秒连接/读取超时边界，读取超时进入 UNKNOWN 且不自动重发；
- 仅在可证明未送达时最多一次连接级重试；Agent/Knowledge 独立 Resilience4j 熔断器；
- PDF 扩展名、MIME、文件签名、大小、SHA-256 校验，Java 保存原文件；
- Python 受控索引构建与原子 generation/pointer 发布，不提交 FAISS 副本；
- Phase 12 Chrome E2E 已覆盖登录、会话、正常聊天、503、504/UNKNOWN、PDF 和路由保护；
  本轮又通过 Chrome 完成一条真实 DeepSeek 等待期聊天。

这不代表生产容量、灾备、渗透测试、专用 Maven/Python CVE 扫描或生产 SLA 已完成。

## 3. Online AI Evidence

### 3.1 环境与链路

本轮真实链路为：

```text
Vue / Evidence HTTP Client
  -> Spring Boot public API
  -> FastAPI internal API
  -> LangGraph
  -> insurance_rag_search / premium_calculator
  -> BGE + FAISS
  -> DeepSeek deepseek-chat
  -> Java persistence
  -> Client
```

DeepSeek Key 只从宿主 User 环境变量读取并注入 Python 子进程。初始 Key 的最小请求返回 401；
用户更新后最小请求返回 200，正式链路 smoke 和固定题集均成功。任何命令、日志或 artifact 都没有
记录 Key、前后缀或长度。

### 3.2 固定测试集

测试集在模型结果出现前冻结于
[`evidence/resume_evidence/cases.json`](../evidence/resume_evidence/cases.json)：30 个 case、34 次请求，
覆盖条款 RAG、保费正常/边界/缺参、直接回答、Prompt 安全、多工具、4 组多轮和知识库未知问题。
没有使用缺失的 LoRA train/eval 数据，也没有因结果修改问题或重跑选最好结果。

### 3.3 指标

| 指标 | 实测 |
|---|---:|
| 请求成功率 | 34/34 = **100%** |
| Tool 严格路由正确率 | 25/34 = **73.53%** |
| 工具集合一致（忽略顺序/重复，仅补充诊断） | 31/34 = 91.18% |
| RAG 公共 sources 返回率 | 0/18 = **0%** |
| 固定字面质量规则通过 | 32/34 = 94.12% |
| 多轮场景通过 | 4/4 |
| 端到端平均 / P50 / P95 | 5.832s / 5.636s / 10.829s |
| 端到端 Min / Max | 1.508s / 14.295s |
| Java→Python 平均 / P50 / P95 | 5.744s / 5.551s / 10.737s |
| Python HTTP 平均 / P50 / P95 | 5.731s / 5.516s / 10.719s |
| Python Agent duration | 当前公共响应/结构化日志不可获得 |

**该结果来自本地/开发环境的小规模 Evidence Test，不代表生产 SLA。**

严格路由以实际有序 Tool 调用序列与预期完全相等为准。9 条未满足全部指标的记录全部保存在原始
结果中：6 条是 RAG 重复调用导致序列不同，`PREMIUM-05` 缺参时仍调用 Premium+RAG，
`PREMIUM-06` 非法年龄又额外调用 RAG，`MULTI-02` 根据历史直接回答而未再次调用 RAG。

字面规则的两条失败需要和原始分数一起解释：

- `RAG-06` 实际答案写明“至少3项”，固定规则要求精确子串“至少三项”；
- `UNKNOWN-02` 实际明确说明“并非按天给付”，却命中了禁词“每天给付”。

两条都属于规则型假阴性，但本报告保留原始 32/34，不把人工复核包装成新的“回答准确率”。

### 3.4 BGE / FAISS

- 模型：`BAAI/bge-base-zh-v1.5`，CPU，normalize embeddings，768 维；
- 引擎：LangChain + FAISS；`top_k=5`；chunk 500、overlap 100；
- 受控来源：2 PDF + 1 TXT，共 3 个文档；本轮重新构建 23 chunks/vectors；
- 独立 similarity search 返回 5 条，Top-1 为《中国人寿重大疾病保险条款.txt》，包含 180 天等待期；
- 该查询检索耗时 97.09ms；离线加载加查询 18.077s；
- 公共 API Facade 当前固定返回空 `sources`，因此 Retrieval Hit Rate 不从批量 API 伪造，记为不可得。

## 4. LoRA Offline Evidence

LoRA 未接入在线 DeepSeek Agent。本轮没有重新训练，也没有成功加载 Adapter。

### 4.1 可验证事实

| 项目 | 当前证据 |
|---|---|
| Base Model | Qwen2.5-0.5B-Instruct；本地模型文件存在 |
| Base 参数 | 494,032,768（从 safetensors header 统计） |
| Adapter 配置 | LoRA rank 8、alpha 16、dropout 0.1 |
| target_modules | q/k/v/o_proj、gate/up/down_proj |
| 推导可训练参数 | 4,399,104；由模型结构与配置计算，非训练日志输出 |
| 实际脚本参数 | lr 5e-5、3 epochs、batch 1、grad accumulation 8、max length 512、FP16 |
| 训练状态 | 18 steps；loss 3.4412 → 2.8403 → 2.6337 |
| Eval Loss | 无记录 |
| Adapter 权重 | 缺失，大小不可得 |
| 原 train/eval | 两者均缺失；独立性不可验证 |
| GPU | GTX 1650，4096 MiB，driver 591.74 |
| 原训练耗时 | 无可靠记录 |

README/`lora_train_config.json` 描述的是 rank 16 / alpha 32 和另一套 batch/length 参数；现存 Adapter
配置与实际 `run_train.py` 是 rank 8 / alpha 16，存在配置漂移。因此不能把 README 参数当成该
Adapter 的实际参数。

历史报告声明 14 个样本、57.62% 数字关键词准确率、24.3244s/条；但评估代码只匹配数字集合，
语义错误答案也可能得到 100%，并且只保留 5 条样例。Adapter 权重和原 eval set 缺失后，无法用
相同数据、相同生成参数比较 Base 与 LoRA，也无法检查训练/评估泄漏。

| 旧指标 | 本轮复现 | 结论 |
|---|---:|---|
| 79.69% | 否 | **旧指标未复现，不建议继续用于简历** |
| +10.5% | 否 | **旧指标未复现，不建议继续用于简历** |
| Eval Loss 1.18 | 否 | **旧指标未复现，不建议继续用于简历** |
| 8.0s/条 | 否 | **旧指标未复现，不建议继续用于简历** |
| 历史 57.62% | 文件存在但方法失效、不可复验 | 不应描述为模型准确率 |
| 历史 24.3244s/条 | 文件存在但不可复验 | 不应作为当前性能 |

符合“Adapter 不存在、原 eval set 不存在、配置无法一致确定”的条件，**建议重新训练**；但本次
没有启动训练。重新训练应另行批准，并首先固定、版本化且隔离 train/dev/test 数据及有效指标。

## 5. Resume-safe Claims

### 允许写

- 构建 Vue 3 → Spring Boot → FastAPI → LangGraph/Tool/RAG 的保险 AI 平台，完成 JWT、MySQL、
  Redis、幂等、TraceId、超时/有限重试/熔断与 PDF 索引链路；本地回归为 Java 90、Python 77、
  Vue 26 项测试通过。
- 使用 `BAAI/bge-base-zh-v1.5` 生成 768 维向量并通过 FAISS 完成受控保险条款检索；本轮索引
  3 个文档、23 个 chunks/vectors，真实查询 Top-1 命中 180 天等待期条款。
- 在真实 DeepSeek `deepseek-chat` 下，以 30-case / 34-request 固定集完成本地端到端验收，
  公共 API 请求成功率 100%，严格 Tool 调用序列匹配 73.53%，P50/P95 约 5.64/10.83 秒。
- 通过 Chrome 实测注册、登录、创建会话和真实等待期问答；回答包含 180 天并引用受控条款。

### 建议改写

- 不写“RAG 来源返回完整”；改为“内部 FAISS 检索真实命中，但当前公共 `sources` 字段返回率为 0，
  已识别为证据/产品缺口”。
- 不写“Tool 路由准确率 91.18%”；主指标是严格序列 73.53%，91.18% 仅是忽略重复和顺序的诊断。
- 不写“回答准确率 94.12%”；这是固定字面规则通过率，且包含两个已知规则假阴性。
- LoRA 只能写成“实现过离线训练/评估实验的代码与配置”，不能写成可加载、已复验或在线能力。

### 不应写

- “LoRA 已接入在线 Agent”或“线上使用 Qwen LoRA”；在线模型是 DeepSeek。
- “LoRA 79.69% / 提升 10.5% / Eval Loss 1.18 / 8.0s/条”。
- “RAG sources 返回率/Recall@K/MRR 很高”；公共 sources 是 0%，Recall@K/MRR 没有客观标注集。
- 生产 SLA、生产容量、生产可用性或安全认证。

## 6. Claims To Remove

1. 删除或降级所有 `79.69%`、`+10.5%`、`Eval Loss 1.18`、`8.0s/条` LoRA 数字。
2. 删除把历史 `57.62%` 描述为“准确率”的文本；其方法只比较数字且无法重放。
3. 删除把 LoRA Adapter 描述为当前可加载产物的文本；权重文件不存在。
4. 删除把 LoRA 描述成在线 DeepSeek Agent 组成部分的文本。
5. 删除公共 RAG 来源已完整返回、已测 Recall@K/MRR 的文本。
6. 删除把本地 34 请求延迟描述为生产 SLA 的文本。
7. 删除把 README 中 rank 16 / alpha 32 配置描述成现存 Adapter 实际配置的文本。

## 7. Final Verdict

**ERROR**。

在线工程证据真实、可复现且足以支撑上述 Resume-safe Claims：真实 DeepSeek、BGE、FAISS、公共 HTTP
和 Chrome 均已验证。但 LoRA 部分同时存在 Adapter 权重缺失、原 train/eval 数据缺失、配置漂移、
测试方法失效和旧指标无法解释/复现，符合用户定义的 ERROR 条件。删除所有 LoRA 数值型简历声明后，
在线平台与工程测试声明仍可安全使用；LoRA 若需重新进入简历，必须另行批准重新训练与独立评估。

## 8. Sources fix 后复验（2026-08-13）

这是独立的 RAG Sources Trace & Fix 复验，不是新产品 Phase，也没有重跑 LoRA。修复前原始结果已保留为
`online_ai_results_before_sources_fix.json` 和 `online_ai_summary_before_sources_fix.json`；当前
`online_ai_results.json` / `online_ai_summary.json` 是修复后、干净日志环境下的完整 34-request 结果。

根因有两处：`InsuranceRAGTool._run()` 把 `RetrievalResult` 降为仅供 LLM 阅读的字符串，Graph 随后未能把
真实 `RetrievalDocument` 写入已有 `retrieved_docs` state；`DefaultAgentFacade` 又固定返回空 sources。
修复采用既有 Graph State 显式携带结构化来源，Facade 映射为冻结字段
`documentName/page/snippet/score`。FAISS 的 `numpy.float32` score 在进入 checkpoint state 前转换为
Python `float`，避免序列化失败。公共 API 契约未改变，Tool Routing、Prompt、top_k、chunk、Embedding、
DeepSeek、MySQL 和 Redis 均未修改。

复验结果：

| 指标 | 修复前 | 修复后 |
|---|---:|---:|
| 公共 API 请求成功 | 34/34 | 34/34 |
| expected-RAG 请求 sources 非空 | 0/18 | **17/18（94.44%）** |
| 实际执行 RAG 的请求 sources 非空 | 不可得 | **19/19（100%）** |
| expected-RAG 每请求平均 sources | 0 | **5.67** |
| sources 非空请求平均 sources | 不适用 | **6.00** |
| expected source 文档命中 | 不可得 | **100%（有标注记录）** |
| 严格 Tool Routing | 25/34（73.53%） | 25/34（73.53%） |
| 端到端平均 / P50 / P95 | 5.832s / 5.636s / 10.829s | **5.361s / 5.426s / 8.131s** |

唯一 expected-RAG 但 sources 为空的是 `MULTI-02` 第 2 轮：模型使用请求携带的上一轮回答直接作答，
该次 invocation 没有调用 Retriever。系统没有沿用或伪造上一轮来源，符合“sources 必须来自本次真实
Retriever/Tool 执行数据”的约束。因此结果如实记录为 17/18，而不是通过修改 Router 或 Prompt 追求
18/18。

实际 sources 共记录 112 条 page 和 score，文档名仅出现受控索引中的
`中国人寿重大疾病保险条款.txt` 与 `insurance_terms_test.pdf`；score 范围约为 0.4496～0.7173。
没有发现索引外或模型生成的错误来源。非 expected-RAG 的 `PREMIUM-05`、`PREMIUM-06` 因模型实际额外
调用 RAG 而返回来源；这是原有 Tool Routing 行为，未在本任务中优化。

修复后工程回归：Python **84 passed**；Java **92 tests, 0 failures/errors/skipped, BUILD SUCCESS**；
Vue **8 files / 27 tests**、typecheck、production build 均通过。最终判定：**PASS with WARNING**——
真实来源已稳定通过正式链路返回，架构和工程基线无回归；但 expected-RAG 口径为 17/18，且原有 Router
重复/额外调用问题仍按任务边界保留。
