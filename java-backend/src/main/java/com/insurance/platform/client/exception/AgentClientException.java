package com.insurance.platform.client.exception;

/** Agent HTTP 适配器的稳定失败分类；不直接承担公共 HTTP 映射。 */
public class AgentClientException extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        UNAVAILABLE,
        REJECTED,
        PROTOCOL
    }

    private final Kind kind;
    private final String internalCode;

    public AgentClientException(Kind kind, String internalCode) {
        this(kind, internalCode, null);
    }

    public AgentClientException(Kind kind, String internalCode, Throwable cause) {
        super("Agent client request failed", cause);
        this.kind = kind;
        this.internalCode = internalCode;
    }

    public Kind kind() {
        return kind;
    }

    public String internalCode() {
        return internalCode;
    }
}
