package com.insurance.platform.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Java 公共入口 TraceId 生命周期挂载点。
 *
 * <p>单例 Filter 不保存请求字段；TraceId 只存在 request、response 与当前线程 MDC 中。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceIdContext.normalizeOrGenerate(
                request.getHeader(TraceIdContext.HEADER_NAME));
        request.setAttribute(TraceIdContext.REQUEST_ATTRIBUTE, traceId);
        response.setHeader(TraceIdContext.HEADER_NAME, traceId);
        MDC.put(TraceIdContext.MDC_KEY, traceId);
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            Object errorCode = request.getAttribute(TraceIdContext.ERROR_CODE_ATTRIBUTE);
            logger.info(
                    "event=http_request service=java method={} path={} status={} durationMs={} errorCode={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    Math.max(0, (System.nanoTime() - started) / 1_000_000),
                    errorCode == null ? "NONE" : errorCode);
            MDC.remove(TraceIdContext.MDC_KEY);
        }
    }
}
