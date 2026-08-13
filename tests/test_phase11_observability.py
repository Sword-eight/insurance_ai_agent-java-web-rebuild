import logging
from unittest.mock import patch

import httpx
from fastapi.testclient import TestClient

from api.main import create_app
from config import LLM_CONFIG
from services.llm_factory import create_default_llm
from utils.trace_context import current_trace_id


TRACE_ID = "01J4PHASE11TRACE01"


def _failing_factory():
    raise RuntimeError("runtime deliberately unavailable")


def test_request_log_carries_trace_and_context_is_reset(caplog) -> None:
    app = create_app(runtime_factory=_failing_factory)
    with caplog.at_level(logging.INFO, logger="insurance_ai_agent.api.main"):
        with TestClient(app) as client:
            response = client.get(
                "/internal/v1/health/live", headers={"X-Trace-Id": TRACE_ID}
            )

    assert response.status_code == 200
    request_record = next(
        record for record in caplog.records if "event=http_request" in record.message
    )
    assert request_record.trace_id == TRACE_ID
    assert "errorCode=NONE" in request_record.message
    assert current_trace_id() == "NONE"


def test_invalid_trace_is_not_copied_into_log_context(caplog) -> None:
    app = create_app(runtime_factory=_failing_factory)
    with caplog.at_level(logging.INFO, logger="insurance_ai_agent.api.main"):
        with TestClient(app) as client:
            response = client.get("/internal/v1/health/live")

    assert response.status_code == 400
    request_record = next(
        record for record in caplog.records if "event=http_request" in record.message
    )
    assert request_record.trace_id == "INVALID_TRACE_ID"
    assert "errorCode=AI_VALIDATION_ERROR" in request_record.message


def test_default_llm_uses_frozen_connect_read_timeout_and_zero_retry() -> None:
    with patch("services.llm_factory.ChatOpenAI") as constructor:
        create_default_llm()

    arguments = constructor.call_args.kwargs
    timeout = arguments["timeout"]
    assert isinstance(timeout, httpx.Timeout)
    assert timeout.connect == 3.0
    assert timeout.read == 50.0
    assert arguments["max_retries"] == 0
    assert LLM_CONFIG["max_retries"] == 0
