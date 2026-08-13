package com.insurance.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurance.platform.auth.dto.LoginRequest;
import com.insurance.platform.auth.dto.RegisterRequest;
import com.insurance.platform.auth.service.AuthService;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.security.JwtPrincipal;
import com.insurance.platform.security.JwtTokenService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase9MySqlAuthenticationIntegrationTests {

    private static final String DATABASE = "phase9_auth";
    private static final String DATABASE_USER = "phase9_user";
    private static final String DATABASE_PASSWORD = UUID.randomUUID().toString();
    private static final String ROOT_PASSWORD = UUID.randomUUID().toString();
    private static final String USER_PASSWORD = "RealMySqlPass123!";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.4"))
            .withDatabaseName(DATABASE)
            .withUsername(DATABASE_USER)
            .withPassword(DATABASE_PASSWORD)
            .withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD)
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired private AuthService authService;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM iap_chat_message");
        jdbc.update("DELETE FROM iap_chat_request");
        jdbc.update("DELETE FROM iap_conversation");
        jdbc.update("DELETE FROM iap_user");
    }

    @Test
    void realMySqlPersistsBcryptAndSupportsLoginAndDisabledState() {
        var registered = authService.register(
                new RegisterRequest("Real_MySQL_User", USER_PASSWORD));
        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM iap_user WHERE user_id = ?",
                String.class,
                registered.userId().toString());
        assertThat(storedHash).isNotEqualTo(USER_PASSWORD);
        assertThat(passwordEncoder.matches(USER_PASSWORD, storedHash)).isTrue();

        var login = authService.login(new LoginRequest("REAL_MYSQL_USER", USER_PASSWORD));
        JwtPrincipal principal = jwtTokenService.verify(login.accessToken());
        assertThat(principal.userId()).isEqualTo(registered.userId());

        jdbc.update("UPDATE iap_user SET status = 'DISABLED' WHERE user_id = ?",
                registered.userId().toString());
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                authService.login(new LoginRequest("real_mysql_user", USER_PASSWORD))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AUTH_UNAUTHORIZED));
    }

    @Test
    void realMySqlUniqueConstraintArbitratesConcurrentNormalizedRegistration()
            throws Exception {
        int workers = 4;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                String username = index % 2 == 0 ? "Concurrent_User" : "concurrent_user";
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return authService.register(new RegisterRequest(username, USER_PASSWORD));
                    } catch (BusinessException exception) {
                        return exception;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(20, TimeUnit.SECONDS));
            }
            assertThat(outcomes.stream()
                    .filter(outcome -> outcome instanceof com.insurance.platform.auth.vo.UserView)
                    .count()).isEqualTo(1);
            assertThat(outcomes.stream()
                    .filter(outcome -> outcome instanceof BusinessException)
                    .map(outcome -> (BusinessException) outcome)
                    .allMatch(exception -> exception.errorCode() == ErrorCode.VALIDATION_ERROR))
                    .isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM iap_user WHERE username = 'concurrent_user'",
                    Long.class)).isEqualTo(1L);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
