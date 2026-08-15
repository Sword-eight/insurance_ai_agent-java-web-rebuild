"""Python AI Service 的 FastAPI 最小包装。"""

from contextlib import asynccontextmanager
from collections.abc import AsyncIterator
import re
import time
from typing import Callable

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from api.errors import ContractValidationError
from api.routers import agent, health, knowledge
from api.schemas.common import error_envelope
from application.errors import ApplicationError
from application.rag_engine import (
    RagEngineConfigurationError,
    RagEngineDependencyError,
)
from application.runtime import ApplicationRuntime
from utils.logger import get_logger
from utils.trace_context import bind_trace_id, reset_trace_id


logger = get_logger("api.main")
TRACE_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{16,64}$")


def _init_api_runtime() -> ApplicationRuntime:
    """Defer the model/graph dependency tree until FastAPI lifespan starts."""
    from application.bootstrap import init_api_runtime
    from config import get_rag_engine

    return init_api_runtime(rag_engine=get_rag_engine())


HTTP_STATUS_BY_ERROR_CODE = {
    "AI_VALIDATION_ERROR": 400,
    "KNOWLEDGE_INVALID_DOCUMENT": 400,
    "AI_REQUEST_IN_PROGRESS": 409,
    "AI_REQUEST_CONFLICT": 409,
    "AI_LLM_UNAVAILABLE": 503,
    "AI_LLM_TIMEOUT": 504,
    "AI_TOOL_ERROR": 500,
    "AI_RAG_ERROR": 500,
    "KNOWLEDGE_INDEX_FAILED": 500,
    "AI_INTERNAL_ERROR": 500,
}


def _trace_id(request: Request) -> str:
    trace_id = request.headers.get("X-Trace-Id", "")
    return trace_id if TRACE_ID_PATTERN.fullmatch(trace_id) else "INVALID_TRACE_ID"


def _mark_error(request: Request, error_code: str) -> None:
    request.state.error_code = error_code


def _create_lifespan(
    runtime_factory: Callable[[], ApplicationRuntime],
):
    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        runtime: ApplicationRuntime | None = None
        app.state.runtime = None
        app.state.ai_resources_ready = False
        try:
            runtime = runtime_factory()
            app.state.runtime = runtime
            app.state.ai_resources_ready = True
            logger.info("AI Runtime 初始化完成")
        except (RagEngineConfigurationError, RagEngineDependencyError):
            logger.exception("RAG engine startup configuration failed")
            raise
        except Exception:
            logger.exception("AI Runtime 初始化失败")

        try:
            yield
        finally:
            app.state.ai_resources_ready = False
            app.state.runtime = None
            if runtime is not None:
                try:
                    await runtime.close()
                except Exception:
                    logger.exception("AI Runtime 关闭失败")

    return lifespan


def create_app(
    runtime_factory: Callable[[], ApplicationRuntime] = _init_api_runtime,
) -> FastAPI:
    app = FastAPI(
        title="Insurance AI Service",
        version="0.4.0",
        lifespan=_create_lifespan(runtime_factory),
    )

    @app.middleware("http")
    async def trace_id_response_header(request: Request, call_next):
        trace_id = _trace_id(request)
        token = bind_trace_id(trace_id)
        request.state.error_code = "NONE"
        started = time.monotonic()
        try:
            response = await call_next(request)
            response.headers["X-Trace-Id"] = trace_id
            return response
        finally:
            duration_ms = max(0, int((time.monotonic() - started) * 1000))
            completed_response = locals().get("response")
            status_code = completed_response.status_code if completed_response else 500
            logger.info(
                "event=http_request service=python method=%s path=%s "
                "status=%s durationMs=%s errorCode=%s",
                request.method,
                request.url.path,
                status_code,
                duration_ms,
                request.state.error_code,
            )
            reset_trace_id(token)

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        _mark_error(request, "AI_VALIDATION_ERROR")
        envelope = error_envelope(
            code="AI_VALIDATION_ERROR",
            error_type="VALIDATION",
            message="request does not match the frozen internal contract",
            retryable=False,
            trace_id=_trace_id(request),
        )
        return JSONResponse(status_code=400, content=envelope.model_dump(mode="json"))

    @app.exception_handler(ContractValidationError)
    async def contract_validation_error_handler(
        request: Request, exc: ContractValidationError
    ) -> JSONResponse:
        _mark_error(request, "AI_VALIDATION_ERROR")
        envelope = error_envelope(
            code="AI_VALIDATION_ERROR",
            error_type="VALIDATION",
            message=str(exc),
            retryable=False,
            trace_id=_trace_id(request),
        )
        return JSONResponse(status_code=400, content=envelope.model_dump(mode="json"))

    @app.exception_handler(ApplicationError)
    async def application_error_handler(
        request: Request, exc: ApplicationError
    ) -> JSONResponse:
        _mark_error(request, exc.code)
        envelope = error_envelope(
            code=exc.code,
            error_type=exc.error_type,
            message=exc.safe_message,
            retryable=exc.retryable,
            trace_id=_trace_id(request),
        )
        return JSONResponse(
            status_code=HTTP_STATUS_BY_ERROR_CODE.get(exc.code, 500),
            content=envelope.model_dump(mode="json"),
        )

    @app.exception_handler(Exception)
    async def unexpected_error_handler(
        request: Request, exc: Exception
    ) -> JSONResponse:
        _mark_error(request, "AI_INTERNAL_ERROR")
        logger.exception("未预期的内部 API 异常")
        envelope = error_envelope(
            code="AI_INTERNAL_ERROR",
            error_type="INTERNAL",
            message="internal AI service error",
            retryable=False,
            trace_id=_trace_id(request),
        )
        return JSONResponse(
            status_code=500,
            content=envelope.model_dump(mode="json"),
        )

    app.include_router(agent.router)
    app.include_router(knowledge.router)
    app.include_router(health.router)
    return app


app = create_app()
