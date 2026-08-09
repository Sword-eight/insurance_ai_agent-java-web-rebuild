package com.insurance.platform.auth.vo;

/** Successful login projection for the future Phase 9.5 client. */
public record LoginView(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserView user) {

    @Override
    public String toString() {
        return "LoginView[accessToken=<redacted>, tokenType=" + tokenType
                + ", expiresInSeconds=" + expiresInSeconds + ", user=" + user + "]";
    }
}
