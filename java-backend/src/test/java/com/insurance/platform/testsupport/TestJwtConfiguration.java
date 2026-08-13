package com.insurance.platform.testsupport;

import com.insurance.platform.config.JwtProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Generates an ephemeral JWT key for each test ApplicationContext. */
@Configuration(proxyBeanMethods = false)
public class TestJwtConfiguration {

    @Bean
    @Primary
    JwtProperties phase9TestJwtProperties() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return new JwtProperties(
                "insurance-ai-platform-test",
                Duration.ofMinutes(30),
                Base64.getEncoder().encodeToString(secret));
    }
}
