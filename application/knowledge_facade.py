"""KnowledgeService 的安全 HTTP 应用层包装。"""

import hashlib
import os
from pathlib import Path
import tempfile
from threading import RLock
from typing import Any

from application.errors import (
    ApplicationError,
    knowledge_index_error,
    knowledge_invalid_document_error,
    knowledge_status_error,
)
from application.facades import KnowledgeIndexCommand, KnowledgeIndexResult
from config import PDF_CONFIG


MAX_PDF_BYTES = 20 * 1024 * 1024
PDF_SIGNATURE = b"%PDF-"


class DefaultKnowledgeFacade:
    """校验受控 PDF，并串行协调派生文档副本和索引发布。"""

    def __init__(
        self,
        *,
        knowledge_service: Any,
        document_dir: str | Path | None = None,
        max_pdf_bytes: int = MAX_PDF_BYTES,
    ) -> None:
        self._knowledge_service = knowledge_service
        self._document_dir = Path(document_dir or PDF_CONFIG["pdf_dir"]).resolve()
        self._max_pdf_bytes = max_pdf_bytes
        self._write_lock = RLock()

    def index_document(self, command: KnowledgeIndexCommand) -> KnowledgeIndexResult:
        target = self._document_dir / f"{command.document_id}.pdf"
        with self._write_lock:
            self._document_dir.mkdir(parents=True, exist_ok=True)
            if target.exists():
                raise knowledge_invalid_document_error()
            temporary = self._receive(command)
            published = False
            try:
                os.replace(temporary, target)
                published = True
                result = self._knowledge_service.rebuild()
                if not isinstance(result, dict) or result.get("success") is not True:
                    raise knowledge_index_error()
            except ApplicationError:
                if published:
                    target.unlink(missing_ok=True)
                raise
            except Exception as exc:
                if published:
                    target.unlink(missing_ok=True)
                raise knowledge_index_error() from exc
            finally:
                temporary.unlink(missing_ok=True)

        return KnowledgeIndexResult(
            request_id=command.request_id,
            document_id=command.document_id,
            index_status="INDEXED",
        )

    def rebuild(self) -> None:
        with self._write_lock:
            try:
                result = self._knowledge_service.rebuild()
            except ApplicationError:
                raise
            except Exception as exc:
                raise knowledge_index_error() from exc
            if not isinstance(result, dict) or result.get("success") is not True:
                raise knowledge_index_error()

    def status(self) -> dict[str, object]:
        with self._write_lock:
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

    def _receive(self, command: KnowledgeIndexCommand) -> Path:
        if (
            not command.original_filename.lower().endswith(".pdf")
            or self._max_pdf_bytes <= 0
        ):
            raise knowledge_invalid_document_error()

        descriptor, raw_path = tempfile.mkstemp(
            prefix=f"{command.document_id}-", suffix=".tmp", dir=self._document_dir
        )
        os.close(descriptor)
        temporary = Path(raw_path)
        digest = hashlib.sha256()
        size = 0
        signature = b""
        try:
            with temporary.open("wb") as output:
                while True:
                    chunk = command.content.read(64 * 1024)
                    if not chunk:
                        break
                    if not isinstance(chunk, bytes):
                        raise knowledge_invalid_document_error()
                    size += len(chunk)
                    if size > self._max_pdf_bytes:
                        raise knowledge_invalid_document_error()
                    if len(signature) < len(PDF_SIGNATURE):
                        needed = len(PDF_SIGNATURE) - len(signature)
                        signature += chunk[:needed]
                    digest.update(chunk)
                    output.write(chunk)

            if (
                size == 0
                or signature != PDF_SIGNATURE
                or digest.hexdigest().lower() != command.sha256.lower()
            ):
                raise knowledge_invalid_document_error()
            return temporary
        except Exception:
            temporary.unlink(missing_ok=True)
            raise
