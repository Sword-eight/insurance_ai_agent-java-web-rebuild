package com.insurance.platform.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final int MINIMUM_HS256_KEY_BYTES = 32;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSigningKey(JwtProperties properties) {
        validateNonSecretSettings(properties);
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.secretBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT secret must be valid Base64", exception);
        }
        if (decoded.length < MINIMUM_HS256_KEY_BYTES) {
            throw new IllegalStateException("JWT secret must decode to at least 32 bytes");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSigningKey,
            JwtProperties properties,
            Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator =
                new JwtTimestampValidator(Duration.ofSeconds(30));
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                timestampValidator,
                new JwtIssuerValidator(properties.issuer())));
        return decoder;
    }

    private static void validateNonSecretSettings(JwtProperties properties) {
        if (properties.issuer() == null || properties.issuer().isBlank()) {
            throw new IllegalStateException("JWT issuer must not be blank");
        }
        if (properties.accessTokenTtl() == null
                || properties.accessTokenTtl().isZero()
                || properties.accessTokenTtl().isNegative()) {
            throw new IllegalStateException("JWT access token TTL must be positive");
        }
        if (properties.secretBase64() == null || properties.secretBase64().isBlank()) {
            throw new IllegalStateException("JWT secret environment variable is required");
        }
    }
}
