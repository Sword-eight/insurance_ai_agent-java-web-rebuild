"""RAG engine startup configuration errors."""


class RagEngineConfigurationError(ValueError):
    """The configured startup engine is not supported."""


class RagEngineDependencyError(RuntimeError):
    """The selected engine cannot start because optional packages are missing."""
