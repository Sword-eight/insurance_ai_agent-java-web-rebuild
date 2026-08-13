package com.insurance.platform.security;

import java.util.UUID;

/** Framework-neutral port for issuing and verifying Phase 9 access tokens. */
public interface JwtTokenService {

    IssuedJwt issue(UUID userId);

    JwtPrincipal verify(String token);
}
