"""内部 API Envelope 与 TraceId 契约。"""

from typing import Annotated, Any, Literal

from fastapi import Header
from pydantic import BaseModel, ConfigDict, model_validator


TraceId = Annotated[
    str,
    Header(
        alias="X-Trace-Id",
        min_length=16,
        max_length=64,
        pattern=r"^[A-Za-z0-9_-]+$",
    ),
]


class InternalError(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str
    type: Literal["VALIDATION", "CONFLICT", "DEPENDENCY", "INTERNAL"]
    message: str
    retryable: bool


class InternalEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid")

    success: bool
    data: Any | None = None
    error: InternalError | None = None
    traceId: str

    @model_validator(mode="after")
    def validate_success_error_shape(self) -> "InternalEnvelope":
        if self.success and self.error is not None:
            raise ValueError("successful envelope must not contain error")
        if not self.success and self.error is None:
            raise ValueError("failed envelope must contain error")
        return self


def success_envelope(data: Any, trace_id: str) -> InternalEnvelope:
    return InternalEnvelope(success=True, data=data, traceId=trace_id)


def error_envelope(
    *,
    code: str,
    error_type: Literal["VALIDATION", "CONFLICT", "DEPENDENCY", "INTERNAL"],
    message: str,
    retryable: bool,
    trace_id: str,
) -> InternalEnvelope:
    return InternalEnvelope(
        success=False,
        error=InternalError(
            code=code,
            type=error_type,
            message=message,
            retryable=retryable,
        ),
        traceId=trace_id,
    )
