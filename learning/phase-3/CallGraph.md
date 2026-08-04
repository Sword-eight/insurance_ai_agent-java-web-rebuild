# Phase 3 Call Graph

## 1. Java 启动链

```text
InsurancePlatformApplication.main
→ SpringApplication.run
→ 创建 Spring ApplicationContext
→ 读取 application.yml
→ 无 Controller / Service / Client 实现 / DB / Redis Bean
→ Context Ready
```

实际测试：`@SpringBootTest(webEnvironment = NONE)` 启动成功。

## 2. Python 启动链

```text
uvicorn api.main:app
→ import api.main
→ create_app()
→ 注册 Agent / Knowledge / Health Router
→ FastAPI lifespan
   → app.state.ai_resources_ready = False
   → 不调用 application.bootstrap.init_services()
   → 不加载 DeepSeek / BGE / FAISS / Graph
```

## 3. Liveness 正常流程

```text
GET /internal/v1/health/live
→ TraceId Header 校验
→ health.live
→ success_envelope({status: UP})
→ HTTP 200
```

## 4. Chat 占位异常流程

```text
POST /internal/v1/agent/chat
→ TraceId + AgentChatRequest 校验
→ history 完整轮次/条数/字符预算校验
→ Router 转换 AgentChatCommand
→ get_agent_facade()
→ _UnavailableAgentFacade.chat
→ ServiceUnavailableError
→ FastAPI exception handler
→ HTTP 503 + AI_LLM_UNAVAILABLE
```

当前链路不会到达 `AgentGraphBuilder`。

## 5. Knowledge 占位异常流程

```text
POST /internal/v1/knowledge/documents/index
→ TraceId 校验
→ multipart metadata + file 绑定
→ metadata JSON / UUID / SHA-256 格式校验
→ Router 转换 KnowledgeIndexCommand(BinaryIO)
→ _UnavailableKnowledgeFacade.index_document
→ HTTP 500 + AI_INTERNAL_ERROR
```

当前链路不保存文件、不读取内容、不调用 `KnowledgeService` 或 FAISS。

## 6. 下一阶段才允许的链路

```text
FastAPI lifespan
→ application.bootstrap.init_services()（Phase 4 适配后）
→ 进程级 Graph / Services / Retriever / Embedding / Index

Agent Router → Agent Facade → AgentGraphBuilder
Knowledge Router → Knowledge Facade → KnowledgeService
```

该图只说明已冻结的下一步方向，不代表 Phase 4 已实现。
