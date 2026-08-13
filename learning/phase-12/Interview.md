# Phase 12 Interview

1. **为什么三套单元测试通过不等于 E2E？** 它们可能绕过真实 HTTP、序列化、数据库、Redis、代理和浏览器状态。
2. **为什么最终数据库结论必须用 MySQL 8？** H2 compatibility mode 不能完整模拟 MySQL DDL、索引、约束和驱动行为。
3. **确定性 Python 替身证明什么？** 证明 Java/Python HTTP 契约、错误映射和 UI 行为；不证明真实 LLM/RAG 质量。
4. **为什么 MySQL/Redis 不全部用 mock？** schema migration、唯一约束、事务和 TTL 是基础设施行为，mock 会隐藏差异。
5. **如何防止 E2E 误伤本地数据？** 临时容器、随机凭据、隔离目录、固定标签和基于 state 文件的精确清理。
6. **幂等如何跨层验收？** 同 key 重放后响应一致，并同时断言 request、message 数量及 Redis 状态和 TTL。
7. **timeout 为什么断言 UNKNOWN？** Java 已发出请求但不知道下游最终结果，标记 UNKNOWN 比重试或伪造失败更准确。
8. **浏览器测试不可替代什么？** 数据库约束、Redis TTL、内部 Envelope 和异常状态落库需要服务集成测试断言。
9. **`pip check` 与漏洞扫描有什么区别？** 前者检查已安装依赖是否满足版本要求，不判断已知 CVE。
10. **最终 PASS with WARNING 是否矛盾？** 不矛盾；范围内验收通过，同时透明披露在线依赖和生产化限制。
