from pathlib import Path
import hashlib
from uuid import uuid4

from fastapi.testclient import TestClient

from api.main import create_app
from application.facades import (
    AgentChatCommand,
    AgentChatResult,
    KnowledgeIndexCommand,
    KnowledgeIndexResult,
)
from application.knowledge_facade import DefaultKnowledgeFacade
from application.runtime import ApplicationRuntime


TRACE_ID = "01J4EXAMPLETRACE01"
HEADERS = {"X-Trace-Id": TRACE_ID}


class SuccessfulAgentFacade:
    def __init__(self, *, sources=()) -> None:
        self.calls: list[AgentChatCommand] = []
        self.sources = sources

    def chat(self, command: AgentChatCommand) -> AgentChatResult:
        self.calls.append(command)
        return AgentChatResult(
            request_id=command.request_id,
            answer="真实测试回答",
            sources=self.sources,
            duration_ms=12,
        )


class StatusKnowledgeService:
    def get_stats(self):
        return {
            "index_loaded": True,
            "index_exists": True,
            "rag_engine": "langchain",
        }

    def rebuild(self):
        return {"success": True}


def _runtime(
    *,
    close_callback=lambda: None,
    knowledge_document_dir: Path | None = None,
    sources=(),
) -> ApplicationRuntime:
    return ApplicationRuntime(
        agent_facade=SuccessfulAgentFacade(sources=sources),
        knowledge_facade=DefaultKnowledgeFacade(
            knowledge_service=StatusKnowledgeService(),
            document_dir=knowledge_document_dir,
        ),
        close_callbacks=(close_callback,),
    )


def test_lifespan_initializes_once_reuses_runtime_and_closes_once() -> None:
    counts = {"init": 0, "close": 0}

    def close() -> None:
        counts["close"] += 1

    def factory() -> ApplicationRuntime:
        counts["init"] += 1
        return _runtime(close_callback=close)

    app = create_app(runtime_factory=factory)
    with TestClient(app) as client:
        assert client.get("/internal/v1/health/ready", headers=HEADERS).status_code == 200
        assert client.get("/internal/v1/health/ready", headers=HEADERS).status_code == 200
        assert counts == {"init": 1, "close": 0}

    assert counts == {"init": 1, "close": 1}


def test_http_chat_success_uses_runtime_facade_and_echoes_trace_header() -> None:
    runtime = _runtime()
    app = create_app(runtime_factory=lambda: runtime)
    request_id = uuid4()
    payload = {
        "requestId": str(request_id),
        "sessionId": str(uuid4()),
        "message": "等待期多久？",
        "history": [],
    }

    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/agent/chat",
            headers=HEADERS,
            json=payload,
        )

    assert response.status_code == 200
    assert response.headers["X-Trace-Id"] == TRACE_ID
    assert response.json()["data"] == {
        "requestId": str(request_id),
        "answer": "真实测试回答",
        "sources": [],
        "durationMs": 12,
    }
    assert len(runtime.agent_facade.calls) == 1


def test_bootstrap_failure_keeps_live_but_not_ready_or_business_available() -> None:
    def failing_factory() -> ApplicationRuntime:
        raise RuntimeError("local path must not leak")

    app = create_app(runtime_factory=failing_factory)
    payload = {
        "requestId": str(uuid4()),
        "sessionId": str(uuid4()),
        "message": "问题",
        "history": [],
    }

    with TestClient(app) as client:
        live = client.get("/internal/v1/health/live", headers=HEADERS)
        ready = client.get("/internal/v1/health/ready", headers=HEADERS)
        chat = client.post("/internal/v1/agent/chat", headers=HEADERS, json=payload)

    assert live.status_code == 200
    assert ready.status_code == 503
    assert chat.status_code == 503
    assert ready.json()["error"]["code"] == "AI_LLM_UNAVAILABLE"
    assert "local path" not in ready.text


def test_http_chat_serializes_nonempty_retriever_sources() -> None:
    source = {
        "documentName": "terms.pdf",
        "page": 2,
        "snippet": "waiting period is 80 days",
        "score": 0.92,
    }
    runtime = _runtime(sources=(source,))
    app = create_app(runtime_factory=lambda: runtime)
    payload = {
        "requestId": str(uuid4()),
        "sessionId": str(uuid4()),
        "message": "waiting period?",
        "history": [],
    }

    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/agent/chat",
            headers=HEADERS,
            json=payload,
        )

    assert response.status_code == 200
    assert response.json()["data"]["sources"] == [source]


def test_knowledge_status_rebuild_and_invalid_document_use_phase10_contract(
    tmp_path: Path,
) -> None:
    app = create_app(
        runtime_factory=lambda: _runtime(knowledge_document_dir=tmp_path)
    )
    content = b"%PDF-1.7\n"
    metadata = (
        "{"
        f'"requestId":"{uuid4()}",'
        f'"documentId":"{uuid4()}",'
        '"originalFilename":"terms.pdf",'
        f'"sha256":"{hashlib.sha256(content).hexdigest()}"'
        "}"
    )

    with TestClient(app) as client:
        status = client.get("/internal/v1/knowledge/status", headers=HEADERS)
        rebuild = client.post("/internal/v1/knowledge/rebuild", headers=HEADERS)
        index = client.post(
            "/internal/v1/knowledge/documents/index",
            headers=HEADERS,
            data={"metadata": metadata},
            files={"file": ("terms.pdf", b"not-a-pdf", "application/pdf")},
        )

    assert status.status_code == 200
    assert status.json()["data"] == {
        "indexLoaded": True,
        "indexExists": True,
        "ragEngine": "langchain",
    }
    assert rebuild.status_code == 200
    assert rebuild.json()["data"] == {"status": "REBUILT"}
    assert index.status_code == 400
    assert index.json()["error"]["code"] == "KNOWLEDGE_INVALID_DOCUMENT"
