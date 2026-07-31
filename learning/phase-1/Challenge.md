# Phase 1 Challenge：架构练习

> 不要求编写 Spring/FastAPI 业务代码。练习产物以图、表、判断和口述为主。

## 练习 1：画完整聊天调用链

- **任务**：分别画当前 Streamlit 链和目标 Java→Python 链，并画出直接回答、RAG Tool、保费 Tool 三个分支。
- **限制**：当前链只能使用真实文件/函数；目标节点必须标“尚未实现”；不能把 RAG 画成必经链。
- **验收标准**：能定位 `handle_chat_message`、`AgentGraphBuilder`、Router、两种 Tool、Service 和 DeepSeek/FAISS。
- **提示**：bootstrap 是启动装配，不是每条消息的业务节点。

## 练习 2：职责归位

- **任务**：把 JWT 校验、消息事务、Python HTTP 超时、SQL、Embedding、限流、响应 VO 分配到 Controller/Service/Client/Mapper/Python/Redis。
- **限制**：每项只能有一个主要责任方；需要跨边界时写明传递的数据。
- **验收标准**：符合 `Controller → Service → Client/Mapper`，Java 不读取 FAISS，Python 不访问业务 MySQL。
- **提示**：区分“数据所有者”和“数据使用者”。

## 练习 3：找出跨层错误

- **任务**：审查一个假想设计：`ChatController` 直接调用 Mapper 保存消息，再用 HTTP 调 Python；Python Router 直接调用 FAISS。
- **限制**：至少指出四处职责或异常处理问题，不写完整修复代码。
- **验收标准**：能给出正确下一层，并说明超时后消息状态应由谁决定。
- **提示**：检查 Controller、Service、Client、Facade 是否被绕过。

## 练习 4：口述服务边界

- **任务**：用 90 秒解释为什么用户/会话归 Java，而 LangGraph/RAG归 Python。
- **限制**：不能只说“Java 适合后端、Python 适合 AI”；必须提数据事实源和变化隔离。
- **验收标准**：包含 MySQL、有限历史、FAISS 和内部 HTTP 四个关键词。
- **提示**：从“谁拥有最终解释权”开始。

## 练习 5：新增需求放哪一侧

- **任务**：分析“回答增加来源页码”“管理员查看文档上传者”“增加 Rerank”“限制用户每分钟聊天次数”分别修改哪一侧。
- **限制**：不得增加新服务或 MQ；对跨服务需求写出最小契约变化。
- **验收标准**：能区分 AI 结果、业务元数据、检索算法和限流。
- **提示**：来源由 Python产生，上传者由 Java保存，限流属于 Redis/Java入口。

## 练习 6：超时与重复请求风险

- **任务**：分析 Java 等待 60 秒后读取超时、但 Python 可能已执行 Tool 的场景，设计不重复写消息/调用 Tool 的原则。
- **限制**：不得回答“统一重试三次”；不得假设已有 MQ 或持久化 Python 幂等库。
- **验收标准**：区分连接失败和读取超时，使用同一 requestId，并说明未知结果为何不能自动重试。
- **提示**：超时只代表调用方不再等待，不代表被调用方停止执行。
