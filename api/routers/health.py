"""不触发模型或索引初始化的健康检查 Skeleton。"""

from fastapi import APIRouter

from api.errors import ServiceUnavailableError
from api.schemas.common import InternalEnvelope, TraceId, success_envelope


router = APIRouter(prefix="/internal/v1/health", tags=["internal-health"])


@router.get("/live", response_model=InternalEnvelope)
def live(trace_id: TraceId) -> InternalEnvelope:
    return success_envelope({"status": "UP"}, trace_id)


@router.get("/ready", response_model=InternalEnvelope)
def ready(trace_id: TraceId) -> InternalEnvelope:
    raise ServiceUnavailableError(
        code="AI_LLM_UNAVAILABLE",
        message="Phase 3 skeleton has not initialized AI resources",
        retryable=True,
        status_code=503,
    )
