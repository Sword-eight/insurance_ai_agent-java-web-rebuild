import hashlib
import json
from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient

from tests.support.phase12_e2e_api import create_app, create_phase12_runtime


TRACE_ID = "01J4PHASE12TRACE01"
HEADERS = {"X-Trace-Id": TRACE_ID}
FIXTURE = Path(__file__).parent / "fixtures" / "phase12-terms.pdf"


def test_phase12_runtime_exercises_real_fastapi_chat_contract(tmp_path, monkeypatch) -> None:
    monkeypatch.setenv("PHASE12_PYTHON_DOCUMENT_DIR", str(tmp_path))
    request_id = uuid4()
    payload = {
        "requestId": str(request_id),
        "sessionId": str(uuid4()),
        "message": "等待期一般有多久？",
        "history": [],
    }

    with TestClient(create_app(runtime_factory=create_phase12_runtime)) as client:
        response = client.post("/internal/v1/agent/chat", headers=HEADERS, json=payload)
        replay = client.post("/internal/v1/agent/chat", headers=HEADERS, json=payload)

    assert response.status_code == 200
    assert response.headers["X-Trace-Id"] == TRACE_ID
    assert response.json()["data"]["requestId"] == str(request_id)
    assert response.json()["data"]["answer"].startswith("Phase 12 E2E：")
    assert replay.json() == response.json()


def test_phase12_runtime_exercises_real_fastapi_document_contract(tmp_path, monkeypatch) -> None:
    monkeypatch.setenv("PHASE12_PYTHON_DOCUMENT_DIR", str(tmp_path))
    content = FIXTURE.read_bytes()
    request_id = uuid4()
    document_id = uuid4()
    metadata = {
        "requestId": str(request_id),
        "documentId": str(document_id),
        "originalFilename": "phase12-terms.pdf",
        "sha256": hashlib.sha256(content).hexdigest(),
    }

    with TestClient(create_app(runtime_factory=create_phase12_runtime)) as client:
        response = client.post(
            "/internal/v1/knowledge/documents/index",
            headers=HEADERS,
            data={"metadata": json.dumps(metadata)},
            files={"file": ("phase12-terms.pdf", content, "application/pdf")},
        )

    assert response.status_code == 200
    assert response.json()["data"] == {
        "requestId": str(request_id),
        "documentId": str(document_id),
        "indexStatus": "INDEXED",
    }
    assert (tmp_path / f"{document_id}.pdf").read_bytes() == content
