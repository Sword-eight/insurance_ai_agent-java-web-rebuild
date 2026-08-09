package com.insurance.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.platform.common.api.ApiResponse;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.trace.TraceIdContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Writes the same stable public Envelope for every authentication failure. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        response.setStatus(ErrorCode.AUTH_UNAUTHORIZED.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(
                        ErrorCode.AUTH_UNAUTHORIZED,
                        null,
                        TraceIdContext.currentTraceId()));
    }
}
