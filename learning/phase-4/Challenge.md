# Phase 4 Challenges

## Challenge 1：推演重复请求

给定同一 requestId 的四次调用：A 首先执行，B 在 A 进行中到达，C 在 A 成功后到达，D 使用相同 requestId 但修改 message。写出四次 HTTP 结果，并解释 Graph 实际执行次数。

验收点：B=409 IN_PROGRESS，C=缓存成功，D=409 CONFLICT，Graph 只执行一次。

## Challenge 2：发现 TTL 竞态

旧执行超过 10 分钟，新执行取得同一 requestId 后，旧执行先返回。说明只校验 requestId+digest 为什么会污染状态，并用 execution lease 写出安全状态转换条件。

## Challenge 3：上下文去重

输入两轮 history 和一个 current message，连续用相同 sessionId、不同 requestId 调用两次。列出两次 LLM 实际应看到的消息序列，并证明第二次不能包含第一次的 current message。

## Challenge 4：设计降级测试

设计三个 TestClient 用例验证 runtime_factory 抛出包含绝对路径的异常时：

1. live 仍为 200；
2. ready/chat 为 503；
3. JSON 响应不包含异常文本和绝对路径。

## Challenge 5：Knowledge 安全边界

列出把 multipart index 从“稳定失败”升级为真实实现前至少要完成的五项保护。参考答案应包含大小、MIME/PDF 签名、SHA-256、受控临时路径、原子发布/回滚和 rebuild/retrieval 并发中的至少五项。

## Challenge 6：扩展关闭协议

假设未来 LLM HTTP Client 和 Retriever 都需要显式关闭。说明如何注册 close callback、为什么逆序关闭，以及单个关闭失败时应该记录什么、绝不能返回什么。
