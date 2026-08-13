# Phase 9 MiniCourse：用户认证、JWT 与资源归属

> 状态：开发前必修；Phase 9 实现已完成，可结合 Learning Kit 复盘
> 目标：能在面试中解释“密码如何保存、JWT 如何建立身份、业务资源如何继续校验归属”，并能据此审查实现。

## 1. 密码散列不是加密

密码不能明文保存，也不能使用可逆加密。注册时由 Java 使用自带随机盐的强密码散列算法生成摘要；登录时只执行 `matches(raw, hash)`，不尝试还原密码。Phase 9 采用 Spring Security 的 `BCryptPasswordEncoder`，数据库只保存 `password_hash`。

面试要点：

- 散列是单向验证；加盐让相同密码产生不同摘要，并抵抗预计算表。
- BCrypt 的计算成本用于提高暴力破解成本，参数应可演进，但不能记录原始密码。
- “数据库唯一用户名”和“应用先查询用户名”解决的问题不同；并发注册最终仍由唯一约束裁决。

## 2. JWT 是签名凭证，不是服务器会话

登录成功后，Java 签发短期 Bearer JWT。最小 Claim 只包含签发者、主题 `sub=userId`、签发时间和过期时间；服务端验证签名、签发者、时间与 Claim 格式后，才能把用户身份写入安全上下文。

面试要点：

- JWT Payload 默认只是 Base64URL 编码，不是加密，不能放密码或敏感业务数据。
- 签名证明 Token 未被篡改，但不能自动解决泄露、撤销或权限变更。
- v1 不把 JWT 放进 Redis；禁用用户仍需在受保护业务入口通过现有用户查询失败关闭。

## 3. FilterChain、SecurityContext 与 CurrentUserProvider

HTTP 鉴权应位于 Spring Security 过滤器链，而不是散落在 Controller。JWT Filter 从 `Authorization: Bearer <token>` 读取并验证凭证，成功后写入 `SecurityContext`；`CurrentUserProvider` 再把框架身份转换为业务需要的 `UUID`。

```text
Authorization Header
  -> JwtAuthenticationFilter
  -> JwtTokenService.verify
  -> SecurityContext
  -> CurrentUserProvider.requireUserId
  -> Conversation/Message/Chat Service
```

这样 Controller 不解析 JWT，Service 也不依赖 Servlet API。无 Token、格式错误、签名错误、过期或身份 Claim 非法都统一失败为 `AUTH_UNAUTHORIZED`，且不得把解析异常或 Token 内容返回给客户端。

## 4. 认证不等于授权

认证回答“你是谁”，授权回答“你能访问什么”。JWT 得到 `userId` 后，既有 Service 仍须用该身份查询 `iap_user`，并继续执行会话归属检查。不能因为 Token 合法就允许访问任意 `conversationId`。

面试要点：

- 注册、登录是匿名接口；会话、消息和聊天是受保护接口。
- 不从请求体或自定义 `X-User-Id` 接受用户身份。
- 他人资源继续返回稳定的 `CONVERSATION_ACCESS_DENIED`；不存在资源返回 `CONVERSATION_NOT_FOUND`。

## 5. 认证配置与测试边界

JWT 密钥、密码和真实 Token 只来自环境变量或测试隔离配置，不能提交到仓库。测试至少覆盖注册、登录、重复用户名、错误密码、缺失/伪造/过期 Token，以及合法 Token 下的本人资源与他人资源访问。

需要区分：

- Controller/MockMvc 测试验证 HTTP 状态、统一 Envelope 和过滤器行为；
- Service 测试验证密码匹配、用户状态和唯一冲突；
- 真实 MySQL 验收验证 `iap_user` 唯一约束和 BCrypt 摘要持久化；
- 日志与响应审计验证不泄露密码、JWT、密钥、SQL 或堆栈。

## 快速自测

1. 为什么不能用 SHA-256 直接保存用户密码？
2. 一个签名合法但已过期的 JWT 应在哪一层被拒绝？
3. 为什么 JWT 合法后仍必须做 conversation 归属校验？
4. 为什么并发注册不能只依赖“先查用户名不存在”？
5. `CurrentUserProvider` 如何降低业务层对 Spring Security 的耦合？
