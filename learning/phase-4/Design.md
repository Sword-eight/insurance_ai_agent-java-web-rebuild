# Phase 4 Design Notes

## 1. Composition Root

选择：在 FastAPI lifespan 调用 `init_api_runtime()`，由 bootstrap 组装现有对象图和新增 Facade。

替代方案：模块 import 时初始化、Router 懒初始化、每请求初始化。前两者难以隔离测试和表达失败状态，后一种成本和状态语义错误。

兼容性：`init_services()` 新增 keyword-only `build_missing_index`，默认仍为 true，保留 Streamlit 现有行为；FastAPI 显式传 false。

## 2. Application Error

Application 层错误不依赖 FastAPI。它只携带冻结 code、type、安全 message 和 retryable；HTTP 层集中映射状态码并包装 envelope。未知异常统一为 `AI_INTERNAL_ERROR`，响应不包含堆栈或本机路径。

## 3. 有限上下文与执行隔离

Facade 把 Java history 转为 HumanMessage/AIMessage。Graph 新增可选 `history_messages` 和 `execution_id`，因此旧 Streamlit 调用签名仍可工作。

HTTP 模式使用复合 threadId 隔离请求并在 finally 删除 checkpoint。它把 checkpointer 定位为本次执行的临时设施，而不是会话事实源。

## 4. 幂等状态机

登记表使用 `OrderedDict + Lock + monotonic clock`：锁覆盖检查、冲突判断和注册；终态按 LRU 顺序淘汰；缓存的是不可变结果或安全错误，不缓存异常对象和 traceback。

每个 IN_PROGRESS 条目拥有 execution lease。TTL 到期后可以创建新 lease，但旧 lease 无权改变新状态。这一设计比单纯比较 requestId+digest 多解决了迟到完成竞态。

## 5. Knowledge 的阶段拆分

status 是只读、可过滤的能力，Phase 4 可以安全接入。index/rebuild 虽有冻结路径，但当前实现缺少文件安全和原子索引切换，因此返回稳定失败，不读取或写入内容。接口存在不代表阶段越权实现。

## 6. 明确未选择

- 不让 Python 访问 MySQL/Redis；
- 不引入共享幂等存储、MQ、Worker 或 taskId；
- 不重写 graph/tools/services/rag；
- 不伪造 source/toolCalls；
- 不在启动时自动构建缺失索引；
- 不把本阶段的离线替身测试描述成真实 DeepSeek 集成测试。
