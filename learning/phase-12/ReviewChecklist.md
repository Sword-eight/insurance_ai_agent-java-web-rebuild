# Phase 12 Review Checklist

- [x] Python、Java、Vue 全量测试真实执行并通过，Java/Vue 构建成功。
- [x] Java E2E 使用真实 MySQL 8.4、Redis 7.4 和独立 Python HTTP 进程。
- [x] Chrome 验证登录、会话、聊天、503、timeout、PDF 和未登录路由保护。
- [x] 幂等重放同时断言响应、MySQL 行数、Redis 状态和 TTL。
- [x] timeout 保持 UNKNOWN，浏览器不自动重发或重复展示用户消息。
- [x] Vue 不直连 Python；Controller 不直调 Client/Mapper；Python 不访问 Java 业务库。
- [x] E2E 使用临时容器、隔离文档目录、随机凭据和精确清理。
- [x] Python/Node 路径通过参数传入；Phase 12 文件无本机绝对路径。
- [x] Windows CPU 隔离环境可导入 Torch/Transformers，`pip check` 通过。
- [x] npm audit 为 0；Maven dependency tree 可生成。
- [x] 未提交密钥、`.env`、日志、模型、索引、checkpoint、target 或 dist。
- [x] 四维审计为 PASS with WARNING，无未解决 ERROR。
- [ ] 真实 DeepSeek/BGE 在线验收：未提供 Key，按证据边界保留 WARNING。
