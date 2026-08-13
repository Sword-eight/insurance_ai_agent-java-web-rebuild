# Phase 9 Learning Kit

## 一句话讲清设计

注册把规范化用户名和 BCrypt 摘要写入 MySQL；登录校验密码后签发短期 HS256 JWT；过滤器只建立可信身份，业务 Service 仍用该身份校验资源归属。

## 面试讲解顺序

1. 密码：解释为什么使用带盐、可调成本的 BCrypt，而不是明文、可逆加密或普通 SHA-256。
2. 注册并发：应用查重改善错误体验，数据库唯一约束才是并发竞争的最终裁决者。
3. Token：JWT 是签名凭证而非加密数据；固定算法并校验 issuer、iat、exp 与 UUID subject。
4. 分层：Filter 建立 `SecurityContext`，`CurrentUserProvider` 向业务层只暴露 UUID，Controller/Service 不解析 Header。
5. 授权：认证只证明“是谁”，会话 Service 仍要证明“是否拥有该资源”。

## 代码阅读地图

- `AuthController`：观察匿名 HTTP 契约、201/200 状态与公共 Envelope。
- `AuthService`：观察规范化、BCrypt、dummy hash、用户状态和唯一冲突映射。
- `JwtConfig`：观察密钥 fail-fast、固定 HS256 和 issuer/time validator。
- `NimbusJwtTokenService`：观察最小 Claim 集、签发与 UUID subject 验证。
- `JwtAuthenticationFilter`：观察 Bearer 格式拒绝、`SecurityContext` 建立和统一 401。
- `SecurityContextCurrentUserProvider`：观察业务层如何避免依赖 Spring Security API。
- `Phase9AuthenticationIntegrationTests`：观察完整 HTTP 正常/异常流程和资源归属。
- `Phase9MySqlAuthenticationIntegrationTests`：观察真实 MySQL 的 BCrypt 与并发唯一键证据。

## 高频问题与参考答案

### 为什么不能用 SHA-256 直接保存密码？

SHA-256 为通用高速摘要设计，攻击者可低成本批量猜测。BCrypt 自动加盐并有可调计算成本，更适合减慢离线暴力破解；数据库也永远不需要恢复原密码。

### 为什么先查用户名仍然需要唯一约束？

两个并发事务可以同时看到“不存在”并一起插入。先查只能改善普通请求体验，唯一约束才在数据库原子写入点裁决竞争；应用捕获冲突并转换为稳定业务错误。

### JWT 签名有效是否意味着用户能访问任意会话？

不能。签名只证明凭证由可信服务签发且未被篡改；资源授权仍需按 `userId + conversationId` 查询事实来源。否则知道别人的 ID 就可能形成越权访问。

### 为什么缺少 JWT 密钥必须启动失败？

自动生成或回退到默认弱密钥会导致重启后 Token 全失效、集群节点不一致，甚至允许攻击者猜出密钥。生产密钥必须显式、足够强并由部署环境管理。

### JWT 无状态的代价是什么？

服务端无需保存每个会话，但已签发 Token 无法天然即时撤销。当前短 TTL 降低窗口，禁用用户在进入现有受保护业务时还会因数据库查询被拒绝；若未来要求即时全局撤销，需要单独评审黑名单、版本号或密钥轮换方案。

## 自测题

1. 为什么密码字符数未超过 72，UTF-8 字节数仍可能超过 BCrypt 边界？
2. dummy BCrypt 匹配缓解了哪类用户名枚举信号，它为什么不能替代登录限流？
3. 如果允许客户端选择 JWT 算法，可能产生什么算法混淆风险？
4. 为什么 `SecurityContext` 中只建立身份，资源所有权仍留给业务 Service？
5. 对称 HMAC 密钥轮换时，如何在不扩大当前范围的前提下描述未来兼容方案？

## 动手练习

- 构造一个签名正确但 issuer 错误的 Token，确认入口统一返回 401。
- 用两个大小写不同的同名请求并发注册，确认数据库最终只有一条规范化用户名。
- 将用户状态改为 `DISABLED`，比较登录与已有 Token 访问业务资源的拒绝位置。
- 给密码加入多字节字符，分别验证字符长度和 UTF-8 72-byte 边界。

## 验收命令

```text
mvn test
mvn -DskipTests package
```

完整测试需要可用的 Docker Engine，以启动一次性 MySQL 8.4 与 Redis 容器；不得把测试连接指向开发库或生产库。依赖缓存位于仓库外，不提交本机绝对路径、密钥或容器数据。
