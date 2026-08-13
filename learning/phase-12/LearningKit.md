# Phase 12 Learning Kit

## 一句话讲清设计

用“全量回归 + 真实基础设施集成 + 少量真实浏览器主链 + 精确架构审计”形成可追溯证据，同时明确
确定性 AI 替身与在线 DeepSeek 之间的证明边界。

## 最值得学习的核心文件

### `Phase12PlatformE2ETests`

- 位置：Java 最终平台集成层。
- 输入/输出：公共 HTTP 请求 → Java/Python/MySQL/Redis 的可观察结果。
- 依赖：Spring Boot、Testcontainers MySQL/Redis、独立 FastAPI 进程。
- 面试点：为什么既断言 API，又直接断言数据状态和 TTL。
- 小修改：增加非法 Envelope，验证协议错误不写 assistant message。

### `tests/support/phase12_e2e_api.py`

- 位置：仅测试使用的 Python 确定性运行时。
- 输入/输出：真实内部路由与 Envelope → 可预测正常、503、timeout 和文档结果。
- 依赖：生产 router/facade，以及 graph/knowledge test double。
- 面试点：测试替身放在哪个边界最能减少假阳性。
- 小修改：增加 TraceId 不匹配场景，但不要进入生产依赖注入。

### `Start-Phase12E2E.ps1` / `Stop-Phase12E2E.ps1`

- 位置：本地最终联调生命周期入口。
- 输入/输出：显式 Python/Node 可执行文件 → 五组件环境与精确清理。
- 依赖：已打包 JAR、Vite、Docker。
- 面试点：为什么随机凭据、readiness、state 文件和资源归属检查同样重要。
- 小修改：允许端口参数化并继续保持默认拒绝占用端口。

### `requirements-windows-cpu.txt`

- 位置：Windows CPU 测试依赖入口。
- 输入/输出：默认开发依赖 → 已验证的 PyTorch CPU runtime。
- 依赖：PyPI 与 PyTorch 官方 CPU wheel 索引。
- 面试点：版本范围、平台 wheel、`pip check` 与 CVE 扫描的差异。
- 小修改：在 CI 验证 resolver，不提交虚拟环境。

### Vue Browser E2E

- 位置：真实 Chrome 中的用户可见验收。
- 输入/输出：点击、输入和上传 → 页面内容、错误提示和导航结果。
- 依赖：Vue、Java、Python、MySQL、Redis 全部就绪。
- 面试点：为什么浏览器断言“一条用户消息”能验证 UNKNOWN 不重发，但不能代替数据库幂等断言。

## 推荐讲解顺序

1. 先说每种测试能证明什么、不能证明什么。
2. 展示 Java E2E 如何跨 HTTP、数据库、缓存和 Python 同时断言。
3. 用 503/timeout 对比 FAILED 与 UNKNOWN，并解释零自动重试。
4. 解释临时容器、随机凭据、隔离目录和精确清理如何保护本地数据。
5. 最后展示四维审计和 WARNING，强调“无证据不宣称”。

## 验收结果

四维审计为 PASS with WARNING，无未解决 ERROR。完整数据见 [TestMatrix](./TestMatrix.md)，调用关系见
[CallGraph](./CallGraph.md)，限制见 [Audit](./Audit.md)。Phase 12 不会自动进入后续阶段。
