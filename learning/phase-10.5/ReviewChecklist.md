# Phase 10.5 Review Checklist

## 功能

- [x] `/documents` 是受保护路由，并可从聊天页进入。
- [x] 支持单 PDF 选择、20 MiB 快速校验、multipart 上传和真实进度。
- [x] pending 时禁止机械重复提交。
- [x] 文档列表展示文件名、大小、时间和 Java 索引状态。
- [x] UNKNOWN 与 FAILED 分开，失败后不自动重新上传。

## 架构与设计

- [x] 所有请求只访问 Java 公共 `/documents`。
- [x] 未修改 Java、Python、MySQL 或冻结文档。
- [x] 未引入新依赖、状态框架或 UI 框架。
- [x] 文档 305 秒超时是请求级配置，聊天 65 秒保持不变。
- [x] JWT、401 和公共 Envelope 复用既有基础设施。

## 验证

- [x] TypeScript typecheck 通过。
- [x] 8 个 Vitest 文件、20 条测试通过，0 skipped。
- [x] Vite production build 通过。
- [x] npm high-level audit 为 0 vulnerabilities。
- [x] `/documents` Vite HTTP smoke 返回 200 和 Vue 挂载点。
- [ ] 真实浏览器点击和截图：环境没有可用浏览器实例，已记录 WARNING。
- [ ] 新的 Vue→Java→Python 在线索引端到端：留给 Phase 11/12 联调，未伪造。
