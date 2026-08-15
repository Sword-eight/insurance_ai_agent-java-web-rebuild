# LangChain vs LlamaIndex Fair Benchmark

> 执行日期：2026-08-15
> 性质：独立证据验收，不是产品 Phase
> 最终判定：**PASS with WARNING**

## 1. Benchmark Setup

| 项目 | 固定值 |
|---|---|
| 机器 | `Windows-10-10.0.19045-SP0`；`Intel64 Family 6 Model 158 Stepping 10, GenuineIntel` |
| Python | `3.12.6` |
| LangChain | `langchain-core 1.5.4` / `langchain-community 0.4.2` |
| LlamaIndex | `llama-index-core 0.14.23` / `llama-index-vector-stores-faiss 0.6.0` |
| Embedding | `BAAI/bge-base-zh-v1.5`，CPU，normalize=true，768 维 |
| FAISS | `faiss-cpu 1.15.0`，两个独立持久化索引 |
| LLM | 真实 `deepseek-chat`，temperature=0，max_tokens=4096，SDK retry=0 |
| Chunk | size=500，overlap=100，先生成同一份 engine-independent chunks |
| Retrieval | top_k=5；warm-up 后每题重复 3 次 |
| Corpus | 1 份受控 TXT，5 chunks |
| Dataset | 50 cases；其中 48 个纳入 retrieval denominator，15 个纳入 generation，6 个/引擎纳入公共 API E2E |

语料只采用已跟踪且能可靠抽取的《中国人寿重大疾病保险条款.txt》。`insurance_terms_test.pdf` 只能抽取乱码标题，另一份已跟踪 PDF 无可抽取文本，因此二者没有进入 ground truth。未跟踪 PDF（包括用户明确保护的文件）均未读取、修改或纳入评测。

## 2. Fairness Controls

- 两边使用完全相同的原始文本、`benchmark_chunks.jsonl`、问题、ground truth、BGE 模型、normalize 设置、768 维、CPU、FAISS 和 top_k。
- LangChain 和 LlamaIndex 分别从同一 5 个固定 chunks 构建独立索引，没有调用各自默认 splitter。
- 真实生成走相同产品 `SYSTEM_PROMPT`、相同 `deepseek-chat` 参数和相同 history；generation case 按 LC/LI、LI/LC 交替顺序执行。
- Retrieval latency 先各做一次 warm-up，再对每题测 3 次，共 150 条/引擎。公共 API E2E 因单端口隔离生命周期按引擎顺序执行，没有伪装成逐题交替。
- 未针对任何引擎调整 Prompt、chunk、top_k、Router 或 Retriever 算法；没有因结果删除 case 或选择最佳重跑。

Ground truth 在运行前冻结于 `evidence/rag_engine_benchmark/cases.json`。无答案的 BENCH-48/49 不具备可靠相关 chunk 标签，按规则排除 retrieval denominator。

## 3. Index Build Results

| Metric | LangChain | LlamaIndex |
|---|---:|---:|
| 文档 / chunks / embeddings | 1 / 5 / 5 | 1 / 5 / 5 |
| Total build time | 1943.694 ms | 2197.857 ms |
| Load time | 3.759 ms | 13.580 ms |
| Persisted index size | 21637 bytes | 46049 bytes |

框架没有暴露可等价拆分的 embedding/build/persist 分段计时，所以不伪造这些阶段指标；峰值 RSS 也未记录。

## 4. Retrieval Quality

| Metric | LangChain | LlamaIndex |
|---|---:|---:|
| Recall@1 | 0.5521 | 0.5521 |
| Recall@3 | 0.8750 | 0.8750 |
| Recall@5 | 1.0000 | 1.0000 |
| MRR | 0.8201 | 0.8201 |
| nDCG@5（binary relevance） | 0.8643 | 0.8643 |
| Document Hit Rate@5 | 1.0000 | 1.0000 |
| Page Hit Rate@5 | 1.0000 | 1.0000 |

两边排名完全一致。因为语料只有 5 个 chunks 且 top_k=5，Recall@5、document/page hit 必然缺乏区分力；更有信息量的是 Recall@1、Recall@3 和 MRR。Page Hit 仅有 page=1，也不能外推 PDF 页级能力。

## 5. Retrieval Performance

| Metric | LangChain | LlamaIndex |
|---|---:|---:|
| mean | 59.088 ms | 56.257 ms |
| P50 | 55.232 ms | 53.511 ms |
| P95 | 77.911 ms | 73.621 ms |
| min | 44.447 ms | 41.972 ms |
| max | 163.499 ms | 170.278 ms |

LlamaIndex 的本轮 P50/P95 分别低约 3.1%/5.5%，但绝对差仅约 1.7/4.3 ms，且没有做显著性检验；不能据此声称稳定性能优势。

## 6. Generation Quality

| Metric | LangChain | LlamaIndex |
|---|---:|---:|
| 样本数 | 15 | 15 |
| 成功 / ERROR | 14 / 1 | 15 / 0 |
| Correct | 14 | 14 |
| Partial | 0 | 1 |
| Incorrect | 0 | 0 |
| Faithfulness rule proxy | 0.9286 | 0.9333 |
| Hallucination rule proxy | 0.0000 | 0.0000 |

