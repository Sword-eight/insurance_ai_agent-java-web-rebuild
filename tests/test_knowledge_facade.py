import hashlib
from io import BytesIO
from pathlib import Path
from threading import Lock
import time
from uuid import uuid4

import pytest

from application.errors import ApplicationError
from application.facades import KnowledgeIndexCommand
from application.knowledge_facade import DefaultKnowledgeFacade


PDF = b"%PDF-1.7\nminimal phase ten document"


class RecordingKnowledgeService:
    def __init__(self, stats=None, error: Exception | None = None, result=None) -> None:
        self.stats = stats or {}
        self.error = error
        self.result = result if result is not None else {"success": True}
        self.rebuild_calls = 0

    def get_stats(self):
        if self.error is not None:
            raise self.error
        return self.stats

    def rebuild(self):
        self.rebuild_calls += 1
        if self.error is not None:
            raise self.error
        return self.result


def command(content: bytes = PDF, sha256: str | None = None) -> KnowledgeIndexCommand:
    return KnowledgeIndexCommand(
        request_id=uuid4(),
        document_id=uuid4(),
        original_filename="terms.pdf",
        sha256=sha256 or hashlib.sha256(content).hexdigest(),
        content=BytesIO(content),
    )


def test_knowledge_status_returns_only_sanitized_fields(tmp_path: Path) -> None:
    service = RecordingKnowledgeService(
        stats={
            "index_loaded": True,
            "index_exists": True,
            "rag_engine": "langchain",
            "pdf_count": 2,
            "index_path": "C:/sensitive/index",
            "documents": ["secret.pdf"],
        }
    )
    facade = DefaultKnowledgeFacade(knowledge_service=service, document_dir=tmp_path)

    assert facade.status() == {
        "indexLoaded": True,
        "indexExists": True,
        "ragEngine": "langchain",
        "documentCount": 2,
    }


def test_knowledge_status_maps_service_failure(tmp_path: Path) -> None:
    facade = DefaultKnowledgeFacade(
        knowledge_service=RecordingKnowledgeService(error=RuntimeError("path leak")),
        document_dir=tmp_path,
    )

    with pytest.raises(ApplicationError) as captured:
        facade.status()

    assert captured.value.code == "AI_RAG_ERROR"
    assert "path leak" not in captured.value.safe_message


def test_index_document_validates_publishes_and_rebuilds(tmp_path: Path) -> None:
    service = RecordingKnowledgeService()
    facade = DefaultKnowledgeFacade(knowledge_service=service, document_dir=tmp_path)
    request = command()

    result = facade.index_document(request)

    assert result.request_id == request.request_id
    assert result.document_id == request.document_id
    assert result.index_status == "INDEXED"
    assert (tmp_path / f"{request.document_id}.pdf").read_bytes() == PDF
    assert service.rebuild_calls == 1
    assert list(tmp_path.glob("*.tmp")) == []


@pytest.mark.parametrize(
    "invalid",
    [b"not-a-pdf", b"", b"%PDF"],
)
def test_index_document_rejects_invalid_signature_without_rebuild(
    tmp_path: Path, invalid: bytes
) -> None:
    service = RecordingKnowledgeService()
    facade = DefaultKnowledgeFacade(knowledge_service=service, document_dir=tmp_path)

    with pytest.raises(ApplicationError) as captured:
        facade.index_document(command(invalid))

    assert captured.value.code == "KNOWLEDGE_INVALID_DOCUMENT"
    assert service.rebuild_calls == 0
    assert list(tmp_path.iterdir()) == []


def test_index_document_rejects_hash_mismatch_and_size_limit(tmp_path: Path) -> None:
    service = RecordingKnowledgeService()
    facade = DefaultKnowledgeFacade(
        knowledge_service=service, document_dir=tmp_path, max_pdf_bytes=len(PDF) - 1
    )

    with pytest.raises(ApplicationError) as captured:
        facade.index_document(command())
    assert captured.value.code == "KNOWLEDGE_INVALID_DOCUMENT"

    facade = DefaultKnowledgeFacade(knowledge_service=service, document_dir=tmp_path)
    with pytest.raises(ApplicationError) as captured:
        facade.index_document(command(sha256="0" * 64))
    assert captured.value.code == "KNOWLEDGE_INVALID_DOCUMENT"
    assert service.rebuild_calls == 0


def test_failed_candidate_build_removes_derived_document(tmp_path: Path) -> None:
    service = RecordingKnowledgeService(result={"success": False})
    facade = DefaultKnowledgeFacade(knowledge_service=service, document_dir=tmp_path)
    request = command()

    with pytest.raises(ApplicationError) as captured:
        facade.index_document(request)

    assert captured.value.code == "KNOWLEDGE_INDEX_FAILED"
    assert not (tmp_path / f"{request.document_id}.pdf").exists()
    assert service.rebuild_calls == 1


def test_rebuilds_are_serialized_within_process(tmp_path: Path) -> None:
    class ConcurrencyService(RecordingKnowledgeService):
        def __init__(self) -> None:
            super().__init__()
            self.active = 0
            self.max_active = 0
            self.guard = Lock()

        def rebuild(self):
            with self.guard:
                self.active += 1
                self.max_active = max(self.max_active, self.active)
            time.sleep(0.03)
            with self.guard:
                self.active -= 1
            return {"success": True}

    from concurrent.futures import ThreadPoolExecutor

    service = ConcurrencyService()
    facade = DefaultKnowledgeFacade(knowledge_service=service, document_dir=tmp_path)
    with ThreadPoolExecutor(max_workers=2) as executor:
        futures = [executor.submit(facade.rebuild) for _ in range(2)]
        for future in futures:
            future.result(timeout=2)

    assert service.max_active == 1
