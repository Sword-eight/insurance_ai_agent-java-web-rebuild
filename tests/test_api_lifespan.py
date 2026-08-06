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
    def __init__(self) -> None:
        self.calls: list[AgentChatCommand] = []

    def chat(self, command: AgentChatCommand) -> AgentChatResult:
        self.calls.append(command)
        return AgentChatResult(
            request_id=command.request_id,
            answer="真实测试回答",
            sources=(),
            duration_ms=12,
        )


class StatusKnowledgeService:
    def get_stats(self):
        return {
            "index_loaded": True,
            "index_exists": True,
            "rag_engine": "langchain",
        }


def _runtime(*, close_callback=lambda: None) -> ApplicationRuntime:
    return ApplicationRuntime(
        agent_facade=SuccessfulAgentFacade(),
        knowledge_facade=DefaultKnowledgeFacade(
            knowledge_service=StatusKnowledgeService()
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


def test_knowledge_status_is_available_but_writes_remain_deferred() -> None:
    app = create_app(runtime_factory=_runtime)
    metadata = (
        "{"
        f'"requestId":"{uuid4()}",'
        f'"documentId":"{uuid4()}",'
        '"originalFilename":"terms.pdf",'
        f'"sha256":"{"a" * 64}"'
        "}"
    )

    with TestClient(app) as client:
        status = client.get("/internal/v1/knowledge/status", headers=HEADERS)
        rebuild = client.post("/internal/v1/knowledge/rebuild", headers=HEADERS)
        index = client.post(
            "/internal/v1/knowledge/documents/index",
            headers=HEADERS,
            data={"metadata": metadata},
            files={"file": ("terms.pdf", b"%PDF-1.7\n", "application/pdf")},
        )

    assert status.status_code == 200
    assert status.json()["data"] == {
        "indexLoaded": True,
        "indexExists": True,
        "ragEngine": "langchain",
    }
    assert rebuild.status_code == 500
    assert index.status_code == 500
    assert rebuild.json()["error"]["code"] == "KNOWLEDGE_INDEX_FAILED"
    assert index.json()["error"]["code"] == "KNOWLEDGE_INDEX_FAILED"
