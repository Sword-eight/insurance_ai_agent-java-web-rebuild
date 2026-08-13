# Phase 12 MiniCourse：最终联调、测试证据与架构审计

> 状态：开发前必修
> 目标：用分层证据证明 Vue、Java、Python、MySQL、Redis 的真实协作，同时明确在线模型和测试替身的边界。

## 1. 测试金字塔与端到端测试职责

单元测试定位局部规则，集成测试验证真实基础设施，端到端测试证明用户可见主链能协作。Phase 12
不能只把三套单元测试相加后称为 E2E，也不能让浏览器测试承担所有边界组合。

```text
少量 Browser E2E：注册/登录/会话/聊天/文档页面
若干服务集成：Java↔Python、Java↔MySQL/Redis、内部 Envelope
大量单元/组件：状态机、异常映射、Vue 交互、Graph/Tool/RAG
```

## 2. Test Double、真实基础设施与在线依赖

测试替身适合制造确定性异常，例如 Python 500、读取超时、熔断打开；MySQL 8 和 Redis 则必须用
真实临时容器验证 schema、TTL、唯一约束和降级。DeepSeek 在线验证只能在本地 Key 明确配置时执行，
否则必须标为未执行，不能用 fake LLM 冒充在线模型成功。

关键是为每条证据标注边界：

- fake Python：验证 Java 公共链和异常状态，不证明真实 Graph/DeepSeek；
- fake LLM/Embedding：验证 FastAPI/Graph/RAG 协议，不证明外部模型和 BGE 下载；
- Testcontainers：证明真实 MySQL/Redis 行为，但不等于生产环境容量验证；
- Browser：证明真实页面交互，但不能代替数据库约束测试。

## 3. 可重复的隔离环境与数据安全

最终验收应使用临时 MySQL/Redis 容器、随机测试用户、受控小 PDF 和测试专用文档目录。启动前解析
容器、端口和目录，停止后只清理本轮确定创建的资源。禁止连接未知的本机数据库、复用生产 Key、
清空宽泛目录或把 `.env`、日志、索引和测试凭据提交到 Git。

可重复性要求包括固定依赖锁、明确启动顺序、health/readiness 等待和可追溯命令；“我电脑上已启动”
不能作为验收步骤。

## 4. 契约测试与跨服务断言

跨服务测试要同时验证 HTTP 状态和稳定 Envelope，而非只断言 `200`。Java↔Python 必须核对：

- `X-Trace-Id` 请求、响应头和 Envelope 一致；
- requestId/sessionId/history 与错误码符合冻结契约；
- timeout、5xx、非法 Envelope 不泄露内部信息；
- 重复 Idempotency-Key 不重复写消息或执行下游；
- 文档 multipart、SHA-256、状态迁移和零自动重试。

契约测试负责发现两端独立演进产生的漂移，不能靠共享同一个 DTO 类让错误在编译时被掩盖。

## 5. 四维审计与“无证据不宣称”

最终结论分为功能、架构、设计、生命周期四维，每一维只能引用实际命令、测试报告、浏览器交互或
源码差异。结论规则：

- `PASS`：范围内证据完整且没有已知阻断；
- `WARNING`：能力可用，但存在已披露的环境、在线依赖或生产化限制；
- `ERROR`：冻结契约被破坏、关键链路失败、数据风险或测试不能解释，必须停止。

最终审计还要核对调用方向、数据所有权、敏感文件、依赖漏洞与未提交改动，不能因为功能演示成功就
忽略架构漂移和生命周期问题。

## 快速自测

1. 为什么 Vue 组件测试、Java MockMvc 和 Python pytest 全通过仍不能直接宣称三端 E2E 通过？
2. fake LLM、真实 MySQL 容器和浏览器测试分别能证明什么、不能证明什么？
3. 如何确保最终验收不会误连开发库或删除用户数据？
4. Java↔Python 契约测试为什么必须独立断言 TraceId、Envelope 和错误码？
5. 在线 DeepSeek 因缺少 Key 未执行时，最终审计应如何表述？
