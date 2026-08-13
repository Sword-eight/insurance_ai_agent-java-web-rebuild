"""Deterministic FastAPI runtime for Phase 12 browser and boundary E2E.

This runtime exercises the real FastAPI routers, facades, envelopes and file
validation without claiming an online DeepSeek or BGE model call.
"""

import os
from pathlib import Path
import time

from langchain_core.messages import AIMessage

from api.main import create_app
from application.agent_facade import DefaultAgentFacade
from application.knowledge_facade import DefaultKnowledgeFacade
from application.request_registry import RequestRegistry
from application.runtime import ApplicationRuntime
from application.errors import runtime_unavailable_error


class Phase12GraphBuilder:
    def invoke(self, **kwargs):
        if kwargs["user_message"] == "PHASE12_TIMEOUT":
            time.sleep(2)
        if kwargs["user_message"] == "PHASE12_UNAVAILABLE":
            raise runtime_unavailable_error()
        return {"messages": [AIMessage(content=f"Phase 12 E2E：{kwargs['user_message']}")]}


class Phase12KnowledgeService:
    def __init__(self) -> None:
        self._document_count = 0

    def rebuild(self):
        self._document_count += 1
        return {"success": True, "document_count": self._document_count}

    def get_stats(self):
        return {
            "index_loaded": self._document_count > 0,
            "index_exists": self._document_count > 0,
            "rag_engine": "phase12-test-double",
            "pdf_count": self._document_count,
        }


def create_phase12_runtime() -> ApplicationRuntime:
    document_dir_value = os.environ.get("PHASE12_PYTHON_DOCUMENT_DIR")
    if not document_dir_value:
        raise RuntimeError("PHASE12_PYTHON_DOCUMENT_DIR is required")
    document_dir = Path(document_dir_value).resolve()
    knowledge_service = Phase12KnowledgeService()
    return ApplicationRuntime(
        agent_facade=DefaultAgentFacade(
            graph_builder=Phase12GraphBuilder(),
            registry=RequestRegistry(),
        ),
        knowledge_facade=DefaultKnowledgeFacade(
            knowledge_service=knowledge_service,
            document_dir=document_dir,
        ),
    )


app = create_app(runtime_factory=create_phase12_runtime)
