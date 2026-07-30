"""Agent Router 三个分支的单元测试。"""

from langchain_core.messages import AIMessage, HumanMessage

from graph.router import route_after_agent


def test_router_routes_to_tools_when_tool_calls_exist() -> None:
    message = AIMessage(
        content="",
        tool_calls=[
            {
                "name": "echo_tool",
                "args": {"text": "hello"},
                "id": "call-1",
                "type": "tool_call",
            }
        ],
    )

    assert route_after_agent({"messages": [message]}) == "tools"


def test_router_ends_when_last_message_has_no_tool_calls() -> None:
    assert route_after_agent(
        {"messages": [HumanMessage(content="直接回答")]}
    ) == "__end__"


def test_router_ends_when_messages_are_empty() -> None:
    assert route_after_agent({"messages": []}) == "__end__"
