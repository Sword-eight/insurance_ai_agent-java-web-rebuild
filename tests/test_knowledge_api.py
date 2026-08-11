import hashlib
import json
from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient

from api.main import create_app
from application.knowledge_facade import DefaultKnowledgeFacade
from application.runtime import ApplicationRuntime


TRACE_ID = "01J4EXAMPLETRACE10"
PDF = b"%PDF-1.7\napi upload"


class SuccessfulKnowledgeService:
    def __init__(self) -> None:
        self.rebuild_calls = 0

    def rebuild(self):
        self.rebuild_calls += 1
        return {"success": True}

    def get_stats(self):
        return {"index_loaded": True, "index_exists": True, "pdf_count": 1}


def app_for(tmp_path: Path, service: SuccessfulKnowledgeService):
    return create_app(
        runtime_factory=lambda: ApplicationRuntime(
            agent_facade=object(),
            knowledge_facade=DefaultKnowledgeFacade(
                knowledge_service=service, document_dir=tmp_path
            ),
        )
    )


def test_ready_api_accepts_frozen_multipart_and_returns_indexed(tmp_path: Path) -> None:
    service = SuccessfulKnowledgeService()
    request_id = uuid4()
    document_id = uuid4()
    metadata = {
        "requestId": str(request_id),
        "documentId": str(document_id),
        "originalFilename": "terms.pdf",
        "sha256": hashlib.sha256(PDF).hexdigest(),
    }

    with TestClient(app_for(tmp_path, service)) as client:
        response = client.post(
            "/internal/v1/knowledge/documents/index",
            headers={"X-Trace-Id": TRACE_ID},
            data={"metadata": json.dumps(metadata)},
            files={"file": ("controlled.pdf", PDF, "application/pdf")},
        )

    assert response.status_code == 200
    assert response.json() == {
        "success": True,
        "data": {
            "requestId": str(request_id),
            "documentId": str(document_id),
            "indexStatus": "INDEXED",
        },
        "error": None,
        "traceId": TRACE_ID,
    }
    assert service.rebuild_calls == 1
    assert (tmp_path / f"{document_id}.pdf").read_bytes() == PDF


def test_ready_api_rejects_malformed_metadata_before_reading_file(tmp_path: Path) -> None:
    service = SuccessfulKnowledgeService()
    with TestClient(app_for(tmp_path, service)) as client:
        response = client.post(
            "/internal/v1/knowledge/documents/index",
            headers={"X-Trace-Id": TRACE_ID},
            data={"metadata": "not-json"},
            files={"file": ("controlled.pdf", PDF, "application/pdf")},
        )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "AI_VALIDATION_ERROR"
    assert service.rebuild_calls == 0
    assert list(tmp_path.iterdir()) == []
