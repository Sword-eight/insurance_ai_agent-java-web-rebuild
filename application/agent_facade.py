"""同步聊天 Application Facade。"""

import hashlib
import json
import time
from typing import Any, Callable
from uuid import UUID

from langchain_core.messages import AIMessage, HumanMessage

from application.errors import (
    ApplicationError,
    agent_execution_error,
    llm_timeout_error,
)
from application.facades import AgentChatCommand, AgentChatResult
from application.request_registry import RequestRegistry


class DefaultAgentFacade:
    """把冻结 HTTP 命令转换为现有 LangGraph 调用。"""

    def __init__(
        self,
        *,
        graph_builder: Any,
        registry: RequestRegistry[AgentChatResult],
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self._graph_builder = graph_builder
        self._registry = registry
        self._clock = clock

    def chat(self, command: AgentChatCommand) -> AgentChatResult:
        started_at = self._clock()
        digest = self._digest(command)
        registration = self._registry.begin(command.request_id, digest)
        if not registration.should_execute:
            if registration.cached_result is None:
                raise agent_execution_error()
            return registration.cached_result
        execution_token = registration.execution_token
        if execution_token is None:
            raise agent_execution_error()

        try:
            history_messages = [
                HumanMessage(content=item.content)
                if item.role == "user"
                else AIMessage(content=item.content)
                for item in command.history
            ]
            raw_result = self._graph_builder.invoke(
                user_message=command.message,
                session_id=str(command.session_id),
                history_messages=history_messages,
                execution_id=str(command.request_id),
            )
            answer = self._extract_answer(raw_result)
            result = AgentChatResult(
                request_id=command.request_id,
                answer=answer,
                sources=self._extract_sources(raw_result),
                duration_ms=max(0, round((self._clock() - started_at) * 1000)),
            )
        except ApplicationError as error:
            self._cache_failure(
                command.request_id,
                digest,
                execution_token,
                error,
            )
            raise
        except TimeoutError as exc:
            error = llm_timeout_error()
            self._cache_failure(
                command.request_id,
                digest,
                execution_token,
                error,
            )
            raise error from exc
        except Exception as exc:
            error = agent_execution_error()
            self._cache_failure(
                command.request_id,
                digest,
                execution_token,
                error,
            )
            raise error from exc

        try:
            self._registry.succeed(
                command.request_id,
                digest,
                execution_token,
                result,
            )
        except RuntimeError as exc:
            raise agent_execution_error() from exc
        return result

    def _cache_failure(
        self,
        request_id: UUID,
        digest: str,
        execution_token: UUID,
        error: ApplicationError,
    ) -> None:
        try:
            self._registry.fail(
                request_id,
                digest,
                execution_token,
                error,
            )
        except RuntimeError:
            # A newer lease may own this requestId after the 10-minute TTL.
            # Never let the stale execution overwrite that newer state.
            return

    @staticmethod
    def _extract_answer(raw_result: Any) -> str:
        if not isinstance(raw_result, dict):
            raise agent_execution_error()
        for message in reversed(raw_result.get("messages", [])):
            if isinstance(message, AIMessage):
                if isinstance(message.content, str) and message.content.strip():
                    return message.content.strip()
        raise agent_execution_error()

    @staticmethod
    def _extract_sources(raw_result: Any) -> tuple[dict[str, object], ...]:
        if not isinstance(raw_result, dict):
            raise agent_execution_error()
        sources: list[dict[str, object]] = []
        seen: set[tuple[str, int | None, str]] = set()
        for item in raw_result.get("retrieved_docs", []):
            if not isinstance(item, dict):
                continue
            document_name = item.get("source_name")
            content = item.get("content")
            if not isinstance(document_name, str) or not document_name.strip():
                continue
            if not isinstance(content, str) or not content.strip():
                continue
            raw_page = item.get("source_page")
            page = raw_page if isinstance(raw_page, int) and raw_page > 0 else None
            snippet = content.strip()[:500]
            key = (document_name.strip(), page, snippet)
            if key in seen:
                continue
            seen.add(key)
            raw_score = item.get("similarity_score")
            score = float(raw_score) if isinstance(raw_score, (int, float)) else None
            sources.append({
                "documentName": document_name.strip(),
                "page": page,
                "snippet": snippet,
                "score": score,
            })
        return tuple(sources)

    @staticmethod
    def _digest(command: AgentChatCommand) -> str:
        canonical = {
            "sessionId": str(command.session_id),
            "message": command.message,
            "history": [
                {"role": item.role, "content": item.content}
                for item in command.history
            ],
        }
        payload = json.dumps(
            canonical,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
        return hashlib.sha256(payload).hexdigest()
