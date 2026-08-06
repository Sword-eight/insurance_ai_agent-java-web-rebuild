from io import BytesIO
from uuid import uuid4

import pytest

from application.errors import ApplicationError
from application.facades import KnowledgeIndexCommand
from application.knowledge_facade import DefaultKnowledgeFacade


class RecordingKnowledgeService:
    def __init__(self, stats=None, error: Exception | None = None) -> None:
        self.stats = stats or {}
        self.error = error
        self.rebuild_calls = 0

    def get_stats(self):
        if self.error is not None:
            raise self.error
        return self.stats

    def rebuild(self):
        self.rebuild_calls += 1


def test_knowledge_status_returns_only_sanitized_fields() -> None:
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
    facade = DefaultKnowledgeFacade(knowledge_service=service)

    assert facade.status() == {
        "indexLoaded": True,
        "indexExists": True,
        "ragEngine": "langchain",
        "documentCount": 2,
    }


def test_knowledge_status_maps_service_failure() -> None:
    facade = DefaultKnowledgeFacade(
        knowledge_service=RecordingKnowledgeService(error=RuntimeError("path leak"))
    )

    with pytest.raises(ApplicationError) as captured:
        facade.status()

    assert captured.value.code == "AI_RAG_ERROR"
    assert "path leak" not in captured.value.safe_message


def test_index_document_is_deferred_without_reading_content() -> None:
    service = RecordingKnowledgeService()
    facade = DefaultKnowledgeFacade(knowledge_service=service)
    content = BytesIO(b"%PDF-1.7\n")
    command = KnowledgeIndexCommand(
        request_id=uuid4(),
        document_id=uuid4(),
        original_filename="terms.pdf",
        sha256="a" * 64,
        content=content,
    )

    with pytest.raises(ApplicationError) as captured:
        facade.index_document(command)

    assert captured.value.code == "KNOWLEDGE_INDEX_FAILED"
    assert content.tell() == 0
    assert service.rebuild_calls == 0


def test_rebuild_is_deferred_without_deleting_or_building_index() -> None:
    service = RecordingKnowledgeService()
    facade = DefaultKnowledgeFacade(knowledge_service=service)

    with pytest.raises(ApplicationError) as captured:
        facade.rebuild()

    assert captured.value.code == "KNOWLEDGE_INDEX_FAILED"
    assert service.rebuild_calls == 0
