# Phase 0 MiniCourse：从真实源码得到可执行迁移计划

> 面向基础：会 Java 语法、面向对象和 MySQL CRUD；不要求会 Spring Boot。  
> 本阶段只学习 4 个知识点，不写 Java/FastAPI 业务代码。

## 课程 1：事实来源——为什么不能照 README 画架构

### 1. 当前项目为什么需要它

这个仓库同时存在 README、微调设计文档、当前工作树、Git 历史和训练产物。它们并不完全一致：
文档写 1.5B，实际 Adapter 是 0.5B；文档说所有依赖都注入，Premium Tool 却自己创建 Service。
迁移前若不先确定事实来源，后续 Java 接口会建立在错误假设上。

### 2. 它解决了什么问题

建立证据优先级：

```text
可复现运行结果
  > 当前源码与配置
  > 当前产物元数据
  > Git 历史
  > README/设计文档
  > 口头描述
```

这不是说文档没用，而是文档必须接受源码核对。

### 3. 不使用会怎样

- 会为不存在的 FastAPI 接口写 Java Client。
- 会按 1.5B 配置部署一个实际依赖 0.5B 的 Adapter。
- 会把“测试文件存在”误认为“pytest 测试可执行”。
- 面试时一追问调用链，就会出现描述和代码对不上。

### 4. 本项目代码会如何使用

例如核对“548 行单体重构”：

- Git 历史确实有 `refactor: app.py 拆分` 提交。
- 重构前 `app.py` 可核实为 497 个物理行，当前为 70 行。
- 因而可以说“完成了单体拆分”，但不应继续声称“548 行”是已验证事实。

Phase 1 冻结后，`docs/ARCHITECTURE.md` 将成为架构唯一事实来源；代码不一致时必须报告漂移。

### 5. 简单类比

README 像餐厅菜单，源码像厨房里真实使用的配方，运行结果像端上桌的菜。审查不能只看菜单。

### 6. 容易踩的坑

- 看到类名就认为功能已接通。
- 看到 `tests/` 就认为测试已执行。
- 看到配置项就认为运行时真的使用该配置。
- 只看 Git `main`，忽略工作区未提交修改。
- 为了让文档“看起来一致”而偷偷改代码。

### 7. 当前阶段掌握到什么深度

能把每条关键声明标为：

- 已实现且有源码证据；
- 部分实现；
- 未实现；
- 存在但未接入；
- 无法在本阶段验证。

不需要学习 Git 内部对象或复杂静态分析。

### 8. 5～10 分钟练习

打开 `finetune/README.md`、`config.py`、`finetune/scripts/run_train.py` 和
`finetune/outputs/adapter/adapter_config.json`，写出一句不超过 50 字的准确结论，
同时包含 0.5B 与 1.5B 的关系。

参考方向：区分“默认/生成配置”和“实际训练产物”，不要只说其中一个是错的。

## 课程 2：从入口追调用链，而不是按目录猜分层

### 1. 当前项目为什么需要它

目标架构要求 Java 的 `ChatController → ChatService → AgentClient → Python`。
在设计 Python API 前，必须知道当前一条消息最终调用了谁，否则包装层可能绕过
`AgentGraphBuilder`，破坏 Tool 循环和记忆。

### 2. 它解决了什么问题

调用链回答三个问题：

1. 请求从哪里进入？
2. 每一层把什么数据交给下一层？
3. 哪个对象真正执行副作用，例如 API 调用、文件写入或索引读取？

当前聊天核心链是：

```text
ui.chat
→ application.handlers
→ AgentGraphBuilder.invoke
→ agent
→ router
→ tools
→ service
→ retriever/index
```

### 3. 不使用会怎样

- FastAPI Router 可能直接调用 Retriever，绕过 Agent。
- Java Controller 可能直接调用 Python Client，绕过 ChatService。
- 会把 `application/session.py` 的 UI 状态误当成 LangGraph 的记忆。
- 异常无法判断应由 Tool、Service、HTTP 层还是 Java 统一异常处理。

