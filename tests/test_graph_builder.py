"""使用 Mock LLM/Tool 的最小 LangGraph 循环测试。"""

from typing import Any

from langchain_core.messages import AIMessage, ToolMessage

from graph.graph_builder import AgentGraphBuilder


class RecordingLLM:
    model_name = "offline-mock-llm"

    def __init__(self) -> None:
        self.bound_tools: list[Any] = []
        self.invocations: list[list[Any]] = []
        self._responses = [
            AIMessage(
                content="",
                tool_calls=[
                    {
                        "name": "echo_tool",
                        "args": {"text": "hello"},
                        "id": "call-1",
                        "type": "tool_call",
                    }
                ],
            ),
            AIMessage(content="最终回答"),
        ]

    def bind_tools(self, tools: list[Any]) -> "RecordingLLM":
        self.bound_tools = tools
        return self

    def invoke(self, messages: list[Any]) -> AIMessage:
        self.invocations.append(messages)
        return self._responses.pop(0)


class RecordingTool:
    name = "echo_tool"

    def __init__(self) -> None:
        self.calls: list[dict[str, Any]] = []

    def _run(self, text: str) -> str:
        self.calls.append({"text": text})
        return f"echo:{text}"


def test_graph_runs_agent_tool_agent_loop_without_external_services() -> None:
    llm = RecordingLLM()
    tool = RecordingTool()
    builder = AgentGraphBuilder(
        tools=[tool],
        llm=llm,
        system_prompt="离线测试",
        max_context_rounds=2,
    )

    result = builder.invoke(
        user_message="调用 echo",
        session_id="offline-graph-test",
    )

    assert llm.bound_tools == [tool]
    assert len(llm.invocations) == 2
    assert tool.calls == [{"text": "hello"}]
    assert any(
        isinstance(message, ToolMessage)
        and message.tool_call_id == "call-1"
        and message.content == "echo:hello"
        for message in result["messages"]
    )
    assert result["messages"][-1].content == "最终回答"
