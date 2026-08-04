"""Agent HTTP Skeleton；不直接访问 Graph 或 RAG。"""

from typing import Annotated

from fastapi import APIRouter, Depends

from api.dependencies import get_agent_facade
from api.schemas.agent import AgentChatRequest
from api.schemas.common import InternalEnvelope, TraceId, success_envelope
from application.facades import AgentChatCommand, AgentFacade, HistoryItem


router = APIRouter(prefix="/internal/v1/agent", tags=["internal-agent"])


@router.post("/chat", response_model=InternalEnvelope)
def chat(
    request: AgentChatRequest,
    trace_id: TraceId,
    facade: Annotated[AgentFacade, Depends(get_agent_facade)],
) -> InternalEnvelope:
    result = facade.chat(
        AgentChatCommand(
            request_id=request.requestId,
            session_id=request.sessionId,
            message=request.message,
            history=tuple(
                HistoryItem(role=item.role, content=item.content)
                for item in request.history
            ),
        )
    )
    return success_envelope(
        {
            "requestId": result.request_id,
            "answer": result.answer,
            "sources": list(result.sources),
            "durationMs": result.duration_ms,
        },
        trace_id,
    )
