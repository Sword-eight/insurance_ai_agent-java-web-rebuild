"""Knowledge HTTP Skeleton；不直接访问 Builder、Embedding 或 FAISS。"""

import json
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, UploadFile
from pydantic import ValidationError

from api.dependencies import get_knowledge_facade
from api.errors import ContractValidationError
from api.schemas.common import InternalEnvelope, TraceId, success_envelope
from api.schemas.knowledge import KnowledgeIndexMetadata
from application.facades import KnowledgeFacade, KnowledgeIndexCommand


router = APIRouter(prefix="/internal/v1/knowledge", tags=["internal-knowledge"])


@router.post("/documents/index", response_model=InternalEnvelope)
def index_document(
    trace_id: TraceId,
    metadata: Annotated[str, Form()],
    file: Annotated[UploadFile, File()],
    facade: Annotated[KnowledgeFacade, Depends(get_knowledge_facade)],
) -> InternalEnvelope:
    try:
        parsed_metadata = KnowledgeIndexMetadata.model_validate(json.loads(metadata))
    except (json.JSONDecodeError, ValidationError) as exc:
        raise ContractValidationError(
            "metadata must match the frozen knowledge contract"
        ) from exc

    result = facade.index_document(
        KnowledgeIndexCommand(
            request_id=parsed_metadata.requestId,
            document_id=parsed_metadata.documentId,
            original_filename=parsed_metadata.originalFilename,
            sha256=parsed_metadata.sha256.lower(),
            content=file.file,
        )
    )
    return success_envelope(
        {
            "requestId": result.request_id,
            "documentId": result.document_id,
            "indexStatus": result.index_status,
        },
        trace_id,
    )


@router.post("/rebuild", response_model=InternalEnvelope)
def rebuild(
    trace_id: TraceId,
    facade: Annotated[KnowledgeFacade, Depends(get_knowledge_facade)],
) -> InternalEnvelope:
    facade.rebuild()
    return success_envelope({"status": "REBUILT"}, trace_id)


@router.get("/status", response_model=InternalEnvelope)
def status(
    trace_id: TraceId,
    facade: Annotated[KnowledgeFacade, Depends(get_knowledge_facade)],
) -> InternalEnvelope:
    return success_envelope(facade.status(), trace_id)
