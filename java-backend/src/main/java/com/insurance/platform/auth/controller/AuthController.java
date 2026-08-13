package com.insurance.platform.auth.controller;

import com.insurance.platform.auth.dto.LoginRequest;
import com.insurance.platform.auth.dto.RegisterRequest;
import com.insurance.platform.auth.service.AuthService;
import com.insurance.platform.auth.vo.LoginView;
import com.insurance.platform.auth.vo.UserView;
import com.insurance.platform.common.api.ApiResponse;
import com.insurance.platform.common.trace.TraceIdContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication HTTP adapter. It delegates all user and token rules to AuthService.
 */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(
                authService.register(request), TraceIdContext.currentTraceId());
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<LoginView> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(
                authService.login(request), TraceIdContext.currentTraceId());
    }
}
