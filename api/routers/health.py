"""进程存活与 AI Runtime 就绪状态。"""

from fastapi import APIRouter, Request

from api.schemas.common import InternalEnvelope, TraceId, success_envelope
from application.errors import runtime_unavailable_error


router = APIRouter(prefix="/internal/v1/health", tags=["internal-health"])


@router.get("/live", response_model=InternalEnvelope)
def live(trace_id: TraceId) -> InternalEnvelope:
    return success_envelope({"status": "UP"}, trace_id)


@router.get("/ready", response_model=InternalEnvelope)
def ready(request: Request, trace_id: TraceId) -> InternalEnvelope:
    if not getattr(request.app.state, "ai_resources_ready", False):
        raise runtime_unavailable_error()
    return success_envelope({"status": "UP"}, trace_id)
