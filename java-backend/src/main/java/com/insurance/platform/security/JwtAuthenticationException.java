package com.insurance.platform.security;

/** Internal token validation failure. It never exposes parser details to the public response. */
public class JwtAuthenticationException extends RuntimeException {

    public JwtAuthenticationException(Throwable cause) {
        super("invalid bearer token", cause);
    }
}
