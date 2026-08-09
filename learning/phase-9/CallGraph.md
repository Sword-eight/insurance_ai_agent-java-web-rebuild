# Phase 9 调用图

## 1. 注册

```text
HTTP POST /api/v1/auth/register
  -> TraceIdFilter
  -> SecurityFilterChain                     # permitAll, STATELESS
  -> AuthController.register
  -> AuthService.register                    # transaction
     -> normalize username / validate password
     -> UserMapper.findByNormalizedUsername
     -> BCryptPasswordEncoder.encode
     -> UserMapper.insertUser
        -> MySQL iap_user unique(username)    # final concurrency arbiter
  <- HTTP 201 + common Envelope + UserView
```

重复用户名的预检查或数据库唯一键冲突都转换为相同的 `VALIDATION_ERROR`，不会暴露 SQL 或约束名。

## 2. 登录与签发

```text
HTTP POST /api/v1/auth/login
  -> TraceIdFilter
  -> SecurityFilterChain                     # permitAll, STATELESS
  -> AuthController.login
  -> AuthService.login                       # read-only transaction
     -> UserMapper.findByNormalizedUsername
     -> BCryptPasswordEncoder.matches         # missing user uses dummy hash
     -> verify ACTIVE and not deleted
     -> NimbusJwtTokenService.issue
        -> JwtEncoder / HS256
  <- HTTP 200 + Bearer JWT + UserView
```

不存在、密码不符、禁用或删除统一进入 `AUTH_UNAUTHORIZED`。

## 3. 受保护资源

```text
Authorization: Bearer <jwt>
  -> TraceIdFilter
  -> JwtAuthenticationFilter
     -> NimbusJwtTokenService.verify
        -> NimbusJwtDecoder
        -> validate HS256 signature / issuer / iat / exp / UUID subject
     -> SecurityContext(userId)
  -> existing Controller
  -> existing Service
     -> SecurityContextCurrentUserProvider.requireUserId
     -> existing Mapper / MySQL ownership check
  <- existing success or frozen authorization error
```

## 4. 拒绝分支

```text
missing token
  -> SecurityFilterChain authentication entry point
  <- 401 AUTH_UNAUTHORIZED

malformed / tampered / expired / wrong issuer / invalid subject
  -> JwtAuthenticationFilter
  -> RestAuthenticationEntryPoint
  <- 401 AUTH_UNAUTHORIZED

valid token but cross-user resource
  -> existing ownership check
  <- 403 CONVERSATION_ACCESS_DENIED
```
