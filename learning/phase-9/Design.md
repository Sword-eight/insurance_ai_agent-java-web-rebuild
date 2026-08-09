# Phase 9 Design：用户、登录与 JWT

> 状态：实现完成，等待用户验收；本文记录最终落地契约。

## 1. 冻结边界对齐

```text
Register/Login HTTP -> AuthController -> AuthService -> UserMapper -> MySQL
Bearer Header -> JWT Filter -> JwtTokenService -> SecurityContext
SecurityContext -> CurrentUserProvider -> existing protected Service
```

- Java 负责用户、密码散列、JWT 与资源归属；Python 不接触认证。
- Controller 不访问 Mapper；业务 Service 不解析 HTTP Header。
- `iap_user`、公共 Envelope、冻结错误码和 `/api/v1/auth/*` 路径保持 Phase 2 契约。
- MySQL 是用户事实来源；JWT、密码和密钥不写入 Redis。

## 2. 公共接口

### 注册

`POST /api/v1/auth/register`，成功返回 HTTP 201：

```json
{
  "success": true,
  "data": {
    "userId": "00000000-0000-0000-0000-000000000000",
    "username": "candidate_01",
    "createdAt": "2026-08-09T00:00:00Z"
  },
  "error": null,
  "traceId": "..."
}
```

### 登录

`POST /api/v1/auth/login`，成功返回 HTTP 200：

```json
{
  "success": true,
  "data": {
    "accessToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresInSeconds": 1800,
    "user": {
      "userId": "00000000-0000-0000-0000-000000000000",
      "username": "candidate_01",
      "createdAt": "2026-08-09T00:00:00Z"
    }
  },
  "error": null,
  "traceId": "..."
}
```

## 3. 输入、密码与并发注册

- 用户名先 `trim`，再以 `Locale.ROOT` 转小写；仅允许 3～64 个 ASCII 字母、数字、点、下划线或连字符。
- 密码不 `trim`；长度 8～72 个字符，且 UTF-8 编码不得超过 BCrypt 的 72-byte 输入边界。
- 数据库只保存 BCrypt 摘要。用户不存在时仍执行一次 dummy BCrypt 匹配，降低明显的用户名枚举时序差异。
- 注册先查重以提供稳定响应，同时依赖数据库唯一约束裁决并发竞争；两条路径均映射为 400 `VALIDATION_ERROR` 和安全消息 `username is unavailable`。
- 用户不存在、密码错误、用户禁用或逻辑删除统一返回 401 `AUTH_UNAUTHORIZED`。

## 4. JWT 与受保护资源

- JWT v1 固定为 HMAC-SHA256；Claim 仅含 issuer、`sub=userId`、issued-at 与 expiry。
- 默认 TTL 30 分钟；验证签名、issuer、时间和 UUID subject，时间校验允许 30 秒时钟偏差。
- Base64 密钥只从 `JWT_SECRET_BASE64` 注入，解码后至少 32 bytes；缺失、格式错误或过短均启动失败。
- 注册/登录和 OpenAPI 匿名可访问，其余请求默认需要 Bearer Token；会话策略为 `STATELESS`。
- Filter 只把验证后的 UUID 身份写入 `SecurityContext`；现有 `CurrentUserProvider` 将其提供给业务层，资源归属校验继续由现有 Service/Mapper 完成。
- 不实现 refresh token、token 黑名单、角色、logout、HTTP Session 或 Redis JWT 存储。

## 5. 敏感信息边界

- DTO/VO 的字符串表示会隐藏密码或 Token。
- 客户端只收到统一错误 Envelope，不收到 JWT 解析细节、SQL、堆栈或密码状态差异。
- 测试密钥与数据库密码运行时随机生成，不向仓库提交真实凭据或 Token。
