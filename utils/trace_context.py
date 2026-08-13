"""Request-local trace context shared by API and application logging."""

from contextvars import ContextVar, Token


_TRACE_ID: ContextVar[str] = ContextVar("trace_id", default="NONE")


def bind_trace_id(trace_id: str) -> Token[str]:
    return _TRACE_ID.set(trace_id)


def reset_trace_id(token: Token[str]) -> None:
    _TRACE_ID.reset(token)


def current_trace_id() -> str:
    return _TRACE_ID.get()
