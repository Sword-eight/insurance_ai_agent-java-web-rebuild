"""Phase 6 两进程 smoke 使用的 FastAPI 测试应用，不访问模型或索引。"""

from langchain_core.messages import AIMessage

from application.agent_facade import DefaultAgentFacade
from application.knowledge_facade import DefaultKnowledgeFacade
from application.request_registry import RequestRegistry
from application.runtime import ApplicationRuntime
from api.main import create_app


class SmokeGraphBuilder:
    def invoke(self, **kwargs):
        return {"messages": [AIMessage(content=f"smoke:{kwargs['user_message']}")]}


class SmokeKnowledgeService:
    def get_stats(self):
        return {
            "index_loaded": False,
            "index_exists": False,
            "rag_engine": "langchain",
        }


def create_smoke_runtime() -> ApplicationRuntime:
    return ApplicationRuntime(
        agent_facade=DefaultAgentFacade(
            graph_builder=SmokeGraphBuilder(),
            registry=RequestRegistry(),
        ),
        knowledge_facade=DefaultKnowledgeFacade(
            knowledge_service=SmokeKnowledgeService()
        ),
    )


app = create_app(runtime_factory=create_smoke_runtime)