### 4. 本项目代码会如何使用

以“等待期多久？”为例：

```text
ChatController（未来 Java）
→ ChatService（未来 Java）
→ AgentClient（未来 Java）
→ FastAPI Chat Router（未来薄包装）
→ AgentGraphBuilder.invoke（现有）
→ ChatOpenAI 决定调用 insurance_rag_search
→ InsuranceRAGTool
→ RetrievalService
→ LangChainRetriever 或 LlamaIndexRetriever
→ FAISS
→ ToolMessage
→ ChatOpenAI 生成最终回答
```

Java 不需要知道 FAISS、Chunk 或 ToolMessage，只需要稳定的 HTTP 响应契约。

### 5. 简单类比

快递从寄件人到收件人要经过揽收、分拣、运输和派送。目录名只是仓库门牌，调用链才是包裹真正走过的路线。

### 6. 容易踩的坑

- 只画正常路径，不画 Tool 失败、空检索和 LLM 超时。
- 把类之间“可以调用”画成“运行时真的调用”。
- 忽略对象在启动时已经创建，还是每个请求重新创建。
- 把 UI 回调当成可直接复用的 HTTP Service。

### 7. 当前阶段掌握到什么深度

能从 `app.py` 开始，手工追到：

- 直接回答分支；
- RAG Tool 分支；
- Premium Tool 分支；
- PDF 上传重建分支。

不需要掌握 LangGraph 所有 API。

### 8. 5～10 分钟练习

不看本课程上面的图，从 `app.py` 的 `_on_send()` 开始，在纸上写出 8～12 个节点，
一直追到 `VectorStoreManager.similarity_search()`。每个箭头旁写上传递的数据：
`prompt`、`session_id`、`AgentState`、`query` 或 `RetrievalResult`。

## 课程 3：依赖注入与生命周期——“传了参数”不等于“用了注入”

### 1. 当前项目为什么需要它

Embedding 模型、HTTP Client、Redis Client 都是昂贵或持有资源的对象。如果每个请求都创建，
会变慢、占内存、耗连接。当前仓库已经试图用 `bootstrap.py` 统一组装，但 Premium Tool
存在一个非常适合面试讲解的反例。

### 2. 它解决了什么问题

依赖注入的核心不是框架，而是：

```text
对象不决定“依赖是谁、怎么创建”
对象只声明“我需要什么”
组装入口决定具体实现和生命周期
```

正确关系应是：

```text
bootstrap 创建 PremiumService
→ 注入 PremiumCalculatorTool
→ Tool 保存并使用同一个实例
```

### 3. 不使用会怎样

- 同一份费率配置重复加载。
- 测试无法注入假的 PremiumService。
- FastAPI startup 创建的单例会被 Tool 绕过。
- 未来 HTTP Client 可能每次请求新建连接池，导致连接资源浪费。

### 4. 本项目代码会如何使用

当前 `bootstrap.py` 调用：

```python
premium_service = PremiumService()
premium_tool = PremiumCalculatorTool(premium_service=premium_service)
```

但 `PremiumCalculatorTool.__init__()` 实际执行：

```python
self._service = PremiumService()
```

诊断结果显示传入对象没有被使用。Phase 0 只登记问题；只有进入对应阶段、经过 Skeleton Review
和再次批准后才能修复。

另一个生命周期问题是 `init_services()` 每次 Streamlit rerun 都创建新的 `InMemorySaver`，
所以 UI 看得到旧消息，不代表 Agent 仍持有旧上下文。

### 5. 简单类比

公司已经给员工配了一辆公车，但员工每次出门又自己买一辆车。表面上“公司提供了车”，实际完全没用，还产生重复成本。

### 6. 容易踩的坑

- 构造器接收 `**kwargs`，误以为所有参数都会自动成为依赖。
- 把“单例类”当成所有对象生命周期都正确。
- 在 Controller/Router 里临时 `new` Service。
- 在每次请求里创建 HTTP Client 或加载模型。
- 测试只断言对象类型，不断言注入的是同一个实例。

