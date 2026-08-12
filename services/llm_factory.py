"""DeepSeek chat model construction with the frozen Phase 11 timeout policy."""

import httpx
from langchain_openai import ChatOpenAI

from config import LLM_CONFIG


def create_default_llm() -> ChatOpenAI:
    """Create the default model without enabling SDK-level automatic retries."""
    return ChatOpenAI(
        model=LLM_CONFIG["model"],
        base_url=LLM_CONFIG["base_url"],
        api_key=LLM_CONFIG["api_key"],
        temperature=LLM_CONFIG["temperature"],
        max_tokens=LLM_CONFIG["max_tokens"],
        timeout=httpx.Timeout(
            LLM_CONFIG["read_timeout_seconds"],
            connect=LLM_CONFIG["connect_timeout_seconds"],
        ),
        max_retries=LLM_CONFIG["max_retries"],
    )
