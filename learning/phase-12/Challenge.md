# Phase 12 Challenge

1. 增加独立的在线 DeepSeek smoke profile：只有显式提供测试 Key 才运行，并确保日志不泄密。
2. 给 E2E 增加非法 Python Envelope 和 TraceId 不匹配，断言 Java 返回稳定错误且不污染消息表。
3. 为“已有 Phase 7 schema 升级到最新”建立独立 MySQL fixture，与空库 migration 分开报告。
4. 将 Chrome 主链固化为可选 Playwright 测试，同时保留服务集成测试作为快速回归层。
5. 引入适合 Maven/Python 的依赖漏洞扫描，并把“扫描器不可用”和“发现漏洞”区分为不同结论。
