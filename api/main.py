"""Python AI Service 的 FastAPI 最小包装。"""

from contextlib import asynccontextmanager
from collections.abc import AsyncIterator
from typing import Callable

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from api.errors import ContractValidationError
from api.routers import agent, health, knowledge
from api.schemas.common import error_envelope
from application.errors import ApplicationError
from application.runtime import ApplicationRuntime
from utils.logger import get_logger


logger = get_logger("api.main")


def _init_api_runtime() -> ApplicationRuntime:
    """Defer the model/graph dependency tree until FastAPI lifespan starts."""
    from application.bootstrap import init_api_runtime

    return init_api_runtime()


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
    return request.headers.get("X-Trace-Id", "INVALID_TRACE_ID")


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
        response = await call_next(request)
        response.headers["X-Trace-Id"] = _trace_id(request)
        return response

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
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
