"""内部 HTTP 边界使用的稳定错误类型。"""


class ServiceUnavailableError(RuntimeError):
    """Skeleton 尚未接入真实 Facade 时的显式失败。"""

    def __init__(
        self,
        *,
        code: str,
        message: str,
        retryable: bool,
        status_code: int,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retryable = retryable
        self.status_code = status_code


class ContractValidationError(ValueError):
    """multipart 等无法由 FastAPI 自动解析的契约错误。"""
