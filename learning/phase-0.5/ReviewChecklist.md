# Phase 0.5 Review Checklist

## 功能审计：PASS with WARNING

- [x] `python -m compileall -q ...` 退出码 0。
- [x] pytest 收集 22 项，未收集第三方 LLaMA-Factory。
- [x] `python -m pytest -q`：22 passed in 11.33s。
- [x] PremiumService 正常、边界和异常参数均有真实断言。
- [x] Premium Tool 验证注入身份、参数委托、格式化和异常路径。
- [x] Router 三分支、消息截断与最小 Graph 循环通过。
- [x] 测试未创建默认 LLM、未加载 BGE、未读写真实索引。
- [x] 两个受信任 FAISS 文件只读加载成功。
- [x] LlamaIndex core/FAISS adapter 与五个 LoRA 顶层依赖完成无模型导入冒烟检查。
- [ ] 完整 Streamlit 页面未启动：它会无条件加载完整 BGE 和真实索引，记为 WARNING。

## 架构审计：PASS with WARNING

- [x] 未引入 FastAPI、Java、数据库、Redis、JWT 或新服务边界。
- [x] 未移动或重命名 `graph/`、`tools/`、`services/`、`rag/`。
- [x] Tool → Service 依赖方向保持，伪 DI 已修正。
- [x] Controller/Router 等未来架构未提前实现。
- [ ] `docs/ARCHITECTURE.md` 尚未冻结，无法执行对唯一事实源的最终漂移检查；这是 Phase 1 预期工作。

## 设计审计：PASS

- [x] 业务实现只改 Premium Tool 构造注入。
- [x] 测试替身只替换 LLM、Tool 外部协作和 Retriever 数据源。
- [x] 未增加通用 Mock 框架或无收益抽象。
- [x] 未出现 `assert True`、仅打印结果或导入期断言。
- [x] 依赖按真实用途拆分，未使用 `pip freeze` 堆入间接包。

## 生命周期审计：PASS with WARNING

- [x] PremiumService 只由 bootstrap 创建一次并注入 Tool。
- [x] Graph 测试注入 RecordingLLM，不创建 DeepSeek Client。
- [x] FAISS 验证只读，不触发 EmbeddingManager。
- [ ] Streamlit rerun 重建 Graph/checkpointer 仍存在，已列入 Phase 4 技术债。
- [ ] `InMemorySaver` 仍不是生产持久化方案，已列入 Phase 8 技术债。
- [ ] 本机双 Python PATH 与可选训练环境依赖冲突已记录为 WARNING。

## 总结

**Phase 0.5：PASS with WARNING，无 ERROR。**

WARNING 都有具体证据和后续归属，不影响本阶段进入用户 Review，但不得据此宣称完整在线链路或 LoRA 训练环境已经验收。
