# Phase 0.5 Learning Kit

本阶段把旧项目从“导入文件就运行断言、可能加载模型和修改索引”稳定为可离线重复执行的 Python 基线。

## 学习顺序

1. `MiniCourse.md`：pytest、Mock/DI、依赖拆分、运行基线。
2. `Design.md`：本阶段接口和测试矩阵。
3. `CallGraph.md`：测试覆盖的真实调用关系。
4. `KnowledgeSummary.md`：实现后的关键结论。
5. `Interview.md`：本阶段面试问答。
6. `Challenge.md`：手写、修改、调试、画图和口述练习。
7. `ReviewChecklist.md`：验收证据与四维审计。

## 最小复现

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
python -m pytest -q
```

预期：22 个离线测试通过；不需要 DeepSeek Key，不加载 BGE，不读写真实 FAISS 索引。

运行前应先用 `python -c "import sys; print(sys.executable)"` 确认当前解释器，避免本机多 Python PATH 漂移。
