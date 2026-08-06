"""容量受限、线程安全的单进程 requestId 短期登记表。"""

from collections import OrderedDict
from dataclasses import dataclass
from threading import Lock
import time
from typing import Callable, Generic, Literal, TypeVar
from uuid import UUID, uuid4

from application.errors import (
    ApplicationError,
    registry_capacity_error,
    request_conflict_error,
    request_in_progress_error,
)


T = TypeVar("T")


@dataclass(frozen=True)
class CachedError:
    code: str
    error_type: Literal["VALIDATION", "CONFLICT", "DEPENDENCY", "INTERNAL"]
    message: str
    retryable: bool

    @classmethod
    def from_exception(cls, error: ApplicationError) -> "CachedError":
        return cls(
            code=error.code,
            error_type=error.error_type,
            message=error.safe_message,
            retryable=error.retryable,
        )

    def to_exception(self) -> ApplicationError:
        return ApplicationError(
            code=self.code,
            error_type=self.error_type,
            message=self.message,
            retryable=self.retryable,
        )


@dataclass
class _Entry(Generic[T]):
    digest: str
    state: Literal["IN_PROGRESS", "SUCCEEDED", "FAILED"]
    execution_token: UUID
    result: T | None = None
    error: CachedError | None = None
    expires_at: float | None = None


@dataclass(frozen=True)
class Registration(Generic[T]):
    should_execute: bool
    cached_result: T | None = None
    execution_token: UUID | None = None


class RequestRegistry(Generic[T]):
    """MySQL 之外的单实例防重层；不承担长期业务事实。"""

    def __init__(
        self,
        *,
        ttl_seconds: float = 600.0,
        capacity: int = 1024,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        if ttl_seconds <= 0:
            raise ValueError("ttl_seconds must be positive")
        if capacity <= 0:
            raise ValueError("capacity must be positive")
        self._ttl_seconds = ttl_seconds
        self._capacity = capacity
        self._clock = clock
        self._entries: OrderedDict[UUID, _Entry[T]] = OrderedDict()
        self._lock = Lock()

    def begin(self, request_id: UUID, digest: str) -> Registration[T]:
        with self._lock:
            now = self._clock()
            self._purge_expired(now)
            entry = self._entries.get(request_id)
            if entry is not None:
                if entry.digest != digest:
                    raise request_conflict_error()
                self._entries.move_to_end(request_id)
                if entry.state == "IN_PROGRESS":
                    raise request_in_progress_error()
                if entry.state == "FAILED":
                    if entry.error is None:
                        raise registry_capacity_error()
                    raise entry.error.to_exception()
                return Registration(should_execute=False, cached_result=entry.result)

            self._make_room(now)
            execution_token = uuid4()
            self._entries[request_id] = _Entry(
                digest=digest,
                state="IN_PROGRESS",
                execution_token=execution_token,
                expires_at=now + self._ttl_seconds,
            )
            return Registration(
                should_execute=True,
                execution_token=execution_token,
            )

    def succeed(
        self,
        request_id: UUID,
        digest: str,
        execution_token: UUID,
        result: T,
    ) -> None:
        with self._lock:
            entry = self._require_active_entry(
                request_id,
                digest,
                execution_token,
            )
            entry.state = "SUCCEEDED"
            entry.result = result
            entry.error = None
            entry.expires_at = self._clock() + self._ttl_seconds
            self._entries.move_to_end(request_id)

    def fail(
        self,
        request_id: UUID,
        digest: str,
        execution_token: UUID,
        error: ApplicationError,
    ) -> None:
        with self._lock:
            entry = self._require_active_entry(
                request_id,
                digest,
                execution_token,
            )
            entry.state = "FAILED"
            entry.result = None
            entry.error = CachedError.from_exception(error)
            entry.expires_at = self._clock() + self._ttl_seconds
            self._entries.move_to_end(request_id)

    def _require_active_entry(
        self,
        request_id: UUID,
        digest: str,
        execution_token: UUID,
    ) -> _Entry[T]:
        entry = self._entries.get(request_id)
        if (
            entry is None
            or entry.digest != digest
            or entry.execution_token != execution_token
            or entry.state != "IN_PROGRESS"
        ):
            raise RuntimeError("request registry transition is invalid")
        return entry

    def _purge_expired(self, now: float) -> None:
        expired = [
            request_id
            for request_id, entry in self._entries.items()
            if entry.expires_at is not None
            and entry.expires_at <= now
        ]
        for request_id in expired:
            self._entries.pop(request_id, None)

    def _make_room(self, now: float) -> None:
        self._purge_expired(now)
        while len(self._entries) >= self._capacity:
            completed_id = next(
                (
                    request_id
                    for request_id, entry in self._entries.items()
                    if entry.state != "IN_PROGRESS"
                ),
                None,
            )
            if completed_id is None:
                raise registry_capacity_error()
            self._entries.pop(completed_id)
