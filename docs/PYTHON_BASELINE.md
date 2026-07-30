# Python 运行基线

> 状态：**Phase 0.5 已验证（含明确 WARNING）**
> 验证日期：2026-07-30

## 1. 版本范围

- 项目目标：Python 3.10+
- 实际测试解释器：Python 3.12.6
- 实际 pip：pip 26.0（Python 3.12）
- 本阶段不承诺尚未实际运行的其他 Python 小版本兼容性。

本机 PATH 同时暴露了 Python 3.12 和 Miniconda Python 3.13。提权后的登录
Shell 曾解析到另一解释器，因此新环境必须先执行：

```powershell
python -c "import sys; print(sys.executable); print(sys.version)"
```

确认解释器后再创建项目专用 `.venv`，不要依赖全局 PATH 顺序。

## 2. 环境安装

PowerShell 推荐命令：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
```

可选能力命令：

```powershell
# LlamaIndex 双引擎
python -m pip install -r requirements-llamaindex.txt

# LoRA 数据、训练与评估工具
python -m pip install -r requirements-finetune.txt
```

`requirements-dev.txt` 已包含默认运行依赖，无需先重复安装
`requirements.txt`。当前 Python 3.12 环境执行
`pip install --dry-run --no-index -r requirements-dev.txt` 显示全部依赖已满足。

### 依赖分类与理由

| 清单 | 直接依赖 | 源码依据 |
|---|---|---|
| 默认运行 | LangGraph/checkpoint、LangChain core/openai/community/text-splitters、sentence-transformers、FAISS、PyMuPDF、Streamlit、dotenv、Pydantic | Graph、Tool、LangChain RAG、BGE、本地 UI 与配置的真实 import |
| 开发测试 | pytest | `tests/` 的测试框架 |
| LlamaIndex 可选 | `llama-index-core`、`llama-index-vector-stores-faiss` | `rag/llamaindex/` 与可选 Embedding adapter |
| LoRA 训练 | OpenAI SDK、PyTorch、Transformers、PEFT、Datasets | `finetune/dataset/` 与 `finetune/scripts/` 的真实 import |

原清单中未被源码直接使用的 `langchain` 聚合包、
`langgraph-checkpoint-sqlite`、`langchain-huggingface`、`huggingface-hub`
和 `tiktoken` 不再作为顶层直接依赖；需要它们的上游库会自行声明传递依赖。

## 3. 测试基线

命令：

```powershell
python -m pytest -q
```

验收要求：

- 不要求 `DEEPSEEK_API_KEY`；
- 不访问网络；
- 不下载或加载完整 BGE 模型；
- 不读取、写入或重建真实 FAISS 索引；
- 测试由 pytest 正常收集，不依赖模块导入期断言。

实际结果：

```text
22 passed in 11.33s
22 tests collected in 10.56s
```

`pytest.ini` 将收集范围限制为根目录 `tests/`，避免误收集仓库内忽略的
`LLaMA-Factory` 第三方测试；同时禁用 pytest 缓存插件，因为当前受控工作区
在缓存目录的原子临时目录创建阶段会持续阻塞。该设置没有跳过项目测试。

## 4. Streamlit 启动

命令：

```powershell
python -m streamlit run app.py
```

已验证 CLI 可用，版本为 Streamlit 1.59.2。未执行完整页面启动：现有
`init_services()` 会无条件加载完整 BGE，并根据索引状态执行加载/构建，
这超出本阶段的离线最小验证边界。该阻塞是具体的生命周期问题，不代表
Streamlit 链路已经通过。此入口也不代表未来 Python HTTP 服务入口。

## 5. 环境变量

| 变量 | 是否必需 | 当前用途 |
|---|---:|---|
| `DEEPSEEK_API_KEY` | 在线聊天必需 | DeepSeek-compatible `ChatOpenAI` 鉴权 |
| `RAG_ENGINE` | 否 | 选择 `langchain` 或 `llamaindex`；现有 bootstrap 也会设置它 |
| `LOG_LEVEL` | 否 | 控制日志级别 |

密钥只能由环境变量或未跟踪的本地配置提供，不得写入源码或文档示例值。
仓库提供不含密钥的 `.env.example`。

## 6. 无真实 API Key 的预期

- 离线 pytest 在无 Key、无网络调用的设计下通过。
- 测试通过注入 `RecordingLLM`，不会创建默认 DeepSeek Client。
- 禁止 dotenv 加载并清除环境变量后，`_create_default_llm()` 在客户端构造阶段
  抛出 `openai.OpenAIError: Missing credentials`，尚未发起 HTTP 请求。
- 完整 Streamlit 初始化还会先加载 BGE，因此未重复执行该重型路径。

## 7. FAISS 最小验证

本阶段对仓库内受信任索引做了只读检查：

1. 确认索引所需文件存在；
2. 使用 FAISS 原生只读加载验证向量索引可反序列化；
3. 记录维度和向量数量；
4. 不调用知识库 rebuild/delete，不加载 BGE，不触碰未知来源的 pickle。

实际结果：

```text
data/vectorstore/index.faiss: d=768, ntotal=23
data/vectorstore/llamaindex/faiss.index: d=768, ntotal=19
```

验证只调用 `faiss.read_index()`，没有读取 `index.pkl`、加载 BGE 或修改索引。

## 8. 验证记录

| 项目 | 命令 | 状态 | 证据或阻塞原因 |
|---|---|---|---|
| Python 版本 | `python --version` | PASS | 3.12.6 |
| 语法编译 | `python -m compileall -q ...` | PASS | 退出码 0 |
| pytest 收集 | `python -m pytest --collect-only -q` | PASS | 收集 22 项 |
| pytest 执行 | `python -m pytest -q` | PASS | 22 passed |
| 默认依赖解析 | `pip install --dry-run --no-index -r requirements-dev.txt` | PASS | 当前 3.12 环境全部满足 |
| LlamaIndex 可选导入 | 导入 core 与 FAISS adapter | PASS | 未加载模型或索引 |
| LoRA 顶层依赖导入 | 导入 OpenAI/Torch/Transformers/PEFT/Datasets | PASS | 顶层包均可导入；未加载模型 |
| Streamlit CLI | `python -m streamlit --version` | PASS | 1.59.2 |
| Streamlit 完整启动 | `python -m streamlit run app.py` | WARNING | 会加载完整 BGE/真实索引，本阶段未执行 |
| 无 Key 行为 | 隔离 dotenv 后构造默认 LLM | PASS | 客户端构造阶段明确报 Missing credentials |
| FAISS 只读加载 | `faiss.read_index(...)` | PASS | 两个索引均为 768 维，分别 23/19 条 |
| 全局环境一致性 | `python -m pip check` | WARNING | 现有 LLaMA-Factory/TRL 与 Transformers、Numba 与 NumPy 有版本冲突 |

`pip check` 的冲突来自本机已有的可选训练环境，不影响本阶段 22 个离线测试。
本阶段不通过大规模升级解决它；LoRA 环境应后续使用单独虚拟环境复现。
