"""Python AI Service 的 Phase 3 FastAPI Skeleton。"""

from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from api.errors import ContractValidationError, ServiceUnavailableError
from api.routers import agent, health, knowledge
from api.schemas.common import error_envelope


def _trace_id(request: Request) -> str:
    return request.headers.get("X-Trace-Id", "INVALID_TRACE_ID")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Phase 4 才在此调用 bootstrap；Skeleton 不加载昂贵资源。"""

    app.state.ai_resources_ready = False
    yield


def create_app() -> FastAPI:
    app = FastAPI(
        title="Insurance AI Service",
        version="0.3.0-skeleton",
        lifespan=lifespan,
    )

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

    @app.exception_handler(ServiceUnavailableError)
    async def unavailable_handler(
        request: Request, exc: ServiceUnavailableError
    ) -> JSONResponse:
        error_type = "DEPENDENCY" if exc.status_code == 503 else "INTERNAL"
        envelope = error_envelope(
            code=exc.code,
            error_type=error_type,
            message=exc.message,
            retryable=exc.retryable,
            trace_id=_trace_id(request),
        )
        return JSONResponse(
            status_code=exc.status_code,
            content=envelope.model_dump(mode="json"),
        )

    app.include_router(agent.router)
    app.include_router(knowledge.router)
    app.include_router(health.router)
    return app


app = create_app()
