package com.insurance.platform.common.trace;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * TraceId 在 Header、request attribute 与 MDC 中使用的稳定键。
 */
public final class TraceIdContext {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    public static final String REQUEST_ATTRIBUTE =
            TraceIdContext.class.getName() + ".traceId";
    public static final String ERROR_CODE_ATTRIBUTE =
            TraceIdContext.class.getName() + ".errorCode";
    private static final Pattern VALID_TRACE_ID =
            Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    private TraceIdContext() {
    }

    public static boolean isValid(String candidate) {
        return candidate != null && VALID_TRACE_ID.matcher(candidate).matches();
    }

    public static String normalizeOrGenerate(String candidate) {
        return isValid(candidate) ? candidate : generate();
    }

    public static String currentTraceId() {
        String current = MDC.get(MDC_KEY);
        return isValid(current) ? current : generate();
    }

    public static void markErrorCode(String errorCode) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null && errorCode != null) {
            attributes.setAttribute(ERROR_CODE_ATTRIBUTE, errorCode, RequestAttributes.SCOPE_REQUEST);
        }
    }

    private static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
