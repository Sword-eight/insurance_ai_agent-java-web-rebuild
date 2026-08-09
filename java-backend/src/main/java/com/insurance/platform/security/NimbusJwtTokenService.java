package com.insurance.platform.security;

import com.insurance.platform.config.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class NimbusJwtTokenService implements JwtTokenService {

    private static final long ALLOWED_FUTURE_IAT_SECONDS = 30;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties properties;
    private final Clock clock;

    public NimbusJwtTokenService(
            JwtEncoder encoder,
            JwtDecoder decoder,
            JwtProperties properties,
            Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedJwt issue(UUID userId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
        return new IssuedJwt(value, expiresAt);
    }

    @Override
    public JwtPrincipal verify(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("token is blank");
            }
            Jwt jwt = decoder.decode(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            if (issuedAt == null
                    || expiresAt == null
                    || issuedAt.isAfter(clock.instant().plusSeconds(ALLOWED_FUTURE_IAT_SECONDS))) {
                throw new IllegalArgumentException("required JWT time claim is invalid");
            }
            return new JwtPrincipal(userId, expiresAt);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtAuthenticationException(exception);
        }
    }
}
