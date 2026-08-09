# Phase 9 四维审计

> 审计对象：用户注册、登录、JWT 签发/验证、安全过滤器链及现有资源归属接入。

## 功能：PASS

- 注册、用户名规范化、BCrypt 持久化、登录和 JWT 签发均通过 H2 与真实 MySQL 8.4 验证。
- 无效输入、重复/并发注册、错误密码、禁用/删除用户和各种无效 Token 均有异常路径测试。
- 匿名端点、无 Session、安全错误 Envelope、本人资源及跨用户资源均通过集成测试。

## 架构：PASS

- 保持 `Controller -> Service -> Mapper`，Controller 未直接访问 Mapper、Python Client 或数据库。
- JWT 在 Filter/Security Service 内处理，业务 Service 不解析 Servlet Header；`CurrentUserProvider` 隔离框架身份与业务 UUID。
- MySQL 仍是用户及资源归属事实来源；Redis 与 Python 语义未改变。
- 未引入 MQ、Worker、异步任务、角色系统、完整 DDD 或新的分布式组件。

## 设计：PASS

- BCrypt 单向散列；72-byte 边界显式校验，密码不被 trim。
- JWT 固定 HS256，校验 issuer、时间和 UUID subject；密钥必须由环境注入且至少 256 bit。
- 无用户与错误密码使用同一路径和相同公共错误；重复注册不泄露约束细节。
- 应用预检查提升可读性，MySQL 唯一约束负责并发下最终一致裁决。
- 默认拒绝其余接口，认证与资源授权仍分层执行。

## 生命周期：PASS

- `PasswordEncoder`、JWT encoder/decoder、Filter 与 Provider 为 Spring 管理的无请求可变状态单例。
- 身份只存于请求级 `SecurityContext`，服务端不创建 HTTP Session，也不持久化 JWT。
- 注册事务只包围用户写入；登录为只读事务；容器测试由 Testcontainers 清理。
- 生产密钥缺失时 fail-fast，测试密钥与容器密码运行时生成。

## 实际证据

- 最终完整测试：65 tests，0 failures，0 errors，0 skipped。
- 真实数据库：MySQL 8.4 migration、BCrypt、登录、禁用用户与 4 线程唯一键竞争全部通过。
- Redis 既有容器测试与全部 Phase 3～8 回归通过。
- `mvn -DskipTests package`：BUILD SUCCESS，生成可执行 Spring Boot JAR。

## 非阻塞 WARNING

- Flyway 10.10 启动时提示其官方测试上限低于 MySQL 8.4；本阶段真实 MySQL 迁移与集成测试均通过，后续依赖升级时应重新评估。
- H2 版本也有 Flyway 支持范围提示；它仅用于快速回归，真实 MySQL 结果拥有兼容性结论。
- v1 按冻结范围不实现 refresh/logout/黑名单/角色、登录速率限制与在线密钥轮换；这些能力若进入后续范围，应重新设计威胁模型与契约，不能顺手扩展本阶段。

## 总结

总体结论：PASS。未发现 ERROR 或架构漂移；上述 WARNING 不阻塞 Phase 9 验收。
