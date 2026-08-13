package com.insurance.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class AuthServiceValidationTests {

    @Test
    void usernameNormalizationIsLocaleIndependentAndCanonical() {
        assertThat(AuthService.normalizeUsername(" Candidate_01 "))
                .isEqualTo("candidate_01");
    }

    @Test
    void passwordOverBcryptUtf8ByteLimitIsRejected() {
        String multiBytePassword = "保".repeat(25);

        assertThatThrownBy(() -> AuthService.validatePassword(multiBytePassword))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
