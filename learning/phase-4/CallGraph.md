# Phase 4 Call Graph

## 1. 启动与关闭

```text
uvicorn api.main:app
→ create_app(runtime_factory=init_api_runtime)
→ FastAPI lifespan startup
→ init_api_runtime
→ init_services(build_missing_index=False)
   → EmbeddingManager
   → Builder + Retriever
   → KnowledgeService
   → existing index? load : do not build
   → RetrievalService → Tools → AgentGraphBuilder
→ RequestRegistry(TTL=600, capacity=1024)
→ DefaultAgentFacade + DefaultKnowledgeFacade
→ app.state.runtime / ai_resources_ready=true

shutdown
→ ai_resources_ready=false
→ detach runtime
→ ApplicationRuntime.close (callbacks in reverse order)
```

初始化异常分支：记录内部异常，保持 live=200；ready 和业务接口映射为 `AI_LLM_UNAVAILABLE` 503。

## 2. Chat 正常链

```text
POST /internal/v1/agent/chat
→ TraceId + AgentChatRequest 校验
→ get_agent_facade(app.state.runtime)
→ DefaultAgentFacade.chat
→ canonical payload SHA-256
→ RequestRegistry.begin(requestId, digest)
→ history 转 LangChain Messages
→ AgentGraphBuilder.invoke(
     sessionId,
     executionId=requestId,
     finite history + current message)
→ existing LangGraph agent/tool loop
→ 最后一个非空 AIMessage
→ RequestRegistry.succeed(lease, AgentChatResult)
→ success envelope + X-Trace-Id
```

Graph finally 删除 `sessionId:requestId` 临时 checkpoint。

## 3. Chat 防重与错误分支

```text
same requestId + same digest + IN_PROGRESS → 409 AI_REQUEST_IN_PROGRESS
same requestId + same digest + SUCCEEDED  → cached AgentChatResult
same requestId + same digest + FAILED     → cached safe ApplicationError
same requestId + different digest         → 409 AI_REQUEST_CONFLICT
TimeoutError                              → 504 AI_LLM_TIMEOUT
unexpected Graph/result error             → 500 AI_INTERNAL_ERROR
```

TTL 后的新执行拥有新 lease；旧 lease 的迟到结果不能覆盖它。

## 4. Knowledge 链

```text
GET /internal/v1/knowledge/status
→ KnowledgeFacade.status
→ KnowledgeService.get_stats
→ filter paths/document names
→ indexLoaded/indexExists/ragEngine/documentCount

POST index / POST rebuild
→ KnowledgeFacade
→ 500 KNOWLEDGE_INDEX_FAILED
→ no file read / no delete / no build
```

## 5. 架构边界

```text
Router → Facade → Graph / KnowledgeService
                   ↓
                Tools / RAG
```

没有 Router→Graph、Router→Builder、Python→Java MySQL/Redis，也没有 MQ、Worker 或异步 taskId。
