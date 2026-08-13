# Phase 12 Test Matrix

> 最终执行日期：2026-08-13

| 层级 | 真实边界 | 实际结果 |
|---|---|---|
| Python Phase 12 契约 | FastAPI router/facade、Envelope、TraceId、PDF 校验；AI 为替身 | 2 passed |
| Python 全量回归（隔离 venv） | 应用与既有测试；无在线 DeepSeek | 77 passed，2 warnings |
| Java Phase 12 E2E | Spring HTTP + MySQL 8.4 + Redis 7.4 + Python HTTP | 1 passed |
| Java 全量构建 | 单元、H2、真实 MySQL/Testcontainers、打包 | 90 tests，BUILD SUCCESS |
| Vue 全量 | 组件/API 测试、TypeScript、Vite build | 8 files / 26 tests，build PASS |
| Chrome E2E | Vue + Java + MySQL/Redis + deterministic Python | 正常聊天、503、504、PDF、路由保护 PASS |
| Python 依赖 | Windows CPU 隔离 venv | import PASS，`pip check` PASS |
| npm audit | 已安装前端依赖 | 0 vulnerabilities |
| E2E 生命周期 | 显式 Python/Node 参数 + 临时容器 | 三个端点 200；停止后无进程/容器残留 |

## 关键命令

```powershell
$env:PYTHONUTF8 = '1'
python -m pip install --timeout 180 --retries 8 -r requirements-windows-cpu.txt
python -m pip check
python -m pytest -q

mvn -Dmaven.repo.local=<cache> -Dphase12.python.executable=<python> clean package

Set-Location web-client
npm test
npm run build

.\scripts\phase12\Start-Phase12E2E.ps1 `
  -PythonExecutable <python.exe> `
  -NodeExecutable <node.exe>
.\scripts\phase12\Stop-Phase12E2E.ps1
```

`<cache>`、`<python.exe>` 和 `<node.exe>` 必须替换为当前机器的路径；这些路径不写入仓库。

## 浏览器断言

- 未登录访问 `/documents` 跳转至 `/login?redirect=/documents`。
- 注册、登录、创建会话和正常聊天成功；页面只出现一条用户消息。
- Python 503 显示“AI 服务暂时不可用，请稍后再试。”，不重复消息。
- Python timeout 显示“结果暂时无法确认，请勿自动重发。”，不重复消息。
- `phase12-terms.pdf` 上传成功，文件名只出现一次，状态显示“可检索”。

## 未执行项

本机没有为本阶段提供真实 `DEEPSEEK_API_KEY`，因此未执行在线 DeepSeek、真实 BGE 下载/推理和
生产向量索引验收。这是明确 WARNING，不影响本阶段对平台 HTTP、数据和 UI 契约的结论。
