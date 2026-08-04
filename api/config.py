"""FastAPI Skeleton 的最小、无敏感信息配置。"""

from dataclasses import dataclass
import os


@dataclass(frozen=True)
class ApiSettings:
    """进程启动配置；不包含 DeepSeek、模型或索引初始化。"""

    host: str = "127.0.0.1"
    port: int = 8000

    @classmethod
    def from_env(cls) -> "ApiSettings":
        port_text = os.getenv("AI_SERVICE_PORT", "8000")
        try:
            port = int(port_text)
        except ValueError as exc:
            raise ValueError("AI_SERVICE_PORT must be an integer") from exc
        if not 1 <= port <= 65535:
            raise ValueError("AI_SERVICE_PORT must be between 1 and 65535")
        return cls(
            host=os.getenv("AI_SERVICE_HOST", "127.0.0.1"),
            port=port,
        )