### 7. 当前阶段掌握到什么深度

能识别三种生命周期：

- 进程级：Embedding、HTTP Client、Graph、连接池；
- 请求级：请求 DTO、Trace 上下文；
- 会话/业务级：conversation、聊天消息。

并能用身份断言表达 DI 测试：`tool._service is injected_service`。

### 8. 5～10 分钟练习

写一个不依赖 Spring 的 Java 小例子：

```java
class AgentClient {}
class ChatService {
    private final AgentClient agentClient;
    ChatService(AgentClient agentClient) {
        this.agentClient = agentClient;
    }
}
```

然后回答：如果 `ChatService` 构造器内部改成 `this.agentClient = new AgentClient()`，
测试替换、连接复用和配置管理会分别受到什么影响？

## 课程 4：双服务边界——包装核心，而不是搬运核心

### 1. 当前项目为什么需要它

目标是 Web → Java → Python。Java 负责用户、会话、消息、数据库、Redis 和统一 REST；
Python 已经有稳定的 Agent/RAG 语义。迁移的关键不是“把 Python 改写成 Java”，而是在边界上加一个稳定协议。

### 2. 它解决了什么问题

薄包装把两种变化隔开：

- Java 业务规则变化，不影响 FAISS/LangGraph。
- Python RAG 引擎变化，不影响 Controller/数据库表。
- Java/Python 只通过明确的请求和响应 DTO 协作。

建议关系：

```text
Java ChatService
→ AgentClient
→ HTTP
→ FastAPI Router
→ Python Application Wrapper
→ AgentGraphBuilder
```

### 3. 不使用会怎样

- Java 会逐步复制 Prompt、Tool 和 RAG 逻辑。
- Python 会为了查用户/会话而直连 Java MySQL。
- Controller 会混合鉴权、持久化、远程调用和异常处理。
- 两个服务无法独立测试和演进。

### 4. 本项目代码会如何使用

FastAPI Router 只负责：

- 接收并校验 chat 请求；
- 调用现有 Agent 入口；
- 把结果转换成稳定响应；
- 映射 Python 内部异常；
- 传递 TraceId。

它不直接调用 `LangChainRetriever`，也不重新实现 `Agent → Router → Tools`。

Java `ChatService` 负责：

- 校验会话归属；
- 保存用户消息；
- 调用 `AgentClient`；
- 保存助手消息或失败状态；
- 返回统一 VO。

`ChatController` 只接收参数和返回结果。

### 5. 简单类比

给现有发动机加标准变速箱接口，而不是为了换车身重新制造发动机。Java 是业务车身，Python 是 AI 发动机，HTTP DTO 是连接法兰。

### 6. 容易踩的坑

- Router 直接调用 Retriever，绕过 Graph。
- Java Client 把 Python 内部类名暴露给 Web API。
- Python 接收 `userId` 后直接查询 Java 数据库。
- 对同步聊天盲目重试，产生重复消息。
- 为“以后可能用”提前加入 MQ、Worker 和 `task_id`。

### 7. 当前阶段掌握到什么深度

能解释清楚：

- 哪些数据归 Java；
- 哪些算法归 Python；
- 为什么只加 HTTP 薄包装；
- 为什么第一版同步；
- timeout、retry、熔断为什么属于边界可靠性，而不是 RAG 核心。

不需要现在掌握 Spring MVC 注解或 FastAPI 语法，它们分别在后续 Phase 教学。

### 8. 5～10 分钟练习

把以下职责分别放进 Java、Python 或“两者的 HTTP 契约”：

1. JWT 校验
2. FAISS 检索
3. conversationId
4. Tool Calling
5. 消息落 MySQL
6. RAG 引用来源
7. TraceId
8. 限流

参考答案：

- Java：1、5、8。
- Python：2、4。
- HTTP 契约：3、6、7；Java 管业务含义，Python接收/返回必要字段。
