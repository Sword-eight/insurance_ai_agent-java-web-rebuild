# Phase 10.5 Test Matrix

| 边界 | 正常流程 | 异常流程 |
|---|---|---|
| TypeScript 契约 | `DocumentData` 与分页 Envelope | 非 `OK`/空 data 继续由 `unwrap` 拒绝 |
| document API | GET 列表；FormData 上传；真实进度 | 总量未知返回 loading；请求级超时不污染全局 |
| 文件选择 | 单个非空 PDF，20 MiB 以内 | 超大文件在调用 API 前拒绝 |
| 页面状态 | INDEXED 与列表展示 | UNKNOWN 与 FAILED 分开显示 |
| 提交生命周期 | pending 禁用；成功后 GET 刷新 | 504 只提示并 GET 刷新，不自动重新上传 |
| 路由 | 受保护 `/documents` 与聊天入口 | 401 复用共享登录态清理 |
| 回归 | 原认证、会话、聊天测试继续通过 | 0 个无法解释的失败或跳过 |
| 运行产物 | Vite production build | npm high-level audit 无漏洞 |

最终数量和未执行项以 [Audit](./Audit.md) 的实际命令证据为准。
