# RAG Dual-Engine Runtime Validation

> 日期：2026-08-15
> 类型：独立修复验收，不是新的产品 Phase
> 结论：PASS with WARNING

## 范围与配置链

本次修复只完成 FastAPI **启动时**引擎选择，不包含运行时热切换、Vue/Java 切换入口、每请求选择或双引擎同时服务。冻结边界仍为：

```text
RAG_ENGINE environment
  -> config.get_rag_engine()
  -> FastAPI lifespan / init_api_runtime(engine)
  -> application.bootstrap
  -> matching IndexBuilder + Retriever
  -> KnowledgeService / RetrievalService
  -> InsuranceRAGTool
  -> LangGraph
  -> readiness / knowledge status
```

允许值为 `langchain`、`llamaindex`，默认 `langchain`。配置会去除首尾空格并忽略大小写；非法值直接失败。选择 LlamaIndex 但未安装可选依赖时，启动错误会明确指向 `requirements-llamaindex.txt`，不会回退到 LangChain。

## 双引擎证据

两种参数使用同一受控临时文档 `controlled-insurance-terms.txt` 和同一查询：

```text
What is the waiting period for the controlled Aurora insurance policy?
```

文档包含唯一受控事实 `180 days`。索引和文档均位于 pytest 临时目录，测试结束后清理；用户保留的未跟踪 PDF 未被读取、修改或用于建库。

| 项目 | LangChain | LlamaIndex |
|---|---|---|
| 正式 FastAPI lifespan 启动 | PASS | PASS |
| 实际对象图 | `LangChainIndexBuilder` + `LangChainRetriever` | `LlamaIndexBuilder` + `LlamaIndexRetriever` |
| readiness `ragEngine` | `langchain` | `llamaindex` |
| status `indexLoaded` | `true` | `true` |
| 真实 BGE embedding | PASS | PASS，共享进程级模型 |
| 真实 FAISS 建库/检索 | PASS | PASS |
| 预期文档命中 | PASS | PASS |
| FastAPI→Graph→RAG Tool→Retriever | PASS | PASS |
| structured `sources` 返回 | PASS | PASS，命中受控文档和 `180 days` snippet |
| 真实 DeepSeek | 否，使用确定性 LLM 测试替身 | 否，使用确定性 LLM 测试替身 |

正式集成测试仅替换在线 LLM，以确定性工具调用避免外部模型路由波动；FastAPI 默认 lifespan、bootstrap、Builder、Retriever、Service、RAG Tool、LangGraph、BGE、FAISS 和 sources 组装均使用正式实现。

## 索引隔离与分数语义

- LangChain：`langchain-generations/<generation>`，活动指针为 `LANGCHAIN_CURRENT`。
- LlamaIndex：`llamaindex/generations/<generation>`，活动指针为 `llamaindex/CURRENT`。
- 两个测试分别断言另一引擎的活动指针不存在，没有跨引擎读取或覆盖。
- 两个引擎都必须遵守公开 `score` 的 `0..1` 相似度契约。LlamaIndex 将单位归一化 BGE 向量的 FAISS squared-L2 distance 换算为 cosine similarity；原生距离只保留在内部 metadata 供审计，不进入公共响应。

## 执行命令与结果

本机工具和缓存均位于 D 盘隔离目录；以下以可移植形式记录命令，未记录密钥或本机绝对路径：

```powershell
python -m pip install -r requirements-llamaindex.txt
$env:HF_HUB_OFFLINE='1'
$env:TRANSFORMERS_OFFLINE='1'
python -m pytest -q
mvn -Dmaven.repo.local=<D-drive-repository> `
  -Dphase12.python.executable=<D-drive-venv-python> test
npm run typecheck
npm test
npm run build
.\start-local.cmd -RagEngine langchain -ValidateOnly
.\start-local.cmd -RagEngine llamaindex -ValidateOnly
```

| 回归 | 结果 |
|---|---|
| Python | 99 passed，3 个上游 deprecation/future warnings |
| Java | 92 tests，0 failures，0 errors，BUILD SUCCESS |
| Vue typecheck | PASS |
| Vue tests | 8 files / 27 tests PASS |
| Vue production build | PASS |
| 启动器参数校验 | LangChain PASS；LlamaIndex PASS |

## 限制与结论

本轮没有对两个引擎分别调用真实 DeepSeek，因此不声称答案质量、分数或排序相同，也不提供模型效果指标。真实 DeepSeek 的既有平台链证据单独记录在 `RESUME_EVIDENCE.md`，不能替代双引擎对照中的确定性路由证明。

配置、对象图、索引隔离、真实 BGE/FAISS 检索、正式 Agent RAG 路径、readiness/status 和 sources 均已通过；因双引擎对照未使用真实 DeepSeek，最终结论为 **PASS with WARNING**。

## 后续公平评测补充

上述结论是“双引擎运行时补全”当时的确定性验收，保持不变。随后独立执行了
[LangChain vs LlamaIndex Fair Benchmark](./RAG_ENGINE_BENCHMARK.md)，补齐同一固定 chunks、
同一 BGE/FAISS/top_k、同一产品 Prompt 与真实 `deepseek-chat` 条件下的对照：

- 1 个可可靠提取的受控 TXT，5 个 engine-independent chunks，50 cases；
- 48 个带可靠 retrieval ground truth 的 case，每题 warm-up 后重复 3 次；
- 两边 Recall@1/3/5 均为 0.5521/0.8750/1.0000，MRR 均为 0.8201，50 个 case 排名完全一致；
- 15 个真实 DeepSeek generation case：LangChain 14 Correct + 1 timeout，LlamaIndex 14 Correct + 1 Partial；
- Spring 公共 API E2E：LangChain 6/6，LlamaIndex 5/6；LlamaIndex 的失败发生在 Retriever 之前的 Agent/LLM 节点，不能归因于检索器。

后续评测把双引擎证据从“功能等价”扩展到“小样本性能与生成效果对照”，但仍只有单文档、
5 chunks 和每 generation case 1 次调用，因此最终仍为 **PASS with WARNING**，不支持“任一引擎全面更优”或生产性能结论。
