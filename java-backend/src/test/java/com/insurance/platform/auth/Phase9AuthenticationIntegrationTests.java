package com.insurance.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.platform.config.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class Phase9AuthenticationIntegrationTests {

    private static final String PASSWORD = "InterviewPass123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private Clock clock;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM iap_chat_message");
        jdbc.update("DELETE FROM iap_chat_request");
        jdbc.update("DELETE FROM iap_conversation");
        jdbc.update("DELETE FROM iap_user");
    }

    @Test
    void registerLoginAndJwtProtectOwnedResources() throws Exception {
        JsonNode registered = register("Candidate_01", PASSWORD);
        String userId = registered.path("data").path("userId").asText();
        assertThat(registered.path("data").path("username").asText())
                .isEqualTo("candidate_01");

        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM iap_user WHERE user_id = ?",
                String.class,
                userId);
        assertThat(storedHash).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, storedHash)).isTrue();

        MvcResult login = login("CANDIDATE_01", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.data.user.userId").value(userId))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
        JsonNode loginBody = body(login);
        assertThat(loginBody.path("data").path("expiresInSeconds").asLong())
                .isBetween(1790L, 1800L);
        String token = loginBody.path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();
        assertThat(login.getRequest().getSession(false)).isNull();

        MvcResult create = mockMvc.perform(post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"secured chat\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();
        String conversationId = body(create).path("data").path("conversationId").asText();

        register("other_user", PASSWORD);
        String otherToken = body(login("other_user", PASSWORD)
                        .andExpect(status().isOk()).andReturn())
                .path("data").path("accessToken").asText();
        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONVERSATION_ACCESS_DENIED"));
    }

    @Test
    void duplicateUsernameAndInvalidPasswordUseStableValidationError() throws Exception {
        register("Duplicate_User", PASSWORD);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson("duplicate_user", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("username is unavailable"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson("valid_user", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson("valid_user", "保".repeat(25))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidCredentialsAndDisabledUserShareUnauthorizedResponse() throws Exception {
        register("login_user", PASSWORD);
        String activeToken = body(login(" login_user ", PASSWORD)
                        .andExpect(status().isOk()).andReturn())
                .path("data").path("accessToken").asText();

        login("missing_user", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("authentication is required"));
        login("login_user", "WrongPass123!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("authentication is required"));

        jdbc.update("UPDATE iap_user SET status = 'DISABLED' WHERE username = 'login_user'");
        login("login_user", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("authentication is required"));
        mockMvc.perform(get("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + activeToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void missingMalformedTamperedExpiredAndInvalidClaimTokensAreRejected() throws Exception {
        register("token_user", PASSWORD);
        String token = body(login("token_user", PASSWORD)
                        .andExpect(status().isOk()).andReturn())
                .path("data").path("accessToken").asText();

        expectUnauthorized(null);
        expectUnauthorized("Token " + token);
        expectUnauthorized("Bearer " + token + "tampered");
        expectUnauthorized("Bearer " + signedToken(
                UUID.randomUUID().toString(),
                jwtProperties.issuer(),
                clock.instant().minusSeconds(180),
                clock.instant().minusSeconds(120)));
        expectUnauthorized("Bearer " + signedToken(
                "not-a-uuid",
                jwtProperties.issuer(),
                clock.instant(),
                clock.instant().plusSeconds(300)));
        expectUnauthorized("Bearer " + signedToken(
                UUID.randomUUID().toString(),
                "wrong-issuer",
                clock.instant(),
                clock.instant().plusSeconds(300)));
    }

    private JsonNode register(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(username, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();
        return body(result);
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(authJson(username, password)));
    }

    private void expectUnauthorized(String authorization) throws Exception {
        var request = get("/api/v1/conversations");
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("authentication is required"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").isString());
    }

    private String signedToken(
            String subject,
            String issuer,
            Instant issuedAt,
            Instant expiresAt) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String authJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("username", username, "password", password));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
