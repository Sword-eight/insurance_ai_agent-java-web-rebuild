# Phase 3 Challenge

## 练习 1：找出越界实现

以下代码为什么不应出现在 Phase 3？

```python
@router.post("/chat")
def chat(request):
    graph = init_services()["graph_builder"]
    return graph.invoke(request.message, request.session_id)
```

检查点：每请求初始化、Router 直调 Graph、忽略有限历史、没有 Facade、可能加载模型和索引。

## 练习 2：设计异常矩阵

为 chat Skeleton 写出以下输入的 HTTP 状态、错误码和是否进入 Facade：

1. 缺少 `X-Trace-Id`；
2. message 为空；
3. history 有 11 条；
4. history 只有一条 user；
5. 请求完全合法，但真实 Facade 尚未接入。

参考方向：前四项应在 HTTP/Schema 边界以 400 拒绝；第 5 项通过校验后返回 503。

## 练习 3：解释 Java 依赖方向

画出未来 `ChatService` 的依赖，并回答为什么测试可以给它注入假的 `AgentClient`，而不需要启动 Python。

## 练习 4：补一个契约测试

新增一个测试，证明 12000 字符 history 可接受、12001 字符拒绝。要求每条消息仍不超过 4000 字符，避免只触发单条长度校验。

## 练习 5：判断 readiness

分别判断以下情况的 live/ready：

| 情况 | live | ready |
|---|---|---|
| FastAPI 进程启动，Facade 未接入 | 200 | 503 |
| Embedding 加载失败，但 HTTP 栈仍在 | 200 | 503 |
| 进程退出 | 连接失败 | 连接失败 |

## 面试复盘

用 90 秒回答：“你为什么没有在 Skeleton 阶段直接打通 Java、FastAPI 和 DeepSeek？”答案必须包含故障面、生命周期、契约证据和 Phase 边界。
