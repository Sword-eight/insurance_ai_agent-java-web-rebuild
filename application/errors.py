"""Application 层稳定错误；不依赖 FastAPI 或 HTTP 类型。"""

from typing import Literal


ErrorType = Literal["VALIDATION", "CONFLICT", "DEPENDENCY", "INTERNAL"]


class ApplicationError(RuntimeError):
    """可安全映射到冻结内部错误 Envelope 的应用异常。"""

    def __init__(
        self,
        *,
        code: str,
        error_type: ErrorType,
        message: str,
        retryable: bool,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.error_type = error_type
        self.safe_message = message
        self.retryable = retryable


def request_in_progress_error() -> ApplicationError:
    return ApplicationError(
        code="AI_REQUEST_IN_PROGRESS",
        error_type="CONFLICT",
        message="request is already in progress",
        retryable=True,
    )


def request_conflict_error() -> ApplicationError:
    return ApplicationError(
        code="AI_REQUEST_CONFLICT",
        error_type="CONFLICT",
        message="requestId was reused with different content",
        retryable=False,
    )


def runtime_unavailable_error() -> ApplicationError:
    return ApplicationError(
        code="AI_LLM_UNAVAILABLE",
        error_type="DEPENDENCY",
        message="AI runtime is not ready",
        retryable=True,
    )


def llm_timeout_error() -> ApplicationError:
    return ApplicationError(
        code="AI_LLM_TIMEOUT",
        error_type="DEPENDENCY",
        message="LLM execution timed out",
        retryable=False,
    )


def agent_execution_error() -> ApplicationError:
    return ApplicationError(
        code="AI_INTERNAL_ERROR",
        error_type="INTERNAL",
        message="agent execution failed",
        retryable=False,
    )


def knowledge_operation_deferred_error() -> ApplicationError:
    return ApplicationError(
        code="KNOWLEDGE_INDEX_FAILED",
        error_type="INTERNAL",
        message="knowledge write operations are not enabled in Phase 4",
        retryable=False,
    )


def knowledge_status_error() -> ApplicationError:
    return ApplicationError(
        code="AI_RAG_ERROR",
        error_type="INTERNAL",
        message="knowledge status query failed",
        retryable=False,
    )


def registry_capacity_error() -> ApplicationError:
    return ApplicationError(
        code="AI_INTERNAL_ERROR",
        error_type="INTERNAL",
        message="request registry capacity is exhausted",
        retryable=False,
    )
