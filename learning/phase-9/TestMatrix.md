# Phase 9 Test Matrix

> 状态：已实现并实际执行。最终全量结果：65 tests，0 failures，0 errors，0 skipped。

| 层级 | 场景 | 实际结论 |
|---|---|---|
| DTO/Service | 用户名为空、越界、非法字符 | PASS：400 `VALIDATION_ERROR`，不写库 |
| DTO/Service | 密码为空、字符越界或 UTF-8 超过 72 bytes | PASS：400 `VALIDATION_ERROR`，不写库 |
| Service/H2 | 首次注册 | PASS：ACTIVE 用户落库；只保存 BCrypt 摘要 |
| Service/H2 | 大小写不同的重复用户名 | PASS：400 `VALIDATION_ERROR` |
| Real MySQL 8.4 | 4 线程并发注册规范化后的同一用户名 | PASS：仅一条成功，唯一约束裁决竞争 |
| Service | 正确登录 | PASS：BCrypt 匹配后签发当前用户 JWT |
| Service | 用户不存在、密码错误、DISABLED、逻辑删除 | PASS：统一 401 `AUTH_UNAUTHORIZED` |
| JWT | 合法 Token | PASS：HS256、issuer、subject、iat、exp 均通过并得到 UUID principal |
| JWT | 缺失、Bearer 格式错、篡改、issuer 错、过期、subject 非 UUID | PASS：统一 401 Envelope，无解析细节 |
| JWT 配置 | 密钥缺失、Base64 非法、少于 32 bytes、issuer 空、TTL 非正 | PASS：配置构造失败，不生成弱密钥 |
| Security | 注册与登录 | PASS：匿名允许，STATELESS，不创建 Session |
| Security | 其他资源无 Token | PASS：401，Controller/Service 不执行 |
| Security | 合法 Token 访问本人会话 | PASS：身份经 `CurrentUserProvider` 进入现有 Service |
| Authorization | 用户 A Token 访问用户 B 会话 | PASS：403 `CONVERSATION_ACCESS_DENIED` |
| Regression | Chat/Conversation/Message/Redis/Python Client | PASS：完整回归继续通过，TraceIdFilter 保持生效 |
| Real MySQL 8.4 | migration、BCrypt、登录、禁用状态 | PASS：与 H2 快速回归结论一致 |
| Packaging | Spring Boot 可执行 JAR | PASS：`mvn -DskipTests package` 成功 |

## 实际执行记录

- 定向 Phase 9 H2/JWT 测试：6/6 通过。
- 安全过滤器回归修正后，既有受影响用例：23/23 通过。
- MySQL 8.4 Testcontainers 定向测试：2/2 通过；使用 tmpfs 隔离数据目录并将启动预算设为 5 分钟，稳定启动约 13 秒。
- 最终 Maven 全量回归：65/65 通过，包含 H2、MySQL 8.4 与 Redis 容器测试。
- 最终 Maven 打包：成功生成 Spring Boot 可执行 JAR。

## 环境说明

- MySQL 测试使用一次性 `mysql:8.4` 容器与隔离空库，不连接开发库或生产库。
- 容器密码和 JWT 测试密钥均运行时生成。
- H2 只承担快速回归，不能代替真实 MySQL 结论。
