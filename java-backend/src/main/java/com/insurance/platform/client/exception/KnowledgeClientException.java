package com.insurance.platform.client.exception;

/** Knowledge HTTP adapter failure classification; public mapping belongs to DocumentService. */
public class KnowledgeClientException extends RuntimeException {
    public enum Kind { TIMEOUT, UNAVAILABLE, DELIVERY_UNKNOWN, REJECTED, PROTOCOL }

    private final Kind kind;
    private final String internalCode;

    public KnowledgeClientException(Kind kind, String internalCode) {
        this(kind, internalCode, null);
    }

    public KnowledgeClientException(Kind kind, String internalCode, Throwable cause) {
        super("Knowledge client request failed", cause);
        this.kind = kind;
        this.internalCode = internalCode;
    }

    public Kind kind() { return kind; }
    public String internalCode() { return internalCode; }
}
