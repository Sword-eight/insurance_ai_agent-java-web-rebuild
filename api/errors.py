"""只能在 HTTP 边界识别的契约错误。"""


class ContractValidationError(ValueError):
    """multipart 等无法由 FastAPI 自动解析的契约错误。"""
