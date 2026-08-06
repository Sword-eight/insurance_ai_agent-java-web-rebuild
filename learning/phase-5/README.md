# Phase 5 Learning Kit：Spring Boot 公共 API 基础设施

## 阶段结论

Phase 5 已建立后续 Java 公共 Controller 可复用的基础设施：

- 冻结公共 Envelope 与 17 个公共错误码；
- Bean Validation、业务异常、媒体类型和未知异常统一映射；
- TraceId 合法值复用、非法/缺失值生成、Header/Envelope/MDC 一致及 finally 清理；
- 未知异常响应和日志都不回显异常 message、绝对路径或敏感内容；
- OpenAPI 默认文档与 `public-v1` 分组都只扫描 `/api/v1/**`；
- 使用 test-only Controller 验证 Web 行为，没有提前创建 Phase 6 业务链。

四维审计结论：**PASS with WARNING**，无 ERROR。WARNING 是本机资源争用导致单次 Python 全量命令超时；按收集清单拆分后 63 项全部通过。Java→Python TraceId 传播和业务 Controller 仍属于 Phase 6/11。

## 验证摘要

```text
Maven verify：20 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS
可执行 JAR：insurance-platform-backend-0.5.0-SNAPSHOT.jar
Python 回归：42 passed + 21 passed = 63 passed
git diff --check：exit 0
```

## 导航

| 文件 | 用途 |
|---|---|
| `MiniCourse.md` | 5 个面试必需概念 |
| `KnowledgeSummary.md` | 一页知识摘要 |
| `Interview.md` | 高频面试问答 |
| `Design.md` | 关键设计和取舍 |
| `Challenge.md` | 编码与推演练习 |
| `CallGraph.md` | 当前真实请求调用图 |
| `TestMatrix.md` | 正常/异常测试矩阵 |
| `ReviewChecklist.md` | 构建证据与四维审计 |

## 阶段边界

本阶段没有 Java 业务 Controller、Service 或 Client 实现，不访问 Python、MySQL、Redis，不实现 JWT、限流、聊天幂等、文件上传或容错。Phase 5 到此停止，不自动进入 Phase 6。
