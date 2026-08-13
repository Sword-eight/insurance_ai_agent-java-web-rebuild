package com.insurance.platform.auth.service;

import com.insurance.platform.auth.dto.LoginRequest;
import com.insurance.platform.auth.dto.RegisterRequest;
import com.insurance.platform.auth.vo.LoginView;
import com.insurance.platform.auth.vo.UserView;
import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import com.insurance.platform.security.IssuedJwt;
import com.insurance.platform.security.JwtTokenService;
import com.insurance.platform.user.entity.UserEntity;
import com.insurance.platform.user.entity.UserStatus;
import com.insurance.platform.user.mapper.UserMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns registration and login rules without depending on HTTP or SecurityContext details.
 */
@Service
public class AuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final String USERNAME_UNAVAILABLE = "username is unavailable";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            Clock clock) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String username = normalizeUsername(request.username());
        validatePassword(request.password());
        if (userMapper.findByNormalizedUsername(username) != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, USERNAME_UNAVAILABLE);
        }

        LocalDateTime now = LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MILLIS);
        UserEntity user = new UserEntity();
        user.setUserId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE.name());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insertUser(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, USERNAME_UNAVAILABLE);
        }
        return toView(user);
    }

    @Transactional(readOnly = true)
    public LoginView login(LoginRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String username = normalizeUsername(request.username());
        validatePassword(request.password());
        UserEntity user = userMapper.findByNormalizedUsername(username);
        String candidateHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), candidateHash);
        if (user == null
                || !passwordMatches
                || !UserStatus.ACTIVE.name().equals(user.getStatus())
                || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        UUID publicUserId = UUID.fromString(user.getUserId());
        IssuedJwt issued = jwtTokenService.issue(publicUserId);
        long expiresInSeconds = Math.max(
                0, Duration.between(clock.instant(), issued.expiresAt()).toSeconds());
        return new LoginView(
                issued.value(), "Bearer", expiresInSeconds, toView(user));
    }

    static String normalizeUsername(String rawUsername) {
        if (rawUsername == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalized = rawUsername.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3
                || normalized.length() > 64
                || !USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }

    static void validatePassword(String password) {
        if (password == null
                || password.length() < 8
                || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static UserView toView(UserEntity user) {
        return new UserView(
                UUID.fromString(user.getUserId()),
                user.getUsername(),
                user.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
