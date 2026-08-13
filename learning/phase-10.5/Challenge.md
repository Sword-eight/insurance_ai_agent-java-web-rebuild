# Phase 10.5 Challenges

## 1. 进度语义

让上传进度回调先返回 50%、100%，但 Promise 延迟完成。验证页面仍保持 pending，并解释为什么不能显示“索引完成”。

## 2. UNKNOWN 流程

令上传返回 `AI_SERVICE_TIMEOUT`，随后 GET 列表返回 `UNKNOWN`。验证页面只调用一次 POST、显示 TraceId，且明确禁止自动重传。

## 3. DTO 小修改

如果 Java 未来经批准增加 `updatedAt`，写出 TypeScript DTO、页面和测试的最小修改清单；不得直接暴露 Entity。

## 4. 浏览器与后端校验

列出文件名、MIME、大小、签名、SHA-256 分别应在哪一层检查，并说明浏览器检查被绕过后的保护链。

## 5. 超时预算

比较全局 65 秒、文档请求 305 秒和 Java knowledge 300 秒，说明为什么不能无限等待或自动重试。