质量判断是预冻结 `requiredAll` / `requiredAny` / `forbiddenClaims` 的客观字面规则，不是人工评审，也没有伪称 LLM-as-Judge。BENCH-01 的语料写“十六”而答案写“16”，导致两边 faithfulness 字面 proxy 均记为不支持；这是已知规则局限。未执行盲 A/B Judge，因为没有独立可信 Judge 或人工评审。

## 7. Token Usage

| Metric | LangChain | LlamaIndex |
|---|---:|---:|
| total tokens（成功请求） | 57685 | 61927 |
| mean | 4120.36 | 4128.47 |
| P50 | 4006.00 | 4022.00 |
| P95 | 4619.40 | 4575.60 |

LangChain total 只覆盖 14 个成功样本，LlamaIndex 覆盖 15 个，不能直接用 total 判断引擎成本。平均 token 基本相同。运行时没有提供可靠的当前计价信息，因此不计算人民币或美元成本。

## 8. End-to-End Latency

正式链路：Spring Boot public API → FastAPI → LangGraph → RAG Tool → Retriever → DeepSeek → Java。

| Metric | LangChain | LlamaIndex |
|---|---:|---:|
| success | 6/6 (100.00%) | 5/6 (83.33%) |
| timeout / other error | 0 / 0 | 0 / 1 |
| P50（成功请求） | 5058.981 ms | 5022.594 ms |
| P95（成功请求） | 5488.682 ms | 6812.055 ms |
| min / max | 4643.064 / 5511.032 ms | 4687.583 / 7016.646 ms |

两个引擎在同一机器、同一网络窗口、隔离临时 MySQL/Redis 上顺序执行。LlamaIndex 的失败请求不进入成功请求 latency 分布，因此不能只看其 P50 判断更快。

## 9. Failure Cases

- LangChain generation `BENCH-05`：真实 DeepSeek 请求发生 `APITimeoutError`，按原始结果保留，没有挑选重跑。
- LlamaIndex generation `BENCH-41`：回答命中“核定”但漏掉字面规则要求的“10个工作日”，记为 Partial。
- LlamaIndex E2E `BENCH-01`：Spring 返回 HTTP 502 / `AI_EXECUTION_FAILED`；Python 在首次 Agent/DeepSeek 节点返回 `AI_INTERNAL_ERROR`，尚未进入 Retriever。其余 5 个 E2E case 成功。
- 双方 retrieval 的主要失分来自相关 chunk 未排到 Top-1/Top-3；完整逐题排名保存在原始结果 JSON 中。
- 两次 E2E 编排预检失败（readiness 缺 TraceId、JAR 路径含空格）发生在正式 E2E 请求前，修正 benchmark 脚本后才产生上表结果；没有修改产品代码。

## 10. Conclusion

在当前保险知识库、固定参数和本地测试环境中，两引擎的 retrieval 排名质量完全相同。LangChain 的索引构建、加载和持久化体积更小；LlamaIndex 的本轮纯 retrieval P50/P95 略低，但差值很小。真实 DeepSeek 生成的客观规则结果接近：LangChain 14 个成功样本均 Correct，但有 1 次 timeout；LlamaIndex 14 Correct、1 Partial、无 generation 异常。公共 API 小样本中 LangChain 6/6，LlamaIndex 5/6。

这些数据不足以证明任一框架“全面更好”。如果以当前小型静态知识库和运行稳定性为优先，本轮证据更支持继续使用 LangChain 作为默认引擎，同时保留 LlamaIndex 的可切换实现；若要做框架选型，应先补充多文档、真实 PDF 页码、更多 chunks、重复生成与更大 E2E 样本。

## Resume-safe Conclusion

### 可以写进简历

实现 LangChain/LlamaIndex 双 RAG 引擎可切换运行时，并在同一 BGE、FAISS、固定 chunks、top_k 和真实 DeepSeek 条件下构建 50-case 可复现对照评测；本地受控语料中两引擎 Recall@1/3/5 分别为 0.5521/0.8750/1.0000，MRR 为 0.8201，并完成 Spring Boot 公共 API 端到端小样本验证。

### 面试时可以说

公平对比的关键不是直接比较两个框架默认 splitter，而是先冻结 engine-independent chunks 和 ground truth，只让 Retriever 实现成为变量。本轮 retrieval 质量相同，性能差异为毫秒级；生成与 E2E 有少量随机/运行时失败，因此我把结论限制在本地小样本，并保留原始失败而不重跑挑优。

### 不应该说

- “LlamaIndex 全面优于 LangChain”或“LangChain 一定更快”。
- “生产性能提升 xx%”或把本地 P50/P95 当成生产 SLA。
- “人工评审准确率”或“统计显著”，因为本轮没有人工盲评，也没有显著性检验。

## Reproducibility and Artifacts

- 固定题集：`evidence/rag_engine_benchmark/cases.json`
- 固定 chunks：`artifacts/rag_engine_benchmark/benchmark_chunks.jsonl`
- Retrieval 原始结果：`retrieval_results_langchain.json` / `retrieval_results_llamaindex.json`
- Generation 原始结果：`generation_results_langchain.json` / `generation_results_llamaindex.json`
- E2E 原始结果：`e2e_results_langchain.json` / `e2e_results_llamaindex.json`
- 汇总：`benchmark_summary.json`

复现命令需要本地 `DEEPSEEK_API_KEY`，但脚本不会输出或保存它。没有执行 commit 或 push。
