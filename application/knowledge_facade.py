"""KnowledgeService 的 Phase 4 安全只读包装。"""

from typing import Any

from application.errors import (
    ApplicationError,
    knowledge_operation_deferred_error,
    knowledge_status_error,
)
from application.facades import KnowledgeIndexCommand, KnowledgeIndexResult


class DefaultKnowledgeFacade:
    """只开放状态查询；写操作留到 Phase 10。"""

    def __init__(self, *, knowledge_service: Any) -> None:
        self._knowledge_service = knowledge_service

    def index_document(self, command: KnowledgeIndexCommand) -> KnowledgeIndexResult:
        raise knowledge_operation_deferred_error()

    def rebuild(self) -> None:
        raise knowledge_operation_deferred_error()

    def status(self) -> dict[str, object]:
        try:
            stats = self._knowledge_service.get_stats()
        except ApplicationError:
            raise
        except Exception as exc:
            raise knowledge_status_error() from exc

        if not isinstance(stats, dict):
            raise knowledge_status_error()

        result: dict[str, object] = {
            "indexLoaded": stats.get("index_loaded") is True,
            "indexExists": stats.get("index_exists") is True,
        }
        rag_engine = stats.get("rag_engine")
        if isinstance(rag_engine, str) and rag_engine:
            result["ragEngine"] = rag_engine
        pdf_count = stats.get("pdf_count")
        if isinstance(pdf_count, int) and pdf_count >= 0:
            result["documentCount"] = pdf_count
        return result
