"""消息截断纯函数的测试。"""

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from graph.nodes import safe_truncate_messages


def _tool_call(call_id: str) -> dict:
    return {
        "name": "echo_tool",
        "args": {"text": call_id},
        "id": call_id,
        "type": "tool_call",
    }


def test_safe_truncate_keeps_messages_when_under_limit() -> None:
    messages = [
        HumanMessage(content="问题"),
        AIMessage(content="回答"),
    ]

    result = safe_truncate_messages(messages, max_rounds=2)

    assert result == messages


def test_safe_truncate_keeps_latest_round() -> None:
    messages = [
        HumanMessage(content="第一问"),
        AIMessage(content="第一答"),
        HumanMessage(content="第二问"),
        AIMessage(content="第二答"),
    ]

    result = safe_truncate_messages(messages, max_rounds=1)

    assert [message.content for message in result] == ["第二问", "第二答"]


def test_safe_truncate_preserves_tool_message_pair() -> None:
    messages = [
        HumanMessage(content="旧问题"),
        AIMessage(content="旧回答"),
        HumanMessage(content="调用工具"),
        AIMessage(content="", tool_calls=[_tool_call("call-1")]),
        ToolMessage(content="工具结果", tool_call_id="call-1"),
        AIMessage(content="最终回答"),
    ]

    result = safe_truncate_messages(messages, max_rounds=1)

    assert len(result) == 4
    assert isinstance(result[0], HumanMessage)
    assert isinstance(result[1], AIMessage)
    assert result[1].tool_calls[0]["id"] == "call-1"
    assert isinstance(result[2], ToolMessage)
    assert result[2].tool_call_id == "call-1"
    assert result[3].content == "最终回答"


def test_safe_truncate_drops_orphan_tool_message() -> None:
    messages = [
        ToolMessage(content="孤儿结果", tool_call_id="missing"),
        HumanMessage(content="新问题"),
        AIMessage(content="新回答"),
    ]

    result = safe_truncate_messages(messages, max_rounds=2)

    assert all(not isinstance(message, ToolMessage) for message in result)
    assert [message.content for message in result] == ["新问题", "新回答"]
