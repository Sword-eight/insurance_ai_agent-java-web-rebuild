from uuid import uuid4

from fastapi.testclient import TestClient

from api.main import app


TRACE_ID = "01J4EXAMPLETRACE01"
HEADERS = {"X-Trace-Id": TRACE_ID}


def test_liveness_is_available_without_ai_resources() -> None:
    with TestClient(app) as client:
        response = client.get("/internal/v1/health/live", headers=HEADERS)

    assert response.status_code == 200
    assert response.json() == {
        "success": True,
        "data": {"status": "UP"},
        "error": None,
        "traceId": TRACE_ID,
    }


def test_readiness_explicitly_reports_unavailable_skeleton() -> None:
    with TestClient(app) as client:
        response = client.get("/internal/v1/health/ready", headers=HEADERS)

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "AI_LLM_UNAVAILABLE"
    assert response.json()["success"] is False


def test_chat_route_validates_contract_then_reports_unavailable_facade() -> None:
    payload = {
        "requestId": str(uuid4()),
        "sessionId": str(uuid4()),
        "message": "等待期一般有多久？",
        "history": [],
    }

    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/agent/chat",
            headers=HEADERS,
            json=payload,
        )

    assert response.status_code == 503
    body = response.json()
    assert body["error"]["code"] == "AI_LLM_UNAVAILABLE"
    assert "answer" not in body


def test_chat_route_maps_invalid_request_to_frozen_validation_error() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/agent/chat",
            headers=HEADERS,
            json={"message": ""},
        )

    assert response.status_code == 400
    assert response.json()["error"] == {
        "code": "AI_VALIDATION_ERROR",
        "type": "VALIDATION",
        "message": "request does not match the frozen internal contract",
        "retryable": False,
    }


def test_internal_route_requires_valid_trace_id() -> None:
    with TestClient(app) as client:
        response = client.get("/internal/v1/health/live")

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "AI_VALIDATION_ERROR"


def test_knowledge_index_accepts_multipart_shape_but_does_not_read_ai_core() -> None:
    metadata = (
        "{"
        f'"requestId":"{uuid4()}",'
        f'"documentId":"{uuid4()}",'
        '"originalFilename":"terms.pdf",'
        f'"sha256":"{"a" * 64}"'
        "}"
    )

    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/knowledge/documents/index",
            headers=HEADERS,
            data={"metadata": metadata},
            files={"file": ("terms.pdf", b"%PDF-1.7\n", "application/pdf")},
        )

    assert response.status_code == 500
    assert response.json()["error"]["code"] == "AI_INTERNAL_ERROR"


def test_knowledge_index_rejects_invalid_metadata_before_facade() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/knowledge/documents/index",
            headers=HEADERS,
            data={"metadata": "not-json"},
            files={"file": ("terms.pdf", b"%PDF-1.7\n", "application/pdf")},
        )

    assert response.status_code == 400
    assert response.json()["error"] == {
        "code": "AI_VALIDATION_ERROR",
        "type": "VALIDATION",
        "message": "metadata must match the frozen knowledge contract",
        "retryable": False,
    }
