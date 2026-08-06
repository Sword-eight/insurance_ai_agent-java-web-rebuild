from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4

import pytest

from application.errors import ApplicationError, agent_execution_error
from application.request_registry import RequestRegistry


class MutableClock:
    def __init__(self) -> None:
        self.value = 0.0

    def __call__(self) -> float:
        return self.value


def test_registry_reuses_completed_result_without_reexecution() -> None:
    registry: RequestRegistry[str] = RequestRegistry()
    request_id = uuid4()

    first = registry.begin(request_id, "digest")
    assert first.execution_token is not None
    registry.succeed(request_id, "digest", first.execution_token, "answer")
    second = registry.begin(request_id, "digest")

    assert first.should_execute is True
    assert second.should_execute is False
    assert second.cached_result == "answer"


def test_registry_rejects_request_id_with_different_digest() -> None:
    registry: RequestRegistry[str] = RequestRegistry()
    request_id = uuid4()
    registry.begin(request_id, "first")

    with pytest.raises(ApplicationError) as captured:
        registry.begin(request_id, "second")

    assert captured.value.code == "AI_REQUEST_CONFLICT"


def test_registry_rejects_duplicate_while_in_progress() -> None:
    registry: RequestRegistry[str] = RequestRegistry()
    request_id = uuid4()
    registry.begin(request_id, "digest")

    with pytest.raises(ApplicationError) as captured:
        registry.begin(request_id, "digest")

    assert captured.value.code == "AI_REQUEST_IN_PROGRESS"
    assert captured.value.retryable is True


def test_registry_replays_cached_failure_without_reexecution() -> None:
    registry: RequestRegistry[str] = RequestRegistry()
    request_id = uuid4()
    registration = registry.begin(request_id, "digest")
    assert registration.execution_token is not None
    registry.fail(
        request_id,
        "digest",
        registration.execution_token,
        agent_execution_error(),
    )

    with pytest.raises(ApplicationError) as captured:
        registry.begin(request_id, "digest")

    assert captured.value.code == "AI_INTERNAL_ERROR"
    assert captured.value.safe_message == "agent execution failed"


def test_registry_allows_new_execution_after_completed_entry_expires() -> None:
    clock = MutableClock()
    registry: RequestRegistry[str] = RequestRegistry(
        ttl_seconds=10,
        clock=clock,
    )
    request_id = uuid4()
    registration = registry.begin(request_id, "digest")
    assert registration.execution_token is not None
    registry.succeed(
        request_id,
        "digest",
        registration.execution_token,
        "answer",
    )

    clock.value = 10.0

    assert registry.begin(request_id, "digest").should_execute is True


def test_registry_evicts_oldest_completed_entry_when_capacity_is_full() -> None:
    registry: RequestRegistry[str] = RequestRegistry(capacity=1)
    first_id = uuid4()
    second_id = uuid4()
    first = registry.begin(first_id, "first")
    assert first.execution_token is not None
    registry.succeed(first_id, "first", first.execution_token, "answer")

    second = registry.begin(second_id, "second")
    assert second.should_execute is True
    assert second.execution_token is not None
    registry.succeed(second_id, "second", second.execution_token, "answer-2")
    assert registry.begin(first_id, "first").should_execute is True


def test_registry_allows_recovery_after_inprogress_entry_expires() -> None:
    clock = MutableClock()
    registry: RequestRegistry[str] = RequestRegistry(
        ttl_seconds=10,
        clock=clock,
    )
    request_id = uuid4()
    registry.begin(request_id, "digest")

    clock.value = 10.0

    assert registry.begin(request_id, "digest").should_execute is True


def test_expired_execution_cannot_overwrite_newer_lease() -> None:
    clock = MutableClock()
    registry: RequestRegistry[str] = RequestRegistry(
        ttl_seconds=10,
        clock=clock,
    )
    request_id = uuid4()
    stale = registry.begin(request_id, "digest")
    assert stale.execution_token is not None
    clock.value = 10.0
    current = registry.begin(request_id, "digest")
    assert current.execution_token is not None

    with pytest.raises(RuntimeError):
        registry.succeed(
            request_id,
            "digest",
            stale.execution_token,
            "stale-answer",
        )

    registry.succeed(
        request_id,
        "digest",
        current.execution_token,
        "current-answer",
    )
    cached = registry.begin(request_id, "digest")
    assert cached.cached_result == "current-answer"


def test_registry_does_not_evict_in_progress_entry() -> None:
    registry: RequestRegistry[str] = RequestRegistry(capacity=1)
    registry.begin(uuid4(), "first")

    with pytest.raises(ApplicationError) as captured:
        registry.begin(uuid4(), "second")

    assert captured.value.safe_message == "request registry capacity is exhausted"


def test_registry_begin_is_atomic_under_concurrent_duplicates() -> None:
    registry: RequestRegistry[str] = RequestRegistry()
    request_id = uuid4()

    def begin_once() -> str:
        try:
            registration = registry.begin(request_id, "digest")
            return "execute" if registration.should_execute else "cached"
        except ApplicationError as error:
            return error.code

    with ThreadPoolExecutor(max_workers=8) as pool:
        outcomes = list(pool.map(lambda _: begin_once(), range(8)))

    assert outcomes.count("execute") == 1
    assert outcomes.count("AI_REQUEST_IN_PROGRESS") == 7
